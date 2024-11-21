package constellation

import cats.effect.{IO, Timer}
import cats.instances.list._
import cats.syntax.all._
import constellation.util.testing.FakeNode
import io.circe.Json
import io.circe.syntax._
import org.constellation.NetworkLoadbalancer
import org.constellation.primitives.node.{Id, Info, NodeState}
import org.http4s.Method._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.circe._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{Header, Headers, Request, Status}
import org.scalatest._
import org.scalatest.time.{Seconds, Span}

import java.net.InetAddress
import scala.concurrent.duration._
import scala.language.postfixOps

class IntegrationSpec extends FunSpec with Matchers with BeforeAndAfterAll with SequentialNestedSuiteExecution {

  implicit val cs               = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer: Timer[IO] = IO.timer(scala.concurrent.ExecutionContext.global)

  val fakeSetup = List(
    Info(Id("node-1"), InetAddress.getLoopbackAddress, 9997, 9998, 99L, 123L, NodeState.Ready),
    Info(Id("node-2"), InetAddress.getLoopbackAddress, 9999, 10000, 99L, 456L, NodeState.Ready)
  )

  def nodesServices(nodesInfo: List[Info]) =
    nodesInfo
      .map(i => new FakeNode(i.publicPort, i.p2pPort, nodesInfo, Json.obj("node-id" -> i.id.hex.asJson)))
      .parTraverse(_.run)
      .flatMap(_ => IO.unit)
      .start
      .unsafeRunSync()

  val nodes = nodesServices(fakeSetup)

  val httpClient = BlazeClientBuilder[IO](scala.concurrent.ExecutionContext.global).resource

  def checkIfReady = httpClient.use(_.expect[String](uri"http://localhost:9000/utils/health").recover(_ => "0"))

  def checkWhileReady: IO[String] = checkIfReady.flatMap {
    case a if a == "2" => IO.pure(a)
    case _             => IO.sleep(100 millis).flatMap(_ => checkWhileReady)
  }

  val clusterInit = fakeSetup.map(i => s"localhost:${i.publicPort}")

  val lb = NetworkLoadbalancer
    .run(clusterInit)
    .flatMap(_ => IO.unit)
    .start
    .unsafeRunSync()

  override def beforeAll(): Unit =
    (disableMaintenance >> checkWhileReady.timeout(5.seconds)).unsafeRunSync()

  override def afterAll(): Unit =
    (nodes.cancel >> lb.cancel).unsafeRunSync()

  def enableMaintenance: IO[Unit] =
    httpClient.use {
      _.successful(Request[IO](method = POST, uri = uri"http://localhost:8889/settings/maintenance"))
    }.void

  def disableMaintenance: IO[Unit] =
    httpClient.use {
      _.successful(Request[IO](method = DELETE, uri = uri"http://localhost:8889/settings/maintenance"))
    }.void

  describe("With running loadbalancer and fake nodes") {

    it("Subsequent requests should come from the same node (aka sticky sessions)") {
      val result = for {
        req1 <- httpClient.use(_.expect(uri"http://localhost:9000/dashboard")(jsonOf[IO, Json]))
        req2 <- httpClient.use(_.expect(uri"http://localhost:9000/dashboard")(jsonOf[IO, Json]))
      } yield (req1, req2)
      val (req1, req2) = result.unsafeRunSync()
      req1 shouldEqual req2

    }

    it("Different clients should get results from different nodes when X-Forwarded-From is spoofed") {

      val result = httpClient.use(_.expect(uri"http://localhost:9000/dashboard")(jsonOf[IO, Json])).unsafeRunSync

      httpClient
        .use(
          _.expect(
            Request[IO](
              uri = uri"http://localhost:9000/dashboard",
              headers = Headers.of(Header("X-Forwarded-For", "1.2.3.4"))
            )
          )(jsonOf[IO, Json])
        )
        .unsafeRunSync should not equal (result)
    }

    it("Returns 503 when maintenance mode enabled") {
      enableMaintenance.unsafeRunSync
      val result = httpClient.use {
        _.status(Request[IO](method = GET, uri = uri"http://localhost:9000/dashboard"))
      }.unsafeRunSync
      result should equal(Status.ServiceUnavailable)
      disableMaintenance.unsafeRunSync
    }
  }

  describe("Filter out nodes that switched session") {

    import concurrent.Eventually._
    implicit val patienceConfig =
      PatienceConfig(timeout = scaled(Span(30, Seconds)), interval = scaled(Span(1, Seconds)))

    val newNodes = List(
      Info(Id("node-3"), InetAddress.getLoopbackAddress, 19997, 19998, 99L, 1123L, NodeState.Ready),
      Info(Id("node-4"), InetAddress.getLoopbackAddress, 19999, 20000, 99L, 1456L, NodeState.Ready)
    )

    val newSessionNodes = newNodes.map(_.copy(clusterSession = 199L))

    it("New nodes joined should appear in LB") {

      //restart the nodes with new nodes
      nodes.cancel.unsafeRunSync()
      val newNodeSvcs = nodesServices(fakeSetup ++ newNodes)

      eventually { checkIfReady.unsafeRunSync() shouldBe "4" }

      newNodeSvcs.cancel.unsafeRunSync()
      checkEventualSize(newNodes, 4)
    }

    it("Nodes that change session should be removed") {

      //restart the nodes with new nodes
      nodes.cancel.unsafeRunSync()

      checkEventualSize(newNodes, 4)
      checkEventualSize(newSessionNodes, 2)

    }

    def checkEventualSize(extraNodes: List[Info], size: Int) = {
      val newNodeSvcs = nodesServices(fakeSetup ++ extraNodes)
      eventually { checkIfReady.unsafeRunSync() shouldBe s"$size" }
      newNodeSvcs.cancel.unsafeRunSync()
    }
  }
}
