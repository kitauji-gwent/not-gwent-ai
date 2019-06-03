package pl.edu.agh.gwent.ai.learning

import cats.effect.IO
import org.deeplearning4j.gym.StepReply
import org.deeplearning4j.rl4j.mdp.MDP
import org.deeplearning4j.rl4j.space.{DiscreteSpace, ObservationSpace}
import org.json.JSONObject
import pl.edu.agh.gwent.ai.model.{Card, CardID, GameState}
import pl.edu.agh.gwent.ai.model.commands.GameCommand

class GwentMDP(
  esFactory: IO[GameES],
  closeOp: IO[Unit],
  cards: Map[CardID, Card],
  envName: String,
  lowestCard: CardID,
  highesCard: CardID
) extends DiscreteMDP[GameState] {

  private var es: GameES = _
  private var gs: GameState = _
  var isDone: Boolean = false

  override def getObservationSpace: ObservationSpace[GameState] = ???

  override def getActionSpace: DiscreteSpace = GwentActionSpace.create()

  override def reset(): GameState = {
    val code = for {
      _ <- closeOp
      nes <- esFactory
      _ <- IO(es = nes)
      ngs <- MetaGameHandler.initGameState(nes, envName)
      _ <- IO(gs = ngs)
    } yield ngs

    code.unsafeRunSync()
  }

  override def close(): Unit = closeOp.unsafeRunSync()

  override def step(action: Integer): StepReply[GameState] = {
    val card = GwentActionSpace.getCardOf(cards, action)
    val commands = MetaGameHandler.generateCommand(card, gs)
    (gs, isDone) = MetaGameHandler.applyCommand(es, commands).unsafeRunSync()
    new StepReply[GameState](
      gs,
      ???,
      isDone,
      new JSONObject()
    )

  }

  override def newInstance(): MDP[GameState, Integer, DiscreteSpace] = ???
}

object GwentMDP {

  def default: GwentMDP = ???
}
