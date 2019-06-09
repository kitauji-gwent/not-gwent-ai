package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.HasGenCodec

case class BattleSide(
  name: String,
  lives: Int,
  score: Int,
  hand: Int,
  deck: Int,
  discard: Set[Card],
  passing: Option[Boolean],
) {
  def isPassing: Boolean = passing.getOrElse(false)
}

object BattleSide extends HasGenCodec[BattleSide]
