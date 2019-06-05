package pl.edu.agh.gwent.ai.learning

import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.Deferred
import cats.instances.option._
import cats.syntax.all._
import fs2._
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model.updates._
import pl.edu.agh.gwent.ai.model._

import scala.concurrent.ExecutionContext

object MetaGameHandler {

  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  val `Northern-Kingdoms` = "northern"

  def initGameState(inst: GameInstance)(es: GameES, envName: String): IO[(inst.GameState, Boolean)] = {
    import inst._

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
          .mapN(GameState(_, _,ownSideN, foeSideN, _, _, _, _, _, _)) <* isFirst

    }

    case class PlaceHolder(roomID: String) extends InternalGS {
      override def build: Option[GameState] = None
      override def isFirst: Option[Boolean] = None
    }

    val placeHolder: (List[Command], InternalGS) = List.empty -> PlaceHolder("")

    def go(state: InternalGS, update: Update): (List[Command], InternalGS) = {
      def handleStart(update: Update) = update match {
        case u@NameUpdate(n) if n.startsWith("Guest") =>
          List.empty -> PlaceHolder("")
        case u@NameUpdate(_) =>
          List.empty -> PlaceHolder("")
        case u@JoinRoom(id) =>
          List.empty -> PlaceHolder(id)
        case u@InitBattle(side, fside) =>
          val roomId = state.asInstanceOf[PlaceHolder].roomID
          List(GameLoaded(roomId), FinishRedraw) ->
            StateBuilder(roomId, side, fside)
        case u =>
          List.empty -> state
      }

      def handleInit(state: StateBuilder)(update: Update) = update match {
        case HandUpdate(_roomSide, cards) if _roomSide == state.ownSideN =>
          val newState = state.copy(ownHand = HandState(cards).some)
          List.empty -> newState
        case HandUpdate(_, cards) =>
          val newState = state.copy(foeHand = HandState(cards).some)
          List.empty -> newState
        case InfoUpdate(_roomSide, info, l) if _roomSide == state.ownSideN =>
          val newState = state.copy(ownLeader = l.some, ownSide = info.some)
          List.empty -> newState
        case InfoUpdate(_, info, l) =>
          val newState = state.copy(foeLeader = l.some, foeSide = info.some)
          List.empty -> newState
        case InitBattle(_, _) =>
          List(GameLoaded(state.roomID), FinishRedraw) -> state
        case FieldsUpdate(_roomSide, c, r, s, w) if _roomSide == state.ownSideN =>
          val fields = FieldState(close = c, ranged = r, siege = s, weather = w)
          val newState = state.copy(ownFields = fields.some)
          List.empty -> newState
        case FieldsUpdate(_, c, r, s, w) =>
          val fields = FieldState(close = c, ranged = r, siege = s, weather = w)
          val newState = state.copy(foeFields = fields.some)
          List.empty -> newState
        case u@WaitingUpdate(waiting) =>
          println(s"Got init($envName): $u")
          List.empty -> state.copy(isFirst = Some(waiting))
        case _ =>
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
      gs <- es.events.evalScan(placeHolder)({
        case ((oldComs, oldState), update) =>
          val p@(commands, _) = go(oldState, update)
          es.publish(Stream.emits(commands)) as p
      }).collectFirst({
        case (_, gs) if gs.build.isDefined =>
          println(s"Reporting ($envName) ${gs.isFirst.get}")
          (gs.build product gs.isFirst).get
      }).compile.toList
    } yield gs.head

  }

  /**
    * Applies command and returns new game state, as well as information if round has ended
    *
    * @param es connection to the game
    * @return
    */
  def applyCommand(inst: GameInstance)(es: GameES, oldState: inst.GameState, command: List[GameCommand], shouldWait: Boolean): IO[(inst.GameState, Boolean)] = {
    import inst._

    def handleUpdate(overSignal: Deferred[IO, Boolean])(
      currentState: GameState,
      update: Update
    ): IO[(GameState, Boolean)] = {
      update match {
        case i: InfoUpdate =>        IO.pure((currentState.applyUpdate(i), false))
        case f: FieldsUpdate =>      IO.pure((currentState.applyUpdate(f), false))
        case h: HandUpdate =>
          println(s"Updating hand: ${h._roomSide}, ${h.cards.map(_._id).mkString("{", ", ", "}")}")
                                     IO.pure((currentState.applyUpdate(h), false))

        case p: PassingUpdate =>
          println(s"Updating round state: $p")
                                     IO.pure((currentState.applyPassing(p), false))
        case WaitingUpdate(false) => IO.pure((currentState, true))

        case GameOver(_)          =>
          overSignal.complete(true) as (currentState, true)
        case _ =>                    IO.pure((currentState, false))
      }
    }

    val code = for {
      isOverP <- Deferred[IO, Boolean]
      _ <- if (shouldWait) {
        es.events.collectFirst({ case WaitingUpdate(false) => }).compile.drain
      } else {
        IO.unit
      }
      _ <- es.publish(Stream.emits(command))
      gs <- es.events
        .evalScan(oldState -> false) {
          case ((oldGs, false), up) =>
            handleUpdate(isOverP)(oldGs, up)
          case (p, _) =>
            IO.pure(p)
        }
        .collectFirst {
          case (s, true) =>
            s
        }
        .compile
        .toList
      _ <- isOverP.complete(false).handleError(_ => ())
      isOver <- isOverP.get
    } yield gs.head -> isOver

    if (command.nonEmpty) code else IO.pure(oldState -> false)
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
  def generateCommand(inst: GameInstance)(card: Card, state: inst.GameState): List[GameCommand] = {

    def canReplace(card: Card) = !(Set("hero", "decoy") contains card._data.ability.abilityCode)

    def spiesOf(cards: Iterable[Card]) = cards.iterator.filter(_._data.ability.abilityCode == "spy")

    def regularsOf(cards: Iterable[Card]) = cards.iterator.filter(canReplace)

    if (!state.ownHand.cards.exists(_._id == card._id)) {
      List.empty
    } else if (card._data.ability.abilityCode == "decoy") {

      val cards = (state.ownFields.siege.cards ++ state.ownFields.ranged.cards ++ state.ownFields.close.cards).toArray
      val spies = spiesOf(cards)
      val normals = regularsOf(cards)

      val randomCard =
        if (spies.nonEmpty) spies.minBy(_._data.power).some
        else if (normals.nonEmpty) normals.maxBy(_._data.power).some
        else None

      randomCard match {
        case Some(target) =>
          List(PlayCard(card._id), DecoyReplaceWith(target._id))
        case None =>
          List.empty
      }

    } else if (card._key == "commanders_horn") {

      val (_, maxType) = List(state.ownFields.close -> CardType.CloseCombat,
                        state.ownFields.ranged -> CardType.Ranged,
                        state.ownFields.siege -> CardType.Siege).maxBy(_._1.score)

      List(PlayCard(card._id), SelectHorn(maxType))
    } else if (card._data.ability.abilityCode == "medic") {

      def selectMedic(currentDiscard: Set[Card]): List[GameCommand] = {
        val cards   = currentDiscard.iterator.filter(_._data.ability.abilityCode != "decoy").toArray
        val medics  = cards.iterator.filter(_._data.ability.abilityCode == "medic")
        val spies   = spiesOf(cards)
        val normals = regularsOf(cards)

        if (medics.nonEmpty) {
          val selected = medics.maxBy(_._data.power)
          MedicChooseCard(selected._id.some) :: selectMedic(currentDiscard - selected)
        } else if (spies.nonEmpty) {
          MedicChooseCard(spies.minBy(_._data.power)._id.some) :: Nil
        } else if (normals.nonEmpty) {
          MedicChooseCard(normals.maxBy(_._data.power)._id.some) :: Nil
        } else {
          MedicChooseCard(None) :: Nil
        }
      }

      PlayCard(card._id) :: selectMedic(state.ownSide.discard - card)
    } else {
      List(PlayCard(card._id))
    }

  }

}
