package constellation.util.testing

import java.net.InetAddress
import java.util.concurrent.Executors

import cats.data.NonEmptyList
import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeBuilder
import cats.implicits._
import com.sun.org.apache.xalan.internal.lib.NodeInfo
import org.constellation.primitives.node.{Addr, Codecs, Id, Info, NodeState}
import io.circe.generic.auto._
import io.circe.Json
import io.circe.Json.JArray
import org.http4s.circe._
import org.http4s.circe.CirceEntityCodec._
import io.circe.syntax._
import io.circe.parser._

import scala.concurrent.ExecutionContext

class FakeNode(publicApiPort: Int, peerApiPort: Int, peers: List[Info], publicResult: Json = FakeNode.defaultResult)
  extends Codecs {

  private implicit val exc = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(48))
  private implicit val cs = IO.contextShift(exc)
  private implicit val timer = IO.timer(exc)

  private val clusterInfo = peers.asJson

  private val publicService = HttpRoutes.of[IO] {
    case GET -> Root / "consensus" / "info" => Ok(clusterInfo)
    case GET -> Root / "dashboard" => Ok(publicResult)
  }

  private val peerService = HttpRoutes.of[IO] {
    case _ => NotFound()
  }

  private val public =
    BlazeBuilder[IO]
    .bindLocal(publicApiPort)
    .mountService(publicService, "/")
    .serve
    .compile
    .drain

  private val peer =
    BlazeBuilder[IO]
      .bindLocal(peerApiPort)
      .mountService(peerService, "/")
      .serve
      .compile
      .drain

  def run = NonEmptyList.of(public, peer).parSequence.flatMap(_ => IO.unit)
}

object FakeNode {
  val defaultResult: Json = parse(
    """{"edge":{"observationEdge":{"parents":[{"hash":"DAG61Evc8F7ckdBj2xLmgYtiHBy6GoTtJoJmMFti","hashType":"AddressHash"},{"hash":"DAG0k4UQqyRbL5X1qUaHxLfuPuZXi3Rhjf185VhV","hashType":"AddressHash"}],"data":{"hash":"518517284ce888bd34df366cb6d9ef94916ed6a086919cbf49e9fcad7b5a5637","hashType":"TransactionDataHash"}},"signedObservationEdge":{"signatureBatch":{"hash":"916e5558f0634c9867fc5d330e60a2d1286b45d6f1ab39ab303b66ab109109ef","signatures":[{"signature":"3046022100ecabafd67eb9e333ea28d46bd45f49f21b743d5434d392563f17656281940f90022100d60a9ed8053874cd70ba013fc0f979dcfc2e684dc5ed1ec5a4e3399fecc6705f","id":{"hex":"53207735d0d14d4d38232866e773317d3cd61959dcb730eb5070f23b8cecbde3a41bf767603b441fce40b4fb1ada4401393ddabc20aed2d6ced0cec225e06215"}}]}},"data":{"amount":0,"salt":5326002153881019350}},"lastTxRef":{"hash":"","ordinal":0},"isDummy":true}""".stripMargin)
    .getOrElse(throw new RuntimeException("This is an error in test sample. Cannot provide test data for json string"))
}
