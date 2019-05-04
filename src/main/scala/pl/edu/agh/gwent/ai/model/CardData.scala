package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.GenCodec

case class CardData(
  name: String,
  power: Int,
  ability: String,
  img: String,
  faction: Faction,
  `type`: CardType
)

object CardData {
  implicit val codec: GenCodec[CardData] = GenCodec.materialize
}