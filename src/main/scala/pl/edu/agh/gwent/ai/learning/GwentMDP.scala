package pl.edu.agh.gwent.ai.learning

import cats.effect.IO
import org.deeplearning4j.gym.StepReply
import org.deeplearning4j.rl4j.mdp.MDP
import org.deeplearning4j.rl4j.space.{DiscreteSpace, ObservationSpace}
import org.json.JSONObject
import pl.edu.agh.gwent.ai.learning.GwentMDP.EnviromentSetup
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model._

class GwentMDP(
  esFactory: IO[GameES],
  closeOp: IO[Unit],
  env: EnviromentSetup
) extends DiscreteMDP[GameState] {

  private var es: GameES = _
  private var gs: GameState = _
  var shouldWait: Boolean = false
  var isDone: Boolean = false

  override def getObservationSpace: ObservationSpace[GameState] = null

  override def getActionSpace: DiscreteSpace = GwentActionSpace.create(0, 42)

  override def reset(): GameState = {
    val code = for {
      _ <- closeOp
      nes <- esFactory
      _ <- IO(es = nes)
     (ngs, wait) <- MetaGameHandler.initGameState(nes, env.agentName)
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
    val commands =
      if (action == 0) List.empty[GameCommand]
      else if (action == 1) List(Pass)
      else {
        val card = GwentActionSpace.getCardOf(env.cards, action - 1)
        MetaGameHandler.generateCommand(card, gs)
      }
    val oldGS = gs
    val (newGs, done) = MetaGameHandler.applyCommand(es, oldGS, commands, shouldWait).unsafeRunSync()
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
      new JSONObject()
    )

  }

  override def newInstance(): MDP[GameState, Integer, DiscreteSpace] = ???
}

object GwentMDP {

  case class EnviromentSetup(
    agentName: String,
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
