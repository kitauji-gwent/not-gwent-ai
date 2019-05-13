package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.GenCodec

case class Field(
  horn: Option[Card],
  cards: Set[Card],
  score: Int
)

object Field {
  implicit val codec: GenCodec[Field] = GenCodec.materialize
}
