package pl.edu.agh.gwent.ai.model

import pl.edu.agh.gwent.ai.model.updates.{FieldsUpdate, HandUpdate, InfoUpdate}

object GameState

case class GameState(
  ownLeader: Card,
  foeLeader: Card,
  ownSideN: String,
  foeSideN: String,
  ownSide: BattleSide,
  foeSide: BattleSide,
  ownFields: FieldState,
  foeFields: FieldState,
  ownHand: HandState,
  foeHand: HandState
) {

  def applyUpdate(update: InfoUpdate): GameState =
    if (update._roomSide == ownSideN)
      copy(ownSide = update.info)
    else
      copy(foeSide = update.info)

  def applyUpdate(update: FieldsUpdate): GameState = {
    val fields = FieldState(update.close, update.ranged, update.siege, update.weather)
    if (update._roomSide == ownSideN)
      copy(ownFields = fields)
    else
      copy(foeFields = fields)
  }

  def applyUpdate(update: HandUpdate): GameState = {
    if (update._roomSide == ownSideN)
      copy(ownHand = HandState(update.cards))
    else
      copy(foeHand = HandState(update.cards))
  }

}

case class HandState(
  cards: Set[Card]
)

case class FieldState(
  close: Field,
  ranged: Field,
  siege: Field,
  weather: Field
) {
  def applyUpdate(update: FieldsUpdate): FieldState = FieldState(
    close = update.close,
    ranged = update.ranged,
    siege = update.siege,
    weather = update.weather
  )
}
