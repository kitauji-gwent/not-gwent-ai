package pl.edu.agh.gwent.ai.model

case class Field(
  horn: Boolean,
  cars: Set[Card],
  score: Int
)
