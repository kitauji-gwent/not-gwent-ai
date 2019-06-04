package pl.edu.agh.gwent.ai

package object model {

  type UserID = String
  type CardID = Int
  type Faction = String

  val `Northern-Kingdoms` = "northern"

  val defaultInstance = GameInstance(100) //not sensible
  type GameState = defaultInstance.GameState
  val GameState = defaultInstance.GameState
}
