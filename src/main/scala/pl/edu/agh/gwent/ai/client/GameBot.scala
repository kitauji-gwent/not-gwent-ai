package pl.edu.agh.gwent.ai.client

trait GameBot[F[_]] {

  def bet: Unit

}
