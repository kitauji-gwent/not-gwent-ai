package pl.edu.agh.gwent.ai.client

import java.net.URI

import atto._, Atto._
import cats.effect._
import com.avsystem.commons.serialization.GenCodec
import com.avsystem.commons.serialization.json.{JsonStringInput, JsonStringOutput}
import fs2.concurrent.{Queue, SignallingRef}
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import pl.edu.agh.gwent.ai.client.WebSocketES._

class WebSocketES[C, U] private(
  client: JsonWebsocketResource,
  commandNameF: C => String)(implicit
  commandCodec: GenCodec[C],
  updateCodec: GenCodec[U],
  cs: ContextShift[IO],
) extends EventStream[IO, fs2.Stream[IO, ?], C, U] {

  private def encodeToProtocol(c: C) = {
    val jsString = JsonStringOutput.write(c)
    s"42[${commandNameF(c)},$jsString]"
  }

  private def decodeFromProtocol(msgStr: String) = IO {
    msgStr match {
      case s42(_, content) =>
        JsonStringInput.read[U](content)
      case s =>
        throw new Exception(s"Wrong shape of message: $s")
    }
  }

  override def process(commands: fs2.Stream[IO, C], consumer: U => IO[Unit]): IO[Unit] = for {
    prod <- commands
      .evalTap(c => IO(client.send(encodeToProtocol(c))))
      .interruptWhen(client.endSignal.map(_ => true))
      .compile.drain.start
    cons <- client.queue.dequeue
      .interruptWhen(client.endSignal.map(_ => true))
      .evalTap(c => decodeFromProtocol(c).flatMap(consumer))
      .compile.drain.start
    _ <- prod.join
    _ <- cons.join
  } yield ()
}

object WebSocketES {

  private class JsonWebsocketResource(
    uri: URI,
    val queue: Queue[IO, String],
    val endSignal: SignallingRef[IO, Boolean]
  ) extends WebSocketClient(uri) {
    override def onOpen(handshakedata: ServerHandshake): Unit = {
      println(s"Opened connectionwith message: ${handshakedata.getHttpStatusMessage}")
    }
    override def onMessage(message: String): Unit =
      queue.enqueue1(message).unsafeRunSync()
    override def onClose(code: Int, reason: String, remote: Boolean): Unit =
      endSignal.set(true).unsafeRunSync()
    override def onError(ex: Exception): Unit = {
      println(s"Connection to server failed: $ex")
      endSignal.set(true).unsafeRunSync()
    }
  }

  def make[C, U](
    uri: String,
    commandNameF: C => String
  )(implicit
    commandCodec: GenCodec[C],
    updateCodec: GenCodec[U],
    cs: ContextShift[IO],
  ): Resource[IO, WebSocketES[C, U]] = {
    def create = for {
      queue <- Queue.bounded[IO, String](1000)
      endSignal <- SignallingRef[IO, Boolean](false)
      ws <- IO {
        val client = new JsonWebsocketResource(new URI(uri), queue, endSignal)
        client.connectBlocking()
        client
      }
    } yield ws

    Resource.make(create)(ws => IO(ws.close())).map(ws => new WebSocketES(ws, commandNameF))
  }

  private[WebSocketES] object s42 {

    private val eventName = char('\"') ~> stringOf(letter | digit | whitespace | char(':')) <~ char('\"')
    private val eventBody = stringOf(letter | digit | whitespace | oneOf("{}\",:_"))

    private val parser =
      string("42") ~> envelopes(eventName ~ (char(',') ~> eventBody))

    def unapply(arg: String): Option[(String, String)] = parser.parse(arg).option
  }

}