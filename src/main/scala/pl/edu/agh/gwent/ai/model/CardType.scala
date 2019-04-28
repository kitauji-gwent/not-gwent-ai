package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.misc.{AbstractValueEnumCompanion, EnumCtx, ValueEnum}

final class CardType(implicit val enumCtx: EnumCtx) extends ValueEnum

object CardType extends AbstractValueEnumCompanion[CardType] {
  final val CloseCombat: Value = new CardType
  final val Ranged: Value = new CardType
  final val Siege: Value = new CardType
  final val Leader: Value = new CardType
  final val Special: Value = new CardType
  final val Weather: Value = new CardType
}
