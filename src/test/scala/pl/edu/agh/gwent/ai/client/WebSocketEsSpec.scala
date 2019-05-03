package pl.edu.agh.gwent.ai.client

import java.net.InetSocketAddress

import cats.syntax.all._
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO, Resource}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext

class WebSocketEsSpec extends FlatSpec with Matchers {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private val gwentServerResource: Resource[IO, GwentLikeServer] = Resource.make(
    IO {
      val server = new GwentLikeServer(InetSocketAddress.createUnresolved("127.0.0.1", 4242))
      server.start()
      server
    }
  )(s => IO(s.stop()))

  private val testEventStream = WebSocketES.make[String, String]("ws://127.0.0.1:4242", identity)

  "WebSocketES" should "correctly connect to the server" in {
    val resource = for {
      gs <- gwentServerResource
      _ <- testEventStream
    } yield gs.getConnections

    val connectionNumber = resource.use(cons => IO.pure(cons.size())).unsafeRunSync()
    connectionNumber shouldEqual 1
  }

  it should "decode 'gwent-like` messages correctly" in {

    val resource = for {
      gs <- gwentServerResource
      es <- testEventStream
    } yield gs -> es

    val result = resource
      .use({
        case (gs, es) =>
          for {
            con <- IO {
              val con = gs.getConnections.iterator().next()
              gs.sendGwentMessage(con, "test", "{ field: value }")
              con
            }
            messageRef <- Ref[IO].of("")
            _ <- es.process(fs2.Stream.empty.covaryAll, msg => messageRef.set(msg) *> IO(con.close()))
            res <- messageRef.get
          } yield res
      })
      .unsafeRunSync()

    result shouldEqual "{ field: value }"
  }


}

