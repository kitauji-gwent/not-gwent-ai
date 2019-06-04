package pl.edu.agh.gwent.ai.model
package commands

import com.avsystem.commons.serialization.{GenCodec, flatten}

object Command {
  implicit val codec: GenCodec[Command] = GenCodec.materialize[Command]
}

@flatten
sealed trait Command extends Product with Serializable {
  def event: String
  def hasBody: Boolean
}

sealed trait GameCommand extends Command

case object FinishRedraw extends Command {
  override def event: String = "redraw:close_client"
  override def hasBody: Boolean = false
}
case class PlayCard(id: CardID) extends GameCommand {
  override def event: String = "play:cardFromHand"
  override def hasBody: Boolean = true
}
case class SelectAgile(field: CardType) extends GameCommand {
  override def event: String = "agile:field"
  override def hasBody: Boolean = true
}
case class SelectHorn(field: CardType) extends GameCommand {
  override def event: String = "horn:field"
  override def hasBody: Boolean = true
}

case class MedicChooseCard(cardID: Option[CardID]) extends GameCommand {
  override def event: String = "medic:chooseCardFromDiscard"
  override def hasBody: Boolean = cardID.isDefined
}

case class DecoyReplaceWith(cardID: CardID) extends GameCommand {
  override def event: String = "decoy:replaceWith"
  override def hasBody: Boolean = true
}
case object Pass extends GameCommand {
  override def event: String = "set:passing"
  override def hasBody: Boolean = false
}
case object UseLeader extends GameCommand {
  override def event: String = "activate:leader"
  override def hasBody: Boolean = true
}
case class FromDiscardMedic(cardID: CardID) extends GameCommand {
  override def event: String = "medic:chooseCardFromDiscard"
  override def hasBody: Boolean = true
}
case class FromDiscardEmhyr(cardID: CardID) extends GameCommand {
  override def event: String = "emreis_leader4:chooseCardFromDiscard"
  override def hasBody: Boolean = true
}

sealed trait PlayerCommand extends Command

case class Name(name: UserID) extends PlayerCommand {
  override def event: String = "request:name"
  override def hasBody: Boolean = true
}
case object Enqueue extends PlayerCommand {
  override def event: String = "request:matchmaking"
  override def hasBody: Boolean = true
}
case class GameLoaded(_roomID: String) extends PlayerCommand {
  override def event: String = "request:gameLoaded"
  override def hasBody: Boolean = true
}
case class ChooseDeck(deck: Faction) extends PlayerCommand {
  override def event: String = "set:deck"
  override def hasBody: Boolean = true
}
