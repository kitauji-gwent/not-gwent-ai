package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.{GenCodec, Input, Output}

package object updates {

//  implicit val fieldsCodec: GenCodec[FieldsUpdate] = GenCodec.materialize
//  implicit val handUpCodec: GenCodec[HandUpdate] = GenCodec.materialize
  implicit val infoUpCodec: GenCodec[InfoUpdate] = GenCodec.materialize
  implicit val nameUpCodec: GenCodec[NameUpdate] = GenCodec.materialize
  implicit val initBtCodec: GenCodec[InitBattle] = GenCodec.materialize
  implicit val playUpCodec: GenCodec[PlayedUpdate] = GenCodec.materialize
  implicit val waitUpCodec: GenCodec[WaitingUpdate] = GenCodec.materialize
  implicit val gameOvCodec: GenCodec[GameOver] = GenCodec.materialize
  implicit val passingUpdate: GenCodec[PassingUpdate] = GenCodec.materialize
  implicit val redrawUpdate: GenCodec[RedrawUpdate] = GenCodec.materialize

  type NoOpAck = NoOpAck.type
  implicit object noOpAckCodec extends GenCodec[NoOpAck] {
    override def read(input: Input): NoOpAck = NoOpAck
    override def write(output: Output, value: NoOpAck): Unit = ()
  }

}
