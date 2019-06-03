package pl.edu.agh.gwent.ai

import cats.Order
import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all._
import cats.instances.int._
import cats.instances.list._
import com.avsystem.commons.serialization.GenCodec
import pl.edu.agh.gwent.ai.client.{EventStream, SocketIOEvents}
import pl.edu.agh.gwent.ai.model._
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model.updates._
import fs2._
import pl.edu.agh.gwent.ai.learning.MetaGameHandler


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
      "update:info" -> GenCodec[InfoUpdate],
      "gameover" -> GenCodec[GameOver]
    )

    def simpleHandler(
      es: EventStream[IO, Stream[IO, ?], Command, Update]
    ): IO[Unit] = {

      implicit val order: Order[Card] = Order.by[Card, Int](_._data.power)

      def selectCard(state: GameState): Option[Card] = {
        state.ownHand.cards
          .iterator
          .filter(_._data.ability.abilityCode != "medic")
          .toList
          .maximumOption
      }

      def handleTick(old: GameState, shouldWait: Boolean): IO[(GameState, Boolean)] = for {
        _ <- if (shouldWait) {
          es.events.collectFirst({ case WaitingUpdate(false) => }).compile.drain
        } else {
          IO.unit
        }
        card <- IO(selectCard(old))
        commands = card.map(MetaGameHandler.generateCommand(_, old)).getOrElse(List.empty)
        _ <- IO(println(s"Selected card: $card"))
        _ <- IO(println(s"Selected commands: $commands"))

        (gs, isOver) <-
          if (commands.isEmpty)
            MetaGameHandler.applyCommand(es, old, List(Pass), shouldWait = false)
          else
            MetaGameHandler.applyCommand(es, old, commands, shouldWait = false)

      } yield (gs, isOver)

      for {
        gs <- MetaGameHandler.initGameState(es, "test42")
        _ <- Stream.iterateEval(gs)((handleTick _).tupled).compile.drain
      } yield ()
    }

    SocketIOEvents.make[Command, Update]("http://localhost:16918", events, _.event, _.hasBody).use { es =>
      simpleHandler(es) as ExitCode.Success
    }

  }
}
