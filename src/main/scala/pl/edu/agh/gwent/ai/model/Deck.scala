package pl.edu.agh.gwent.ai.model

case class Deck(
  side: String,
  _owner: UserID,
  _originalDeck: Set[CardID],
  _deck: Set[CardID],
  _faction: Faction,
) {

}
