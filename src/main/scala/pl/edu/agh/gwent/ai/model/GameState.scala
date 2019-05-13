package pl.edu.agh.gwent.ai.model

import pl.edu.agh.gwent.ai.model.updates.{FieldsUpdate, HandUpdate, InfoUpdate}

object GameState {

  def init(
    ownLeader: Card,
    foeLeader: Card,
    info: InfoUpdate,
    ownFields: FieldsUpdate,
    foeFields: FieldsUpdate,
    ownHand: HandUpdate,
    foeHand: HandUpdate
  ): GameState = GameState(
    ownLeader,
    foeLeader,
    ownSide = info.info,
    foeSide = null,
    ownFields = FieldState(ownFields.close, ownFields.ranged, ownFields.siege, ownFields.weather),
    foeFields = FieldState(foeFields.close, foeFields.ranged, foeFields.siege, foeFields.weather),
    ownHand = HandState(ownHand.cards),
    foeHand = HandState(foeHand.cards)
  )

}

case class GameState(
  ownLeader: Card,
  foeLeader: Card,
  ownSide: BattleSide,
  foeSide: BattleSide,
  ownFields: FieldState,
  foeFields: FieldState,
  ownHand: HandState,
  foeHand: HandState
) {

  def applyUpdate(update: InfoUpdate): GameState = copy(
    ownSide = update.info
  )

}

case class HandState(
  cards: Set[Card]
) {
  def applyUpdate(update: HandUpdate): HandState = HandState(cards ++ update.cards)
}

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
