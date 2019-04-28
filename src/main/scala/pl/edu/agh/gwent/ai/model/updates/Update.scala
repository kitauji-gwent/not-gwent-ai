package pl.edu.agh.gwent.ai.model
package updates

sealed trait Update

case class FieldsUpdate(
  close: Field,
  ranged: Field,
  siege: Field,
  weather: Field
) extends Update

case class HandUpdate(
  cards: Set[Card]
) extends Update

case class InfoUpdate(
  info: BattleSide,
  leader: Card
)


