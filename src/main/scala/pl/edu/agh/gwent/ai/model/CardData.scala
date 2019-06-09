package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.HasGenCodec

case class CardData(
  name: String,
  power: Int,
  ability: AbilityData,
  img: String,
  faction: Faction,
  `type`: CardType,
)

object CardData extends HasGenCodec[CardData]
