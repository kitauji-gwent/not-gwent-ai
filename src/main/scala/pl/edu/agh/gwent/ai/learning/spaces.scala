package pl.edu.agh.gwent.ai.learning

import org.deeplearning4j.rl4j.space._
import org.nd4j.linalg.api.ndarray.INDArray
import pl.edu.agh.gwent.ai.model.{Card, GameState}
import pl.edu.agh.gwent.ai.model.commands._

import scala.util.Random

class GwentStateSpace extends ObservationSpace[GameState] {
  override def getName: String = ???
  override def getShape: Array[Int] = ???
  override def getLow: INDArray = ???
  override def getHigh: INDArray = ???
}

class GwentActionSpace(
  cards: () => Set[Card],
  commandsOf: Card => List[GameCommand]
) extends ActionSpace[List[GameCommand]] {

  private val random = new Random()
  override def randomAction(): List[GameCommand] = {
    val snapshot = cards()
    if (snapshot.isEmpty) {
      List.empty
    } else {
      val selectedCard = snapshot.iterator.drop(random.nextInt(snapshot.size - 1)).next()
      commandsOf(selectedCard)
    }
  }
  override def setSeed(seed: Int): Unit = random.setSeed(seed)
  override def encode(action: List[GameCommand]): AnyRef = ???
  override def getSize: Int = ???
  override def noOp(): List[GameCommand] = List()
}