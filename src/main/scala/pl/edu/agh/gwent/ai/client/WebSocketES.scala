package pl.edu.agh.gwent.ai.client

import akka.actor.ActorSystem
import cats.effect.{ContextShift, IO}
import com.avsystem.commons.serialization.GenCodec
import akka.http.scaladsl.Http
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.http.scaladsl.model.ws._
import com.avsystem.commons.serialization.json.{JsonStringInput, JsonStringOutput}
import cats.syntax.all._

import WebSocketES._

class WebSocketES[C, U](commandNameF: C => String)(implicit
  commandCodec: GenCodec[C],
  updateCodec: GenCodec[U],
  cs: ContextShift[IO],
  as: ActorSystem,
  mat: Materializer
) extends EventStream[IO, fs2.Stream[IO, ?], C, U] {

  private def encodeToProtocol(c: C) = {
    val jsString = JsonStringOutput.write(c)
    TextMessage(s"42[${commandNameF(c)},$jsString]")
  }

  private def decodeFromProtocol(u: Message) = IO {
    val msgStr = u.asTextMessage.getStrictText
    msgStr match {
      case s42(tpe, content) =>
        JsonStringInput.read[U](content)
      case s =>
        throw new Exception(s"Wrong shape of message: $s")
    }
  }

  override def process(commands: fs2.Stream[IO, C], consumer: U => IO[Unit]): IO[Unit] = for {
    sink <- IO(Sink.foreachAsync[Message](1)(decodeFromProtocol(_).flatMap(consumer).unsafeToFuture()))
    (sourceQueue, source) <- IO(Source.queue[Message](1000, OverflowStrategy.backpressure).preMaterialize())
    flow <- IO(Flow.fromSinkAndSource(sink, source))
    producer <- commands.evalMap(c => IO.fromFuture(IO(sourceQueue.offer(encodeToProtocol(c))))).compile.drain.start
    _ <- IO(Http().singleWebSocketRequest(WebSocketRequest("ws://127.0.0.1"), flow)) <* producer.join
  } yield ()
}

object WebSocketES {

  private[WebSocketES] object s42 {
    def unapply(arg: String): Option[(String, String)] = ???
  }

}