package pl.edu.agh.gwent.ai.model
package commands

import com.avsystem.commons.serialization.GenCodec

object Command {

  implicit val codec: GenCodec[Command] = GenCodec.materialize[Command]

}

sealed trait Command extends Product with Serializable {
  def event: String
}

sealed trait GameCommand extends Command

case class PlayCard(id: CardID) extends GameCommand {
  override def event: String = "play:cardFromHand"
}
case class SelectAgile(field: CardType) extends GameCommand {
  override def event: String = "agile:field"
}
case class SelectHorn(field: CardType) extends GameCommand {
  override def event: String = "horn:field"
}
case class DecoyReplaceWith(cardID: CardID) extends GameCommand {
  override def event: String = "decoy:replaceWith"
}
case object Pass extends GameCommand {
  override def event: String = "set:passing"
}
case object UseLeader extends GameCommand {
  override def event: String = "activate:leader"
}
case class FromDiscardMedic(cardID: CardID) extends GameCommand {
  override def event: String = "medic:chooseCardFromDiscard"
}
case class FromDiscardEmhyr(cardID: CardID) extends GameCommand {
  override def event: String = "emreis_leader4:chooseCardFromDiscard"
}

sealed trait PlayerCommand extends Command

case class Name(name: UserID) extends PlayerCommand {
  override def event: String = "request:name"
}
case object Enqueue extends PlayerCommand {
  override def event: String = "request:matchmaking"
}
case class GameLoaded(_roomID: String) extends PlayerCommand {
  override def event: String = "request:gameLoaded"
}
case class ChooseDeck(deck: Faction) extends PlayerCommand {
  override def event: String = "set:deck"
}
