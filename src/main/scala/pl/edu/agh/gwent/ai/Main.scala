package pl.edu.agh.gwent.ai

import cats.effect._
import cats.syntax.all._
import pl.edu.agh.gwent.ai.learning.GwentMDP.HandWrittenBot
import pl.edu.agh.gwent.ai.model._


object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    new HandWrittenBot("player2", defaultInstance).play.flatMap(_.join) as ExitCode.Success
  }
}
