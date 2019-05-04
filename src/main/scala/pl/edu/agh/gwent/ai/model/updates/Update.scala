package pl.edu.agh.gwent.ai.model
package updates

import com.avsystem.commons.serialization.GenCodec

object Update {
  implicit val codec: GenCodec[Update] = GenCodec.materialize
}

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

sealed trait LobbyUpdate extends Update

case class NameUpdate(name: UserID) extends LobbyUpdate
case class JoinRoom(roomID: String) extends LobbyUpdate
case class InitBattle(side: String, foeSide: String) extends LobbyUpdate


