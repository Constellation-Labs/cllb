package org.constellation.http

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import org.http4s.circe.CirceEntityEncoder._

import cats.data.{Kleisli, OptionT}
import cats.effect.concurrent.Ref
import org.http4s.headers.`Retry-After`
import cats.effect.IO
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.constellation.primitives.node.{Addr, ErrorBody}
import org.http4s.Uri.{Authority, RegName}
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

class Loadbalancer(port: Int = 9000, host: String = "localhost", retryAfterMinutes: Int) {
  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val upstream: Ref[IO, List[Addr]] = Ref.unsafe[IO, List[Addr]](List.empty[Addr])

  private implicit val exc   = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(24))
  private implicit val cs    = IO.contextShift(exc)
  private implicit val timer = IO.timer(exc)

  private val http = BlazeClientBuilder[IO](exc).resource.map(_.toHttpApp)

  private val upstreamIterator = Ref.unsafe[IO, Iterator[Addr]](List.empty[Addr].iterator)

  private val redirectToMaintenance: Ref[IO, Boolean] = Ref.unsafe(false)

  private val sessions = new SessionCache()

  def shouldRedirectToMaintenance: IO[Boolean] =
    redirectToMaintenance.get

  def enableRedirectingToMaintenance: IO[Unit] =
    redirectToMaintenance.modify(_ => (true, ()))

  def disableRedirectingToMaintenance: IO[Unit] =
    redirectToMaintenance.modify(_ => (false, ()))

  def reset: IO[Unit] =
    for {
      _ <- upstream.modify(_ => (List.empty[Addr], ()))
      _ <- upstreamIterator.modify(_ => (List.empty[Addr].iterator, ()))
      _ <- sessions.clear()
      _ <- logger.info("Performed load balancer reset. Cleared sessions and upstream.")
    } yield ()

  private def resolveUpstream(req: Request[IO]): IO[Option[Addr]] =
    upstream.get.flatMap(
      hosts =>
        sessions
          .resolveUpstream(req, hosts)
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
          }
    )

  private val util = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      upstream.get
        .map(_.size)
        .flatTap(size => logger.info(s"Health check: my upstream size is $size"))
        .flatMap { size =>
          size match {
            case 0 => InternalServerError()
            case _ => Ok(s"$size")
          }
        }
    case GET -> Root / "health" / addrStr =>
      Addr
        .unapply(addrStr)
        .map { addr =>
          upstream.get.flatMap(
            _.find(_ == addr)
              .map(_ => NoContent())
              .getOrElse(NotFound())
          )
        }
        .getOrElse(BadRequest())
  }

  private def maintenance(routes: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { req: Request[IO] =>
    for {
      shouldReturnServiceUnavailable <- OptionT.liftF(shouldRedirectToMaintenance)
      res <- if (shouldReturnServiceUnavailable) {
        val retryAfterTime = HttpDate.unsafeFromInstant(Instant.now.plus(retryAfterMinutes, ChronoUnit.MINUTES))
        val maintenanceRes = Response[IO]()
          .withStatus(Status.ServiceUnavailable)
          .withHeaders(Headers.of(`Retry-After`(retryAfterTime)))
          .withEntity(ErrorBody("Load balancer is currently under a maintenance mode due to cluster upgrade."))

        OptionT.pure[IO](maintenanceRes)
      } else {
        routes(req)
      }
    } yield res
  }

  private def proxy(http: HttpApp[IO]) =
    HttpRoutes.of[IO](
      req =>
        resolveUpstream(req)
          .flatTap(_.map(sessions.memoizeUpstream(req, _)).getOrElse(IO.unit))
          .flatMap {
            case None =>
              ServiceUnavailable()
                .flatTap(
                  _ => logger.error(s"No upstream host available. Cannot handle request ${req.method} ${req.uri}")
                )
            case Some(upstreamHost) =>
              val uri = req.uri.copy(
                authority = Some(
                  req.uri.authority
                    .getOrElse(Authority())
                    .copy(host = RegName(upstreamHost.host.getHostAddress), port = Some(upstreamHost.publicPort))
                )
              )

              http
                .run(req.withUri(uri))
                .flatTap(_ => logger.info(s"Upstream host=${upstreamHost} handled request ${req.method} ${req.uri}"))
          }
    )

  def withUpstream(addrs: Set[Addr]): IO[List[Addr]] =
    upstreamIterator
      .set(addrs.iterator)
      .flatMap(_ => upstream.getAndSet(addrs.toList))
      .flatTap {
        case _ if addrs.isEmpty => logger.warn("No available upstream to handle incoming requests")
        case _                  => IO.unit
      }

  val server: IO[Unit] =
    logger
      .info(s"Setup Loadbalancer instance on $host:$port")
      .flatMap(
        _ =>
          http.use(
            client =>
              BlazeBuilder[IO]
                .bindHttp(port, host)
                .mountService(util, "/utils")
                .mountService(maintenance(proxy(client)), "/")
                .serve
                .compile
                .drain
          )
      )
}
