package pl.edu.agh.gwent.ai.model
package commands

sealed trait PlayerCommand {
  def event: String
}

case class Name(name: UserID) extends PlayerCommand {
  override def event: String = "request:name"
}

case object Enqueue extends PlayerCommand {
  override def event: String = "request:name"
}

case class GameLoaded(_roomID: String) extends PlayerCommand {
  override def event: String = "request:gameLoaded"
}

case class ChooseDeck(deck: Faction) extends PlayerCommand {
  override def event: String = "set:deck"
}


