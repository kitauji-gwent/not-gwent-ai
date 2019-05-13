package pl.edu.agh.gwent.ai.client

trait EventStream[F[_], S[_], C, U] {
  def publish(commands: S[C]): F[Unit]
  def publish1(c: C): F[Unit]
  def events: S[U]
}
