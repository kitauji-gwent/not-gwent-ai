package pl.edu.agh.gwent.ai.model

case class Card(
  _owner: UserID,
  _id: CardID,
  boost: Int,
  _uidEvents: List[Any],
  _disabled: Boolean,
  _data: CardData,
  _key: String,
  _boost: Any,
  _forcedPower: Int,
  power: Int,
  diff: Int,
  diffPos: Option[Any]
) {}