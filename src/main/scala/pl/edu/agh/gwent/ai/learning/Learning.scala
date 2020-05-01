package pl.edu.agh.gwent.ai.learning

import cats.effect.IO
import org.deeplearning4j.rl4j.learning.sync.qlearning.QLearning
import org.deeplearning4j.rl4j.learning.sync.qlearning.discrete.QLearningDiscreteDense
import org.deeplearning4j.rl4j.network.dqn.DQNFactoryStdDense
import org.deeplearning4j.rl4j.util.DataManager
import org.nd4j.linalg.learning.config.Adam
import pl.edu.agh.gwent.ai.learning.GwentMDP.EnviromentSetup
import pl.edu.agh.gwent.ai.model.GameInstance

object Learning {

  def main(args: Array[String]): Unit = {

    val config = new QLearning.QLConfiguration(123, //Random seed
      200, //Max step By epoch
      2000, //Max step
      20000, //Max size of experience replay
      32, //size of batches
      50, //target update (hard)
      10, //num step noop warmup
      0.01, //reward scaling
      0.99, //gamma
      1.0, //td-error clipping
      0.15f, //min epsilon
      1000, //num step for eps greedy anneal
      false //double DQN
    )

    val netConf = DQNFactoryStdDense.Configuration.builder().l2(0.00)
      .updater(new Adam(0.001)).numHiddenNodes(50).numLayer(3).build()

    val manager = new DataManager(true)

    val envSetup = EnviromentSetup(
      "test",
      GameInstance(58),
      Map.empty
    )

    val mdpIO = GwentMDP.default(envSetup)

    mdpIO
      .flatMap { mdp =>
        IO {
          val dql = new QLearningDiscreteDense[GameInstance#GameState](mdp, netConf, config, manager)
          dql.train()
          val pol = dql.getPolicy
          pol.save("simple.policy")
          mdp.close()
        }
      }
      .unsafeRunSync()

  }

}
