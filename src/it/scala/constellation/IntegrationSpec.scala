package constellation

import java.net.InetAddress

import constellation.util.testing.FakeNode
import org.constellation.NetworkLoadbalancer
import org.constellation.primitives.node.{Addr, ErrorBody, Id, Info, NodeState, Reputation}
import org.scalatest.{BeforeAndAfterAll, FunSpec, Matchers}
import cats.data.NonEmptyList
import cats.effect.Timer
import org.http4s.client.blaze.BlazeClientBuilder
import cats.syntax.all._
import io.circe.Json
import org.http4s.client.dsl.io._
import cats.effect.IO
import constellation.util.testing
import org.http4s.Method._
import org.http4s.client.blaze._
import org.http4s.circe._
import org.http4s.Uri._
import io.circe.syntax._
import org.http4s.{Header, Headers, Request, Status}

import scala.concurrent.duration._
import scala.language.postfixOps
import org.http4s.circe.CirceEntityCodec._

class IntegrationSpec extends FunSpec with Matchers with BeforeAndAfterAll {

  implicit val cs               = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  val httpClient = BlazeClientBuilder[IO](scala.concurrent.ExecutionContext.global).resource

  val fakeSetup = NonEmptyList.of(
    Info(Id("node-1"), Addr(InetAddress.getLoopbackAddress, 9997), NodeState.Ready, Reputation(1), "node-1"),
    Info(Id("node-2"), Addr(InetAddress.getLoopbackAddress, 9999), NodeState.Ready, Reputation(1), "node-2")
  )

  val clusterInit = fakeSetup.map(i => s"localhost:${i.ip.port}").toList

  val nodes =
    fakeSetup
      .map(i => new FakeNode(i.ip.publicPort, i.ip.port, fakeSetup.toList, Json.obj("node-id" -> i.id.hex.asJson)))
      .map(_.run)
      .parSequence
      .flatMap(_ => IO.unit)
      .start
      .unsafeRunSync()

  val lb = NetworkLoadbalancer
    .run(clusterInit)
    .flatMap(_ => IO.unit)
    .start
    .unsafeRunSync()

  override def afterAll(): Unit = {
    nodes.cancel.unsafeRunSync()
    lb.cancel.unsafeRunSync()
  }

  describe("With running loadbalancer and fake nodes") {

    def checkIfReady = httpClient.use(_.expect[String](uri("http://localhost:9000/utils/health")))

    def checkWhileReady: IO[String] = checkIfReady.flatMap {
      case a if a == "2" => IO.pure(a)
      case o             => IO.sleep(100 millis).flatMap(_ => checkWhileReady)
    }

    def enableMaintenance: IO[Unit] =
      httpClient.use {
        _.expect[Unit](Request[IO](method = POST, uri = uri("http://localhost:9001/settings/maintenance")))
      }

    checkWhileReady.unsafeRunSync()

    it("Returns 503 when maintenance mode enabled") {
      enableMaintenance.unsafeRunSync
      val result = httpClient.use {
        _.status(Request[IO](method = POST, uri = uri("http://localhost:9000/dashboard")))
      }.unsafeRunSync

      result should equal(Status.ServiceUnavailable)
    }

    it("Subsequent requests should come from the same node (aka sticky sessions)") {

      val result = httpClient.use(_.expect(uri("http://localhost:9000/dashboard"))(jsonOf[IO, Json])).unsafeRunSync

      httpClient.use(_.expect(uri("http://localhost:9000/dashboard"))(jsonOf[IO, Json])).unsafeRunSync should equal(
        result
      )
    }

    it("Different clients should get results from different nodes when X-Forwarded-From is spoofed") {

      val result = httpClient.use(_.expect(uri("http://localhost:9000/dashboard"))(jsonOf[IO, Json])).unsafeRunSync

      httpClient
        .use(
          _.expect(
            Request[IO](
              uri = uri("http://localhost:9000/dashboard"),
              headers = Headers.of(Header("X-Forwarded-For", "1.2.3.4"))
            )
          )(jsonOf[IO, Json])
        )
        .unsafeRunSync should not equal (result)
    }
  }
}
