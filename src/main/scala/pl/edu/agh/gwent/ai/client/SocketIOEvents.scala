package pl.edu.agh.gwent.ai.client

import cats.effect.{ContextShift, IO, Resource}
import com.avsystem.commons.serialization.GenCodec
import fs2._
import fs2.concurrent.Queue
import io.socket.client.{Socket, IO => SIO}
import io.socket.emitter.Emitter

import scala.concurrent.ExecutionContext

class SocketIOEvents[C, U](
  socket: Socket,
  eventsQueue: Queue[IO, U],
  eventNameF: C => String
)(implicit
  commandCodec: GenCodec[C],
  updateCodec: GenCodec[U]
) extends EventStream[IO, Stream[IO, ?], C, U] {

  private def encode(command: C): AnyRef = ???

  override def publish(commands: Stream[IO, C]): IO[Unit] =
    commands.evalMap(c => IO(socket.emit(eventNameF(c), encode(c)))).compile.drain

  override def events: Stream[IO, U] = eventsQueue.dequeue
}


object SocketIOEvents {

  def make[C, U](
    uri: String,
    events: List[String],
    eventNameF: C => String
  )(implicit
    commandCodec: GenCodec[C],
    updateCodec: GenCodec[U],
    cs: ContextShift[IO]
  ): Resource[IO, EventStream[IO, Stream[IO, ?], C, U]] = {

    def decode(arg: AnyRef): U = ???

    def create(queue: Queue[IO, U]) = IO {
      val sock = SIO.socket(uri)
      events.foldLeft(sock: Emitter) { (s, ev) =>
        s.on(ev, (args: Array[AnyRef]) => queue.enqueue1(decode(args(0))).unsafeRunSync())
      }
      sock.connect()
    }

    for {
      queue <- Resource.liftF(Queue.bounded[IO, U](1000))
      sock <- Resource.make(create(queue))(s => IO(s.close()))
    } yield new SocketIOEvents(sock, queue, eventNameF)
  }

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val example = make[String, String]("localhost", List("ev1", "ev2", "ev3"), identity)

}