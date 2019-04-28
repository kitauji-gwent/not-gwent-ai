package pl.edu.agh.gwent.ai.model

case class BattleSide(
  name: String,
  lives: Int,
  score: Int,
  hand: Int,
  deck: Int,
  discard: Set[Card]

)
