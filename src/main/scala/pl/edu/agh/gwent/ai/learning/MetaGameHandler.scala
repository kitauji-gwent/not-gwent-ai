package pl.edu.agh.gwent.ai.learning

import cats.effect.IO
import cats.effect.concurrent.Deferred
import cats.syntax.all._
import cats.instances.option._
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model.updates._
import pl.edu.agh.gwent.ai.model.{BattleSide, Card, Field, FieldState, GameState, HandState}
import fs2._

import scala.util.Random


object MetaGameHandler {

  val `Northern-Kingdoms` = "northern"

  def initGameState(es: GameES, envName: String): IO[GameState] = {

    sealed trait InternalGS {
      def build: Option[GameState]
    }

    case class StateBuilder(
      roomID: String,
      ownSideN: String,
      foeSideN: String,
      ownLeader: Option[Card] = None,
      foeLeader: Option[Card] = None,
      ownSide: Option[BattleSide] = None,
      foeSide: Option[BattleSide] = None,
      ownFields: Option[FieldState] = None,
      foeFields: Option[FieldState] = None,
      ownHand: Option[HandState] = None,
      foeHand: Option[HandState] = None
    ) extends InternalGS {

      def build: Option[GameState] =
        (ownLeader, foeLeader, ownSide, foeSide, ownFields, foeFields, ownHand, foeHand)
          .mapN(GameState(_, _, _, _, _, _, _, _))

    }

    case class PlaceHolder(roomID: String) extends InternalGS {
      override def build: Option[GameState] = None
    }

    val placeHolder: (List[Command], InternalGS) = List.empty -> PlaceHolder("")

    def go(state: InternalGS, update: Update): (List[Command], InternalGS) = {
      def handleStart(update: Update) = update match {
        case u@NameUpdate(n) if n.startsWith("Guest") =>
          println(s"Got: $u")
          List.empty -> PlaceHolder("")
        case u@NameUpdate(_) =>
          println(s"Got: $u")
          List.empty -> PlaceHolder("")
        case u@JoinRoom(id) =>
          println(s"Got: $u")
          List.empty -> PlaceHolder(id)
        case u@InitBattle(side, fside) =>
          println(s"Got: $u")
          val roomId = state.asInstanceOf[PlaceHolder].roomID
          List(GameLoaded(roomId), FinishRedraw) ->
            StateBuilder(roomId, side, fside)
        case u =>
          println(s"Go unexpected: $u")
          List.empty -> state
      }

      def handleInit(state: StateBuilder)(update: Update) = update match {
        case u@HandUpdate(_roomSide, cards) if _roomSide == state.ownSideN =>
          println(s"Got init: $u")
          val newState = state.copy(ownHand = HandState(cards).some)
          List.empty -> newState
        case u@HandUpdate(_, cards) =>
          println(s"Got init: $u")
          val newState = state.copy(foeHand = HandState(cards).some)
          List.empty -> newState
        case u@InfoUpdate(_roomSide, info, l) if _roomSide == state.ownSideN =>
          println(s"Got init: $u")
          val newState = state.copy(ownLeader = l.some, ownSide = info.some)
          List.empty -> newState
        case u@InfoUpdate(_, info, l) =>
          println(s"Got init: $u")
          val newState = state.copy(foeLeader = l.some, foeSide = info.some)
          List.empty -> newState
        case u@InitBattle(_, _) =>
          println(s"Got init: $u")
          List(GameLoaded(state.roomID), FinishRedraw) -> state
        case u@FieldsUpdate(_roomSide, c, r, s, w) if _roomSide == state.ownSideN =>
          println(s"Got init: $u")
          val fields = FieldState(close = c, ranged = r, siege = s, weather = w)
          val newState = state.copy(ownFields = fields.some)
          List.empty -> newState
        case u@FieldsUpdate(_, c, r, s, w) =>
          println(s"Got init: $u")
          val fields = FieldState(close = c, ranged = r, siege = s, weather = w)
          val newState = state.copy(foeFields = fields.some)
          List.empty -> newState
        case u =>
          println(s"Got init: $u")
          List.empty -> state
      }

      state match {
        case b: StateBuilder =>
          handleInit(b)(update)
        case _ =>
          handleStart(update)
      }

    }


    for {
      _ <- es.publish(Stream(Name(envName), ChooseDeck(`Northern-Kingdoms`), Enqueue))
      gs <- es.events.scan(placeHolder)((old, up) => go(old._2, up)).evalMap({
        case (commands, state) =>
          es.publish(Stream.emits(commands)) as state.build
      }).collectFirst({ case Some(gs) => gs }).compile.toList
    } yield gs.head

  }

  /**
    * Applies command and returns new game state, as well as information if round has ended
    * @param es connection to the game
    * @return
    */
  def applyCommand(es: GameES, command: List[GameCommand]): IO[(GameState, Boolean)] = ???


  private val random = new Random()
  def generateCommand(card: Card, state: GameState): List[GameCommand] = {

    def canReplace(card: Card) = !(Set("hero", "decoy") contains card._data.ability.abilityCode)

    if (card._data.ability.abilityCode == "decoy") {
      val randomCard =
        random.shuffle(state.ownFields.siege.cards ++ state.ownFields.ranged.cards ++ state.ownFields.close.cards)
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

  }

}
