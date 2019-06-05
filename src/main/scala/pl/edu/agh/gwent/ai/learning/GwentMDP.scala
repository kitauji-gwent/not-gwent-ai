package pl.edu.agh.gwent.ai.learning

import cats.Order
import cats.effect.concurrent.{MVar, Ref}
import cats.effect.{ContextShift, Fiber, IO}
import com.avsystem.commons.serialization.GenCodec
import org.deeplearning4j.gym.StepReply
import org.deeplearning4j.rl4j.mdp.MDP
import org.deeplearning4j.rl4j.policy.DQNPolicy
import org.deeplearning4j.rl4j.space.{DiscreteSpace, ObservationSpace}
import pl.edu.agh.gwent.ai.client.SocketIOEvents
import pl.edu.agh.gwent.ai.learning.GwentMDP.EnviromentSetup
import pl.edu.agh.gwent.ai.model._
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model.updates._

import scala.concurrent.ExecutionContext

class GwentMDP(
  esFactory: IO[GameES],
  closeOp: IO[Unit],
  val env: EnviromentSetup
) extends DiscreteMDP[GameInstance#GameState] {
  type GameState = GameInstance#GameState

  private implicit class FixMe(state: GameState) {
    def fix: env.gameInstance.GameState = state.asInstanceOf[env.gameInstance.GameState]
  }

  private var es: GameES = _
  private var gs: GameState = _
  var shouldWait: Boolean = false
  var isDone: Boolean = false

  override def getObservationSpace: ObservationSpace[GameState] = GwentObservationSpace.create(env.gameInstance)

  override def getActionSpace: DiscreteSpace = GwentActionSpace.create(env.gameInstance)

  override def reset(): GameState = {
    val code = for {
      _ <- closeOp
      nes <- esFactory
      _ <- IO(es = nes)
     (ngs, wait) <- MetaGameHandler.initGameState(env.gameInstance)(nes, env.agentName)
      _ <- IO {
        gs = ngs
        shouldWait = wait
      }
    } yield ngs

    code.unsafeRunSync()
  }

  override def close(): Unit = closeOp.unsafeRunSync()

  override def step(action: Integer): StepReply[GameState] = {
    //action 0 - do nothing
    //action 1 - pass
    //action N - play card with id {N - 1}
    val commands = GwentMDP.getCommandsOf(env)(gs.fix, action)
    val oldGS = gs
    val (newGs, done) = MetaGameHandler.applyCommand(env.gameInstance)(es, oldGS.fix, commands, shouldWait).unsafeRunSync()
    gs = newGs
    shouldWait = false
    isDone = done

    val illegalActionPunishment = if (commands.isEmpty) Some(env.illegalMovePunish) else None

    val isVictorious = isDone && gs.foeSide.lives == 0
    val isLoser = isDone && gs.ownSide.lives == 0
    val isDraw = isDone && gs.ownSide.lives == 1 && gs.foeSide.lives == 1

    def powerUpReward    = env.ownPowerFactor * (gs.ownSide.score - oldGS.ownSide.score)
    def enemyPowerReward = env.foePowerFactor * (oldGS.foeSide.score - gs.foeSide.score)

    val reward =
      if (isVictorious) env.victoryReward
      else if (isLoser) env.lossPunish
      else if (isDraw) env.drawReward
      else illegalActionPunishment
        .getOrElse(powerUpReward + enemyPowerReward)


    new StepReply[GameState](
      gs,
      reward,
      isDone,
      null
    )

  }

  override def newInstance(): MDP[GameState, Integer, DiscreteSpace] = ???
}

object GwentMDP {
  import cats.instances.int._
  import cats.instances.list._
  import cats.syntax.all._
  import fs2._

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  trait Player {
    def play: IO[Fiber[IO, Unit]]
  }

  class HandWrittenBot(es: GameES, name: String, val inst: GameInstance) extends Player {
    type GameState = inst.GameState

    def play: IO[Fiber[IO, Unit]] = {

      implicit val order: Order[Card] = Order.by[Card, Int](_._data.power)

      def selectCard(state: GameState): Option[Card] = {
        state.ownHand.cards
          .iterator
          .toList
          .maximumOption
      }

      def handleTick(old: GameState, shouldWait: Boolean): IO[(GameState, Boolean)] = for {
        _ <- if (shouldWait) {
          es.events.collectFirst({ case WaitingUpdate(false) => }).compile.drain
        } else {
          IO.unit
        }
        card <- IO(selectCard(old))
        commands = card.map(MetaGameHandler.generateCommand(inst)(_, old)).getOrElse(List.empty)
        _ <- IO(println(s"Selected card: $card"))
        _ <- IO(println(s"Selected commands: $commands"))

        (gs, isOver) <-
        if ((old.ownSide.score > old.foeSide.score && old.foeSide.isPassing) || commands.isEmpty)
          MetaGameHandler.applyCommand(inst)(es, old, List(Pass), shouldWait = false)
        else
          MetaGameHandler.applyCommand(inst)(es, old, commands, shouldWait = false)

      } yield (gs, isOver)

      val code = for {
        (gs, shouldWait) <- MetaGameHandler.initGameState(inst)(es, name)
        _ <- IO(println(s"Current state: ${gs.toArray.mkString("[", ", ", "]")}"))
        _ <- Stream.iterateEval((gs, shouldWait, false))({
          case (state, sw, end) =>
            handleTick(state, sw).map { case (ns, nend) => (ns, false, nend) }
        }).takeWhile(!_._3).compile.drain
      } yield ()

      code.start
    }

  }

  class PolicyExecutor(mdp: GwentMDP, policy: DQNPolicy[GameInstance#GameState]) {

    policy.play(mdp)


  }

  def getCommandsOf(env: EnviromentSetup)(gs: env.gameInstance.GameState, action: Int): List[GameCommand] = {
    //action 0 - do nothing
    //action 1 - pass
    //action N - play card with id {N - 1}
    if (action == 0) List.empty[GameCommand]
    else if (action == 1) List(Pass)
    else {
      val card = env.cards(action - 1)
      MetaGameHandler.generateCommand(env.gameInstance)(card, gs)
    }
  }

  case class EnviromentSetup(
    agentName: String,
    gameInstance: GameInstance,
    cards: Map[CardID, Card],
    illegalMovePunish: Double = -1000d,
    ownPowerFactor: Double = 0.1d,
    foePowerFactor: Double = 0.05d,
    victoryReward: Double = 100d,
    drawReward: Double = 25d,
    lossPunish: Double = -25d
  )

  val events = SocketIOEvents.setupEvents[Update](
    "response:name" -> GenCodec[NameUpdate],
    "init:battle" -> GenCodec[InitBattle],
    "response:joinRoom" -> GenCodec[JoinRoom],
    "set:waiting" -> GenCodec[WaitingUpdate],
    "set:passing" -> GenCodec[NoOpAck],
    "played:medic" -> GenCodec[NoOpAck],
    "played:emreis_leader4" -> GenCodec[NoOpAck],
    "played:agile" -> GenCodec[NoOpAck],
    "played:horn" -> GenCodec[PlayedUpdate],
    "update:hand" -> GenCodec[HandUpdate],
    "update:fields" -> GenCodec[FieldsUpdate],
    "update:info" -> GenCodec[InfoUpdate],
    "set:passing" -> GenCodec[PassingUpdate],
    "gameover" -> GenCodec[GameOver]
  )

  def manualyClosed(env: EnviromentSetup, url: String = "http://localhost:16918", player: Player): IO[IO[GameES]] =
    for {
      streamRef <- MVar[IO].empty[GameES]
      restartCode <- Ref[IO].of(IO.unit)
      startCode = restartCode.get.flatten *> {
        val code = SocketIOEvents.make[Command, Update]("http://localhost:16918", events, _.event, _.hasBody).use { es =>
          streamRef.put(es) <* IO.never
        }
        (player.play, code.start).mapN(_ product _).flatTap(fib => restartCode.set(fib.cancel))
      }
    } yield startCode *> streamRef.take


  def default(player: Player, envSetup: EnviromentSetup): GwentMDP = ???
}
