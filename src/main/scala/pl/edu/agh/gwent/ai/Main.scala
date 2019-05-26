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
      "played:horn" -> GenCodec[PlayedUpdate],
      "update:hand" -> GenCodec[HandUpdate],
      "update:fields" -> GenCodec[FieldsUpdate],
      "update:info" -> GenCodec[InfoUpdate]
    )

    def simpleHandler(
      es: EventStream[IO, Stream[IO, ?], Command, Update]
    ): IO[Unit] = {
      var roomId: String = ""

      sealed trait GameState
      case class Hand(info: HandUpdate, side: String, foeSide: String, myFields: FieldState) extends GameState
      case class NoHand(side: String, foeSide: String, myFields: FieldState) extends GameState
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
            val empty = Field(None, Set.empty, 0)
            IO(List(GameLoaded(roomId), FinishRedraw) -> NoHand(side, fside, FieldState(empty, empty, empty, empty)))
          case u =>
            println(s"Go unexpected: $u")
            IO(List.empty -> PlaceHolder)
        }

        def handleInit(state: NoHand)(update: Update) = update match {
          case u@HandUpdate(_roomSide, cards) if _roomSide == state.side =>
            println(s"Got init: $u")
            IO(List.empty -> Hand(u, state.side, state.foeSide, state.myFields))
          case u@InfoUpdate(_roomSide, _, _) if _roomSide == state.side =>
            println(s"Got init: $u")
            IO(List.empty -> state)
          case u@InitBattle(side, fside) =>
            println(s"Got init: $u")
            IO(List(GameLoaded(roomId), FinishRedraw) -> NoHand(side, fside, state.myFields))
          case u@FieldsUpdate(_roomSide, c, r, s, w) if _roomSide == state.side =>
            println(s"Got init: $u")
            val fields = FieldState(close = c, ranged = r, siege = s, weather = w)
            IO(List.empty -> state.copy(myFields = fields))
          case u =>
            println(s"Got init: $u")
            IO(List.empty -> state)
        }

        val random = new Random()
        def randomCard(state: Hand, fields: FieldState) = {

          def canReplace(card: Card) = !(Set("hero", "decoy") contains card._data.ability.abilityCode)

          val selected = random
            .shuffle(state.info.cards.iterator.filter(_._data.ability.abilityCode != "medic"))
            .find(_ => true)
          println(s"Selecting: $selected")
          selected match {
            case Some(card) =>
              if (card._data.ability.abilityCode == "decoy") {
                val randomCard =
                  random.shuffle(fields.siege.cards ++ fields.ranged.cards ++ fields.close.cards)
                    .find(canReplace)

                randomCard match {
                  case Some(target) =>
                    List(PlayCard(card._id), DecoyReplaceWith(target._id))
                  case None =>
                    List(Pass)
                }

              } else {
                List(PlayCard(card._id))
              }

            case None =>
              List(Pass)
          }
        }

        def handleGame(state: Hand)(update: Update) = update match {
          case WaitingUpdate(false) =>
            println(s"Making move")
            if (state.info.cards.nonEmpty)
              IO(randomCard(state, state.myFields) -> state)
            else
              IO(List(Pass) -> state)
          case u@HandUpdate(_roomSide, _) if _roomSide == state.side =>
            println(s"Got game message: $u")
            IO(List.empty -> state.copy(info = u))
          case u@PlayedUpdate(_roomSide, _, "played:horn")  if _roomSide == state.side =>
            println(s"Got game message: $u")
            IO(List(SelectHorn(CardType.CloseCombat)) -> state)
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

    SocketIOEvents.make[Command, Update]("http://localhost:16918", events, _.event, _.hasBody).use { es =>
      simpleHandler(es) as ExitCode.Success
    }

  }
}
