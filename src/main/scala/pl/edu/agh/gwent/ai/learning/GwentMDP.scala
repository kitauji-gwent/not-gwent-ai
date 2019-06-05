package pl.edu.agh.gwent.ai.learning

import cats.effect.IO
import org.deeplearning4j.gym.StepReply
import org.deeplearning4j.rl4j.mdp.MDP
import org.deeplearning4j.rl4j.space.{DiscreteSpace, ObservationSpace}
import pl.edu.agh.gwent.ai.learning.GwentMDP.EnviromentSetup
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model._

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

  def default: GwentMDP = ???
}
