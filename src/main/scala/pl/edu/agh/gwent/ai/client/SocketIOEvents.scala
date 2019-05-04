package pl.edu.agh.gwent.ai.client

import cats.effect.IO
import com.avsystem.commons.serialization.GenCodec
import fs2._
import io.socket.client.Socket

class SocketIOEvents[C, U] (
  socket: Socket,
  eventNameF: C => String
)(implicit
  commandCodec: GenCodec[C],
  updateCodec: GenCodec[U]
) extends EventStream[IO, Stream[IO, ?], C, U] {

  private def encode(command: C): AnyRef = ???

  override def publish(commands: Stream[IO, C]): IO[Unit] =
    commands.evalMap(c => IO(socket.emit(eventNameF(c), encode(c)))).compile.drain

  override def events: Stream[IO, U] = ???
}


object SocketIOEvents {

}