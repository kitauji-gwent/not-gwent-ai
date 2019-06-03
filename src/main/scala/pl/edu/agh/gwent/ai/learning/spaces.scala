package pl.edu.agh.gwent.ai.learning

import org.deeplearning4j.rl4j.space._
import org.nd4j.linalg.api.ndarray.INDArray
import pl.edu.agh.gwent.ai.model.{Card, CardID, GameState}


class GwentStateSpace extends ObservationSpace[GameState] {
  override def getName: String = ???
  override def getShape: Array[Int] = ???
  override def getLow: INDArray = ???
  override def getHigh: INDArray = ???
}

object GwentActionSpace {

  def create(lower: Int, upper: Int): DiscreteSpace = ???

  def getCardOf(cards: Map[CardID, Card], action: Int): Card = ???

}