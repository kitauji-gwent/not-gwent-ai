package pl.edu.agh.gwent.ai

import cats.Order
import cats.effect._
import cats.instances.int._
import cats.instances.list._
import cats.syntax.all._
import fs2._
import pl.edu.agh.gwent.ai.client.{EventStream, SocketIOEvents}
import pl.edu.agh.gwent.ai.learning.GwentMDP.{EnviromentSetup, HandWrittenBot}
import pl.edu.agh.gwent.ai.learning.{GwentMDP, MetaGameHandler}
import pl.edu.agh.gwent.ai.model._
import pl.edu.agh.gwent.ai.model.commands._
import pl.edu.agh.gwent.ai.model.updates._


object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    def simpleHandler(
      es: EventStream[IO, Stream[IO, ?], Command, Update],
      name: String
    ): IO[Unit] = {

      implicit val order: Order[Card] = Order.by[Card, Int](_._data.power)

      def selectCard(state: GameState): Option[Card] = {
        state.ownHand.cards
          .iterator
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
        commands = card.map(MetaGameHandler.generateCommand(defaultInstance)(_, old)).getOrElse(List.empty)
        _ <- IO(println(s"Selected card: $card"))
        _ <- IO(println(s"Selected commands: $commands"))

        (gs, isOver) <-
          if ((old.ownSide.score > old.foeSide.score && old.foeSide.isPassing) || commands.isEmpty)
            MetaGameHandler.applyCommand(defaultInstance)(es, old, List(Pass), shouldWait = false)
          else
            MetaGameHandler.applyCommand(defaultInstance)(es, old, commands, shouldWait = false)

      } yield (gs, isOver)

      for {
        (gs, shouldWait) <- MetaGameHandler.initGameState(defaultInstance)(es, name)
        _ <- IO(println(s"Current state: ${gs.toArray.mkString("[", ", ", "]")}"))
        _ <- Stream.iterateEval((gs, shouldWait, false))({
          case (state, sw, end) =>
            handleTick(state, sw).map { case (ns, nend) => (ns, false, nend) }
        }).takeWhile(!_._3).compile.drain
      } yield ()
    }

    SocketIOEvents.make[Command, Update]("http://localhost:16918", GwentMDP.events, _.event, _.hasBody).use { es =>
      for {
        myStream <- GwentMDP.manualyClosed(
          EnviromentSetup("player1", defaultInstance, Map.empty),
          player = new HandWrittenBot(es, "player2", defaultInstance)
        ).flatten
        _ <- simpleHandler(myStream, "player1")
      } yield ExitCode.Success
    } as ExitCode.Success

  }
}
