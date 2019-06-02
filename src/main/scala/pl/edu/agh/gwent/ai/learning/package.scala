package pl.edu.agh.gwent.ai

import cats.effect.IO
import pl.edu.agh.gwent.ai.client.EventStream
import pl.edu.agh.gwent.ai.model.commands.Command
import pl.edu.agh.gwent.ai.model.updates.Update

package object learning {

  type GameES = EventStream[IO, fs2.Stream[IO, ?], Command, Update]

}
