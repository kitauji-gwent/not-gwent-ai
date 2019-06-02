package pl.edu.agh.gwent.ai.learning

import cats.effect.IO
import org.deeplearning4j.gym.StepReply
import org.deeplearning4j.rl4j.mdp.MDP
import org.deeplearning4j.rl4j.space.ObservationSpace
import org.json.JSONObject
import pl.edu.agh.gwent.ai.model.{Card, GameState}
import pl.edu.agh.gwent.ai.model.commands.GameCommand

class GwentMDP(
  esFactory: IO[GameES],
  closeOp: IO[Unit],
  envName: String
) extends MDP[GameState, List[GameCommand], GwentActionSpace] {

  private var currentGame: GameES = _
  private var currentRound: Int = _
  private var currentState: GameState = _
  var isDone: Boolean = _

  override def getObservationSpace: ObservationSpace[GameState] = ???
  override def getActionSpace: GwentActionSpace = {
    val getCards = () => currentState.ownHand.cards
    def commandsOf(card: Card) = MetaGameHandler.generateCommand(card, currentState)
    new GwentActionSpace(getCards, commandsOf)
  }
  override def reset(): GameState = {
    val code = for {
      es <- esFactory
      _ <- IO(currentGame = es)
      _ <- IO(currentRound = 1)
      state <- MetaGameHandler.initGameState(es, envName)
      _ <- IO(currentState = state)
    } yield state

    code.unsafeRunSync()
  }
  override def close(): Unit = closeOp.unsafeRunSync()
  override def step(action: List[GameCommand]): StepReply[GameState] = {
    val (state, roundEnd) = MetaGameHandler.applyCommand(currentGame).unsafeRunSync()
    currentState = state
    val reward: Double = ???
    val done = currentRound == 2 && roundEnd
    isDone = done
    if (currentRound == 1 && roundEnd) currentRound = 2
    new StepReply[GameState](
      state,
      reward,
      done,
      new JSONObject()
    )
  }

  override def newInstance(): MDP[GameState, List[GameCommand], GwentActionSpace] = ???
}
