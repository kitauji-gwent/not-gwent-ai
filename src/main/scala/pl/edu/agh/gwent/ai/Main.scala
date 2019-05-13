package pl.edu.agh.gwent.ai

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import com.avsystem.commons.serialization.GenCodec
import pl.edu.agh.gwent.ai.client.{EventStream, SocketIOEvents}
import pl.edu.agh.gwent.ai.model.`Northern-Kingdoms`
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model.updates._
import fs2._
import scala.concurrent.duration._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    val events = SocketIOEvents.setupEvents[Update](
      "response:name" -> GenCodec[NameUpdate],
      "init:battle" -> GenCodec[InitBattle],
      "response:joinRoom" -> GenCodec[JoinRoom],
      "set:waiting" -> GenCodec[NoOpAck],
      "set:passing" -> GenCodec[NoOpAck],
      "played:medic" -> GenCodec[NoOpAck],
      "played:emreis_leader4" -> GenCodec[NoOpAck],
      "played:agile" -> GenCodec[NoOpAck],
      "played:horn" -> GenCodec[NoOpAck],
      "update:hand" -> GenCodec[HandUpdate],
      "update:fields" -> GenCodec[FieldsUpdate],
      "update:info" -> GenCodec[InfoUpdate]
    )

    def simpleHandler(
      es: EventStream[IO, Stream[IO, ?], Command, Update]
    ): IO[Unit] = {
      var roomId: String = ""
      val s: Stream[IO, Command] =
        Stream(Name("test42"), ChooseDeck(`Northern-Kingdoms`), Enqueue) ++ es.events.flatMap {
        case u@NameUpdate(n) if n.startsWith("Guest") =>
          println(s"Got: $u")
          Stream.empty
        case u@NameUpdate(_) =>
          println(s"Got: $u")
          Stream.empty
        case u@JoinRoom(id) =>
          println(s"Got: $u")
          roomId = id
          Stream.empty
        case u@InitBattle(_, _) =>
          println(s"Got: $u")
          Stream(GameLoaded(roomId)) ++ Stream(FinishRedraw).delayBy[IO](100.millis)
        case u =>
          println(s"Got: $u")
          Stream.empty
      }
      es.publish(s)
    }

    SocketIOEvents.make[Command, Update]("http://192.168.0.183:16918", events, _.event, _.hasBody).use { es =>
      simpleHandler(es) as ExitCode.Success
    }

  }
}
