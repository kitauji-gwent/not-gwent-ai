package pl.edu.agh.gwent.ai

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import com.avsystem.commons.serialization.GenCodec
import pl.edu.agh.gwent.ai.client.{EventStream, SocketIOEvents}
import pl.edu.agh.gwent.ai.model._
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model.updates._
import fs2._

import scala.util.Random


object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    val events = SocketIOEvents.setupEvents[Update](
      "response:name" -> GenCodec[NameUpdate],
      "init:battle" -> GenCodec[InitBattle],
      "response:joinRoom" -> GenCodec[JoinRoom],
      "set:waiting" -> GenCodec[WaitingUpdate],
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

      sealed trait GameState
      case class Hand(info: HandUpdate, side: String, foeSide: String) extends GameState
      case class NoHand(side: String, foeSide: String) extends GameState
      case object PlaceHolder extends GameState

      val placeHolder: (List[Command], GameState) = List.empty -> PlaceHolder

      def go(state: GameState, update: Update): IO[(List[Command], GameState)] = {
        def handleStart(update: Update) = update match {
          case u@NameUpdate(n) if n.startsWith("Guest") =>
            println(s"Got: $u")
            IO(List.empty -> PlaceHolder)
          case u@NameUpdate(_) =>
            println(s"Got: $u")
            IO(List.empty -> PlaceHolder)
          case u@JoinRoom(id) =>
            println(s"Got: $u")
            roomId = id
            IO(List.empty -> PlaceHolder)
          case u@InitBattle(side, fside) =>
            println(s"Got: $u")
            IO(List(GameLoaded(roomId), FinishRedraw) -> NoHand(side, fside))
          case u =>
            println(s"Go unexpected: $u")
            IO(List.empty -> PlaceHolder)
        }

        def handleInit(state: NoHand)(update: Update) = update match {
          case u@HandUpdate(_roomSide, cards) if _roomSide == state.side =>
            println(s"Got init: $u")
            IO(List.empty -> Hand(u, state.side, state.foeSide))
          case u@InfoUpdate(_roomSide, _, _) if _roomSide == state.side =>
            println(s"Got init: $u")
            IO(List.empty -> state)
          case u@InitBattle(side, fside) =>
            println(s"Got init: $u")
            IO(List(GameLoaded(roomId), FinishRedraw) -> NoHand(side, fside))
          case u@FieldsUpdate(_roomSide, _, _, _, _) if _roomSide == state.side =>
            println(s"Got init: $u")
            IO(List.empty -> state)
          case u =>
            println(s"Got init: $u")
            IO(List.empty -> state)
        }

        val random = new Random()
        def randomCard(state: Hand) = {
          val selected = random.shuffle(state.info.cards.iterator).next()
          println(s"Selecting: $selected")
          selected._id
        }

        def handleGame(state: Hand)(update: Update) = update match {
          case WaitingUpdate(false) =>
            println(s"Making move")
            IO(List(PlayCard(randomCard(state))) -> state)
          case u@HandUpdate(_roomSide, _) if _roomSide == state.side =>
            println(s"Got game message: $u")
            IO(List.empty -> state.copy(info = u))
          case u =>
            println(s"Got game message: $u")
            IO(List.empty -> state)
        }

        state match {
          case h: Hand => handleGame(h)(update)
          case nh: NoHand => handleInit(nh)(update)
          case _ => handleStart(update)
        }
      }

      val s: Stream[IO, Command] =
        Stream(Name("test42"), ChooseDeck(`Northern-Kingdoms`), Enqueue) ++
          es.events.evalScan(placeHolder)((old, up) => go(old._2, up)).flatMap(p => Stream(p._1:_*))
      es.publish(s)
    }

    SocketIOEvents.make[Command, Update]("http://192.168.0.183:16918", events, _.event, _.hasBody).use { es =>
      simpleHandler(es) as ExitCode.Success
    }

  }
}
