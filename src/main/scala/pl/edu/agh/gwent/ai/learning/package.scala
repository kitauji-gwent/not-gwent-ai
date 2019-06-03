package pl.edu.agh.gwent.ai

import cats.effect.IO
import org.deeplearning4j.rl4j.mdp.MDP
import org.deeplearning4j.rl4j.space.DiscreteSpace
import pl.edu.agh.gwent.ai.client.EventStream
import pl.edu.agh.gwent.ai.model.commands.Command
import pl.edu.agh.gwent.ai.model.updates.Update

package object learning {

  type GameES = EventStream[IO, fs2.Stream[IO, ?], Command, Update]

  type DiscreteMDP[O] = MDP[O, Integer, DiscreteSpace]

}
