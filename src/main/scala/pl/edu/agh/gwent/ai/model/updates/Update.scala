package pl.edu.agh.gwent.ai.model
package updates

import com.avsystem.commons.serialization.{HasGenCodec, transparent}

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
) extends Update

sealed trait LobbyUpdate extends Update

case class NameUpdate(name: UserID) extends LobbyUpdate

@transparent
case class JoinRoom(roomID: String) extends LobbyUpdate

object JoinRoom extends HasGenCodec[JoinRoom]

case class InitBattle(side: String, foeSide: String) extends LobbyUpdate

case object NoOpAck extends Update

