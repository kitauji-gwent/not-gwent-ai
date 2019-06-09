package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.HasGenCodec

case class Field(
  horn: Option[Card],
  cards: Set[Card],
  score: Int,
)

object Field extends HasGenCodec[Field]
