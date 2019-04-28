package pl.edu.agh.gwent.ai.model
package updates

sealed trait LobbyUpdate

case class NameUpdate(name: UserID) extends LobbyUpdate
case class JoinRoom(roomID: String) extends LobbyUpdate
case class InitBattle(side: String, foeSide: String) extends LobbyUpdate

