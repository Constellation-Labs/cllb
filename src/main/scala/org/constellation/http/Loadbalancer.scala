package org.constellation.http

import java.util.concurrent.Executors

import cats.effect.concurrent.Ref
import org.constellation.primitives.node.Addr
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.HttpService
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.Uri.{Authority, RegName}
import cats.syntax.all._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.Logger
import cats.effect.IO
import org.http4s.client.Client

import scala.language.postfixOps

class Loadbalancer(port: Int = 9000, host: String = "localhost") {
  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val upstream: Ref[IO, List[Addr]] = Ref.unsafe[IO, List[Addr]](List.empty[Addr])

  private implicit val exc = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(24))
  private implicit val cs = IO.contextShift(exc)
  private implicit val timer = IO.timer(exc)

  private val http = BlazeClientBuilder[IO](exc).resource

  private val upstreamIterator = Ref.unsafe[IO, Iterator[Addr]](List.empty[Addr].iterator)

  private val sessions = new SessionCache()

  private def resolveUpstream(req: Request[IO]): IO[Option[Addr]] =
    upstream.get.flatMap(hosts =>
      sessions.resolveUpstream(req, hosts)
          .flatMap {
            case addr: Some[_] =>
              IO.pure(addr).flatTap(addr => logger.info(s"Reusing previous upstream for client request addr=$addr"))
            case _ =>
              upstreamIterator.modify {
                case i if i.hasNext => i -> i.nextOption()
                case _ =>
                  val i = hosts.iterator
                  i -> i.nextOption()
              }
          })

  private val util = HttpRoutes.of[IO] {
    case GET -> Root / "health" => upstream.get.flatMap {
      case Nil => ServiceUnavailable()
      case _ => Ok()
    }
  }

  private def proxy(http: Client[IO]) = HttpService[IO] ( req =>
      resolveUpstream(req)
        .flatTap(_.map(sessions.memoizeUpstream(req, _)).getOrElse(IO.unit))
        .flatMap {
          case None =>
            ServiceUnavailable()
              .flatTap(_ => logger.error(s"No upstream host available. Cannot handle request ${req.method} ${req.uri}"))
          case Some(upstreamHost) =>
            val uri = req.uri.copy(authority = Some(
              req.uri.authority.getOrElse(Authority()).copy(
                  host = RegName(upstreamHost.host.getHostAddress),
                  port = Some(upstreamHost.publicPort))))

            http.fetch[Response[IO]](req.withUri(uri))(resp => IO.pure(resp))
              .flatTap(_ => logger.info(s"Upstream host=${upstreamHost} handled request ${req.method} ${req.uri}"))
        })

  def withUpstream(addrs: Set[Addr]): IO[List[Addr]] =
    upstreamIterator.set(addrs.iterator).flatMap(_ =>
      upstream.getAndSet(addrs.toList))
    .flatTap{
      case _ if addrs.isEmpty => logger.warn("No available upstream to handle incoming requests")
      case _ => IO.unit
    }

  val server: IO[Unit] =
    logger.info(s"Setup Loadbalancer instance on $host:$port").flatMap(_ =>
      http.use(client =>
        BlazeBuilder[IO]
          .bindHttp(port, host)
          .mountService(util, "utils")
          .mountService(proxy(client), "/")
          .serve
          .compile
          .drain))
}
