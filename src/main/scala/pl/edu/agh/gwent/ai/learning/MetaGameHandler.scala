package pl.edu.agh.gwent.ai.learning

import cats.effect.IO
import cats.instances.option._
import cats.syntax.all._
import fs2._
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model.updates._
import pl.edu.agh.gwent.ai.model._


object MetaGameHandler {

  val `Northern-Kingdoms` = "northern"

  def initGameState(es: GameES, envName: String): IO[(GameState, Boolean)] = {

    sealed trait InternalGS {
      def build: Option[GameState]
      def isFirst: Option[Boolean]
    }

    case class StateBuilder(
      roomID: String,
      ownSideN: String,
      foeSideN: String,
      isFirst: Option[Boolean] = None,
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
          .mapN(GameState(_, _,ownSideN, foeSideN, _, _, _, _, _, _))

    }

    case class PlaceHolder(roomID: String) extends InternalGS {
      override def build: Option[GameState] = None
      override def isFirst: Option[Boolean] = None
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
        case u@WaitingUpdate(waiting) =>
          println(s"Got init: $u")
          List.empty -> state.copy(isFirst = Some(waiting))
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
          es.publish(Stream.emits(commands)) as (state.build product state.isFirst)
      }).collectFirst({ case Some(p) => p }).compile.toList
    } yield gs.head

  }

  /**
    * Applies command and returns new game state, as well as information if round has ended
    *
    * @param es connection to the game
    * @return
    */
  def applyCommand(es: GameES, oldState: GameState, command: List[GameCommand], shouldWait: Boolean): IO[(GameState, Boolean)] = {

    def handleUpdate(
      currentState: GameState,
      update: Update
    ): (GameState, Boolean) = {
      println(s"Current hand: ${currentState.ownSideN}, ${currentState.ownHand.cards.map(_._id).mkString("{", ", ", "}")}")
      update match {
        case i: InfoUpdate =>        (currentState.applyUpdate(i), false)
        case f: FieldsUpdate =>      (currentState.applyUpdate(f), false)
        case h: HandUpdate =>
          println(s"Updating hand: ${h._roomSide}, ${h.cards.map(_._id).mkString("{", ", ", "}")}")
                                     (currentState.applyUpdate(h), false)
        case WaitingUpdate(false) => (currentState, true)
        case _ =>                    (currentState, false)
      }
    }

    val code = for {
      _ <- if (shouldWait) {
        es.events.collectFirst({ case WaitingUpdate(false) => }).compile.drain
      } else {
        IO.unit
      }
      _ <- es.publish(Stream.emits(command))
      gs <- es.events
        .scan(oldState -> false) {
          case ((oldGs, false), up) =>
            handleUpdate(oldGs, up)
          case (p, _) =>
            p
        }
        .collectFirst {
          case (s, true) =>
            s
        }
        .compile
        .toList
    } yield gs.head

    code product IO.pure(false)
  }


  /**
    * Given specific card generate command based on game-state
    * Most cards are fire-and-forget types, while others require more actions
    * (only allowed is decoy, which will be followed by replacing either lowest-ranking spy or highest ranking non-hero)
    *
    * @param card  card to be played
    * @param state game state
    * @return Commands to be sent
    */
  def generateCommand(card: Card, state: GameState): List[GameCommand] = {

    def canReplace(card: Card) = !(Set("hero", "decoy") contains card._data.ability.abilityCode)

    if (!state.ownHand.cards.exists(_._id == card._id)) {
      List.empty
    } else if (card._data.ability.abilityCode == "decoy") {

      val cards = (state.ownFields.siege.cards ++ state.ownFields.ranged.cards ++ state.ownFields.close.cards).toArray

      val spys = cards
        .iterator.filter(_._data.ability.abilityCode == "spy")

      val normals = cards
        .iterator.filter(canReplace)


      val randomCard =
        if (spys.nonEmpty) spys.minBy(_._data.power).some
        else if (normals.nonEmpty) normals.maxBy(_._data.power).some
        else None

      randomCard match {
        case Some(target) =>
          List(PlayCard(card._id), DecoyReplaceWith(target._id))
        case None =>
          List.empty
      }

    } else {
      List(PlayCard(card._id))
    }

  }

}
