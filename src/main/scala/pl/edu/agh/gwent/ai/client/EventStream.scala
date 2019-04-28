package pl.edu.agh.gwent.ai.client

trait EventStream[F[_], S[_], C, U] {
  def process(commands: S[C], consumer: U => F[Unit]): F[Unit]
}
