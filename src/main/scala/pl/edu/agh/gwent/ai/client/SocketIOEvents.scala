package pl.edu.agh.gwent.ai.client

import cats.effect.{ContextShift, IO, Resource}
import com.avsystem.commons.serialization._
import com.avsystem.commons.serialization.json.JsonStringInput
import fs2._
import fs2.concurrent.Queue
import io.socket.client.{Socket, IO => SIO}
import io.socket.emitter.Emitter
import org.json.{JSONArray, JSONObject}
import pl.edu.agh.gwent.ai.client.SocketIOEvents.JSONObjectOutput

class SocketIOEvents[C, U](
  socket: Socket,
  eventsQueue: Queue[IO, U],
  eventNameF: C => String,
  eventBodyF: C => Boolean
)(implicit
  commandCodec: GenCodec[C],
) extends EventStream[IO, Stream[IO, ?], C, U] {

  private def encode(command: C): AnyRef = {
    var result: AnyRef = null
    val output = new JSONObjectOutput(result = _)
    GenCodec.write(output, command)
    result
  }

  override def publish(commands: Stream[IO, C]): IO[Unit] =
    commands.evalMap(c =>
      if (eventBodyF(c))
        IO(socket.emit(eventNameF(c), encode(c)))
      else
        IO(socket.emit(eventNameF(c)))
    ).compile.drain

  override def publish1(c: C): IO[Unit] =
    IO(socket.emit(eventNameF(c), encode(c)))

  override def events: Stream[IO, U] = eventsQueue.dequeue
}


object SocketIOEvents {

  private class JSONObjectOutput(setter: AnyRef => Unit) extends Output {

    override def writeNull(): Unit = setter(null)
    override def writeSimple(): SimpleOutput = new SimpleOutput {
      override def writeString(str: String): Unit = setter(str)
      override def writeBoolean(boolean: Boolean): Unit = setter(Boolean.box(boolean))
      override def writeInt(int: Int): Unit = setter(Int.box(int))
      override def writeLong(long: Long): Unit = setter(Long.box(long))
      override def writeDouble(double: Double): Unit = setter(Double.box(double))
      override def writeBigInt(bigInt: BigInt): Unit = setter(Long.box(bigInt.toLong))
      override def writeBigDecimal(bigDecimal: BigDecimal): Unit = setter(Double.box(bigDecimal.toDouble))
      override def writeBinary(binary: Array[Byte]): Unit = ???
    }

    override def writeList(): ListOutput = new ListOutput {
      private val arr = new JSONArray()
      override def writeElement(): Output = new JSONObjectOutput(arr.put(_))
      override def finish(): Unit = setter(arr)
    }
    override def writeObject(): ObjectOutput = new ObjectOutput {
      private val obj = new JSONObject()
      override def writeField(key: String): Output = {
        new JSONObjectOutput(v => obj.put(key, v))
      }
      override def finish(): Unit = setter(obj)
    }
  }

  type UpdateSetup[U] = Map[String, AnyRef => U]

  def setupEvents[U](event: (String, GenCodec[_ <: U])*): UpdateSetup[U] = {
    val codecs = Map(event:_*)
    def decode(name: String, argCodec: GenCodec[_ <: U])(arg: AnyRef): U = {
      implicit val codec: GenCodec[U] = argCodec.asInstanceOf[GenCodec[U]]
//      println(s"Got event: $name: $arg")
      arg match {
        case js: JSONObject =>
          if (name.startsWith("played"))
            js.put("_type", name)
          JsonStringInput.read[U](js.toString)
        case js: String =>
          JsonStringInput.read[U](s""""$js"""")
      }
    }

    codecs map { case (k, v) => k -> decode(k, v) _ }
  }


  def make[C, U](
    uri: String,
    events: UpdateSetup[U],
    eventNameF: C => String,
    eventBodyF: C => Boolean
  )(implicit
    commandCodec: GenCodec[C],
    cs: ContextShift[IO]
  ): Resource[IO, EventStream[IO, Stream[IO, ?], C, U]] = {

    def create(queue: Queue[IO, U]) = IO {
      val sock = SIO.socket(uri)
      events.foldLeft(sock: Emitter) {
        case (s, (ev, decode)) =>
          s.on(ev, (args: Array[AnyRef]) => queue.enqueue1(decode(args(0))).unsafeRunSync())
      }
      sock.connect()
    }

    for {
      queue <- Resource.liftF(Queue.bounded[IO, U](1000))
      sock <- Resource.make(create(queue))(s => IO(s.close()))
    } yield new SocketIOEvents(sock, queue, eventNameF, eventBodyF)
  }

  def unsafe[C, U](
    uri: String,
    events: UpdateSetup[U],
    eventNameF: C => String,
    eventBodyF: C => Boolean
  )(implicit
    commandCodec: GenCodec[C],
    cs: ContextShift[IO]
  ): IO[(EventStream[IO, Stream[IO, ?], C, U], IO[Unit])] = {


    def create(queue: Queue[IO, U]) = IO {
      val sock = SIO.socket(uri)
      events.foldLeft(sock: Emitter) {
        case (s, (ev, decode)) =>
          s.on(ev, (args: Array[AnyRef]) => queue.enqueue1(decode(args(0))).unsafeRunSync())
      }
      sock.connect()
    }

    for {
      queue <- Queue.bounded[IO, U](1000)
      sock <- create(queue)
    } yield new SocketIOEvents(sock, queue, eventNameF, eventBodyF) -> IO {
      println("closing event stream")
      sock.close()
    }

  }

}