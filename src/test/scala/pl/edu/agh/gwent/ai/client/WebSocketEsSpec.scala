package pl.edu.agh.gwent.ai.client

import java.net.InetSocketAddress

import cats.syntax.all._
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO, Resource}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext

class WebSocketEsSpec extends FlatSpec with Matchers {
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  private def gwentServerResource(attemptNo: Int): Resource[IO, GwentLikeServer] = Resource.make(
    IO {
      val server = new GwentLikeServer(new InetSocketAddress("localhost", 4241 + attemptNo))
      server.start()
      server
    }
  )(s => IO(s.stop(1000)))

  private def testEventStream(attemptNo: Int) = {
    val port = 4241 + attemptNo
    WebSocketES.make[String, String](s"ws://localhost:$port", identity)
  }

  "WebSocketES" should "correctly connect to the server" in {
    val resource = for {
      gs <- gwentServerResource(1)
      _ <- testEventStream(1)
    } yield gs

    val connectionNumber =
      resource
        .use(cons => IO(cons.getConnections.size()))
        .unsafeRunSync()
    connectionNumber shouldEqual 1
  }

  it should "decode 'gwent-like` messages correctly" in {

    val resource = for {
      gs <- gwentServerResource(2)
      es <- testEventStream(2)
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

