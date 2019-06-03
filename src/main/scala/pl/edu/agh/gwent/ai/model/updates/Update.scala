package pl.edu.agh.gwent.ai.model
package updates

import com.avsystem.commons.serialization.{HasGenCodec, transparent}

sealed trait Update

case class FieldsUpdate(
  _roomSide: String,
  close: Field,
  ranged: Field,
  siege: Field,
  weather: Field
) extends Update

case class HandUpdate(
  _roomSide: String,
  cards: Set[Card]
) extends Update

case class InfoUpdate(
  _roomSide: String,
  info: BattleSide,
  leader: Card
) extends Update

case class WaitingUpdate(
  waiting: Boolean
) extends Update

case class PlayedUpdate(
  _roomSide: String,
  cardID: CardID,
  _type: String
) extends Update

case class GameOver(
  winner: String
) extends Update

sealed trait LobbyUpdate extends Update

case class NameUpdate(name: UserID) extends LobbyUpdate

@transparent
case class JoinRoom(roomID: String) extends LobbyUpdate

object JoinRoom extends HasGenCodec[JoinRoom]

case class InitBattle(side: String, foeSide: String) extends LobbyUpdate

case object NoOpAck extends Update

