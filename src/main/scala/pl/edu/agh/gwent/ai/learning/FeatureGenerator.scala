package pl.edu.agh.gwent.ai.learning

import org.deeplearning4j.rl4j.learning.sync.qlearning.QLearning
import org.deeplearning4j.rl4j.learning.sync.qlearning.discrete.QLearningDiscreteDense
import org.deeplearning4j.rl4j.mdp.MDP
import org.deeplearning4j.rl4j.network.dqn.DQNFactoryStdDense
import org.nd4j.linalg.learning.config.Adam


object FeatureGenerator {

  val env = new MDP


  val config = new QLearning.QLConfiguration(
    123,    //Random seed
    200,    //Max step By epoch
    150000, //Max step
    150000, //Max size of experience replay
    32,     //size of batches
    500,    //target update (hard)
    10,     //num step noop warmup
    0.01,   //reward scaling
    0.99,   //gamma
    1.0,    //td-error clipping
    0.1f,   //min epsilon
    1000,   //num step for eps greedy anneal
    true    //double DQN
  )

  val factory = DQNFactoryStdDense.Configuration.builder()
    .l2(0.001).updater(new Adam(0.0005)).numHiddenNodes()
}
