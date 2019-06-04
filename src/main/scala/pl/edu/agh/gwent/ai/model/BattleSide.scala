package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.GenCodec

case class BattleSide(
  name: String,
  lives: Int,
  score: Int,
  hand: Int,
  deck: Int,
  discard: Set[Card],
  passing: Option[Boolean]
) {
  def isPassing: Boolean = passing.getOrElse(false)
}

object BattleSide {
  implicit val codec: GenCodec[BattleSide] = GenCodec.materialize
}
