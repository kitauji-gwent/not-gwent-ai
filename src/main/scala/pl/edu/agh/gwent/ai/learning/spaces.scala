package pl.edu.agh.gwent.ai.learning

import org.deeplearning4j.rl4j.space._
import pl.edu.agh.gwent.ai.model.GameInstance


object GwentObservationSpace {

  def create(env: GameInstance): ObservationSpace[GameInstance#GameState] = new ArrayObservationSpace(Array(env.size))

}

object GwentActionSpace {

  def create(env: GameInstance): DiscreteSpace = new DiscreteSpace(env.cardNum + 2)

}