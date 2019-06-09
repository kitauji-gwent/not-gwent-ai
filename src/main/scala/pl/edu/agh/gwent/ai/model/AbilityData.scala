package pl.edu.agh.gwent.ai.model

import com.avsystem.commons.serialization.{GenCodec, Input, Output}

import scala.util.Try

sealed trait AbilityData extends Product with Serializable {
  def abilityCode: String
}

object AbilityData {

  case class Simple(name: String) extends AbilityData {
    override def abilityCode: String = name
  }
  case class Complex(parts: List[String]) extends AbilityData {
    override def abilityCode: String = parts.mkString(":")
  }
  case object Null extends AbilityData {
    override def abilityCode: String = "null"
  }

  implicit object codec extends GenCodec[AbilityData] {
    override def read(input: Input): AbilityData =
      Try(Simple(input.readSimple().readString()))
        .orElse(Try(Complex(input.readList().iterator(_.readSimple().readString()).toList)))
        .orElse(Try(if (input.readNull()) Null else throw new Exception("Cannot read AbilityData value")))
        .fold(
          throw _,
          identity
        )

    override def write(output: Output, value: AbilityData): Unit = ()
  }
}
