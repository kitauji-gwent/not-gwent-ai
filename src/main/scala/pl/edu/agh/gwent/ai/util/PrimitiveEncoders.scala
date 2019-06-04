package pl.edu.agh.gwent.ai.util

import java.nio.ByteBuffer


object PrimitiveEncoders {

  def intToBytes(v: Int): Array[Byte] = {
    val b = ByteBuffer.allocate(4)
    b.putInt(v)
    b.array()
  }

  def optionIntToBytes(v: Option[Int]): Array[Byte] = intToBytes(v.getOrElse(0))

}
