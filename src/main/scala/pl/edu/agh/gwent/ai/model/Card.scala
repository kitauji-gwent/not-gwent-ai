package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.{HasGenCodec, whenAbsent}

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
  @whenAbsent(Option.empty[Int])
  power: Option[Int],
  @whenAbsent(Option.empty[Int])
  diff: Option[Int],
//  diffPos: Option[Any]
)

object Card extends HasGenCodec[Card]
