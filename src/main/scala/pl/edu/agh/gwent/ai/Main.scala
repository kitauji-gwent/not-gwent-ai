package pl.edu.agh.gwent.ai

import cats.effect._
import cats.syntax.all._
import pl.edu.agh.gwent.ai.learning.GwentMDP
import pl.edu.agh.gwent.ai.learning.GwentMDP.HandWrittenBot
import pl.edu.agh.gwent.ai.model._


object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    for {
      myStream <- GwentMDP.manualClosed(player = new HandWrittenBot("player2", defaultInstance)).flatten
      _ <- GwentMDP.playInternal(myStream, defaultInstance, "player1")
    } yield ExitCode.Success

  }
}
