package pl.edu.agh.gwent.ai.model
package commands

sealed trait Command {
  def event: String
}

case class PlayCard(id: CardID) extends Command {
  override def event: String = "play:cardFromHand"
}
case class SelectAgile(field: CardType) extends Command {
  override def event: String = "agile:field"
}
case class SelectHorn(field: CardType) extends Command {
  override def event: String = "horn:field"
}
case class DecoyReplaceWith(cardID: CardID) extends Command {
  override def event: String = "decoy:replaceWith"
}
case object Pass extends Command {
  override def event: String = "set:passing"
}
case object UseLeader extends Command {
  override def event: String = "activate:leader"
}
case class FromDiscardMedic(cardID: CardID) extends Command {
  override def event: String = "medic:chooseCardFromDiscard"
}
case class FromDiscardEmhyr(cardID: CardID) extends Command {
  override def event: String = "emreis_leader4:chooseCardFromDiscard"
}
