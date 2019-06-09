package pl.edu.agh.gwent.ai.model

import java.nio.DoubleBuffer

import org.deeplearning4j.rl4j.space.Encodable
import pl.edu.agh.gwent.ai.model.updates.{FieldsUpdate, HandUpdate, InfoUpdate, PassingUpdate}

//assuming cards ids go from 1 to cardNum and cardNum is smaller than a byte
case class GameInstance(cardNum: Int) {

  //state encoding leaders, sides, fields, hands, round, passing
  val size: Int = 2 + 2 * (1 + cardNum) + 2 * 4 * cardNum + 2 * cardNum + 1 + 2

  case class GameState(
    ownLeader: Card,
    foeLeader: Card,
    ownSideN: String,
    foeSideN: String,
    ownSide: BattleSide,
    foeSide: BattleSide,
    ownFields: FieldState,
    foeFields: FieldState,
    ownHand: HandState,
    foeHand: HandState,
    round: Int = 1,
  ) extends Encodable {

    private def encodeLeader(card: Card) = (ownLeader._id - cardNum / 2).toDouble / cardNum * 2

    private def encodeCardSet(writer: Iterator[Boolean] => Unit)(cards: Set[Card]): Unit = {
      val containedIds = cards.map(_._id)
      writer(for (id <- (1 to cardNum).iterator) yield containedIds.contains(id))
    }

    override def toArray: Array[Double] = {
      val buffer = DoubleBuffer.allocate(size)
      buffer.put(encodeLeader(ownLeader))
      buffer.put(encodeLeader(foeLeader))

      val encodeCardsImpl = { cards: Set[Card] =>
        encodeCardSet(it => buffer.put(it.map(c => if (c) 1.0 else -1.0).toArray))(cards)
      }
      val encodeSideImpl = { side: BattleSide =>
        buffer.put((side.lives - 1).toDouble / 2)
        encodeCardsImpl(side.discard)
      }
      encodeSideImpl(ownSide)
      encodeSideImpl(foeSide)

      def encodeFieldState(state: FieldState) = {
        encodeCardsImpl(state.close.cards)
        encodeCardsImpl(state.ranged.cards)
        encodeCardsImpl(state.siege.cards)
        encodeCardsImpl(state.weather.cards)
      }

      encodeFieldState(ownFields)
      encodeFieldState(foeFields)

      encodeCardsImpl(ownHand.cards)
      encodeCardsImpl(foeHand.cards)

      buffer.put((round - 1.5) / 1.5 )

      buffer.put(if (ownSide.isPassing) 1.0 else -1.0)
      buffer.put(if (foeSide.isPassing) 1.0 else -1.0)

      buffer.array()
    }

    def applyUpdate(update: InfoUpdate): GameState = {
        if (update._roomSide == ownSideN)
          copy(ownSide = update.info)
        else
          copy(foeSide = update.info)
    }

    def applyUpdate(update: FieldsUpdate): GameState = {
      val fields = FieldState(update.close, update.ranged, update.siege, update.weather)
      if (update._roomSide == ownSideN)
        copy(ownFields = fields)
      else
        copy(foeFields = fields)
    }

    def applyUpdate(update: HandUpdate): GameState = {
      if (update._roomSide == ownSideN)
        copy(ownHand = HandState(update.cards))
      else
        copy(foeHand = HandState(update.cards))
    }

    def applyPassing(update: PassingUpdate): GameState = {
      if (!update.passing) {
        copy(round = round + 1)
      } else {
        this
      }

    }

  }
}

case class HandState(
  cards: Set[Card]
)

case class FieldState(
  close: Field,
  ranged: Field,
  siege: Field,
  weather: Field,
) {
  def applyUpdate(update: FieldsUpdate): FieldState = FieldState(
    close = update.close,
    ranged = update.ranged,
    siege = update.siege,
    weather = update.weather,
  )
}
