package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.GenCodec

case class Card(
  _owner: UserID,
  _id: CardID,
  boost: Int,
//  _uidEvents: List[Any],
  _disabled: Boolean,
  _data: CardData,
  _key: String,
//  _boost: Any,
  _forcedPower: Int,
  power: Int,
  diff: Int,
//  diffPos: Option[Any]
) {}

object Card {
  implicit val codec: GenCodec[Card] = GenCodec.materialize
}