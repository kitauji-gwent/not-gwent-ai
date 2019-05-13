package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.misc.{AbstractValueEnumCompanion, EnumCtx, ValueEnum}
import com.avsystem.commons.serialization.GenCodec

final class
CardType(implicit val enumCtx: EnumCtx) extends ValueEnum

object CardType extends AbstractValueEnumCompanion[CardType] {
  final val CloseCombat: Value = new CardType
  final val Ranged: Value = new CardType
  final val Siege: Value = new CardType
  final val Leader: Value = new CardType
  final val Special: Value = new CardType
  final val Weather: Value = new CardType

  override implicit lazy val codec: GenCodec[CardType] = {
    val ordinal = values.map(v => v.enumCtx.ordinal -> v).toMap
    GenCodec
      .nonNullSimple[CardType](v => ordinal(v.readInt()), (o, v) => o.writeInt(v.enumCtx.ordinal))
  }
}
