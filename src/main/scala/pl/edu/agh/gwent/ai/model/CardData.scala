package pl.edu.agh.gwent.ai.model

case class CardData(
  name: String,
  power: Int,
  ability: String,
  img: String,
  faction: Faction,
  `type`: CardType
)
