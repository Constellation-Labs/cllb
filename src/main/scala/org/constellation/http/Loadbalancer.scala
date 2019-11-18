package org.constellation.http

import java.util.concurrent.Executors

import cats.effect.IO
import cats.effect.concurrent.Ref
import fs2.concurrent.Signal
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

class Loadbalancer(terminator: Signal[IO, Boolean], port: Int = 9000, host: String = "localhost") {

  private val upstream: Ref[IO, List[Addr]] = Ref.of[IO, List[Addr]](List.empty[Addr]).unsafeRunSync()

  private implicit val exc = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(24))
  private implicit val cs = IO.contextShift(exc)
  private implicit val timer = IO.timer(scala.concurrent.ExecutionContext.Implicits.global)

  private val http = BlazeClientBuilder[IO](exc).resource

  private val upstreamIterator = Ref.of[IO, Iterator[Addr]](List.empty[Addr].iterator).unsafeRunSync()

  private val proxy = HttpService[IO] {
    case req =>
      upstream.get.flatMap(hosts =>
        upstreamIterator.modify {
          case i if i.hasNext => i -> i.nextOption()
          case _ =>
            val i = hosts.iterator
            i -> i.nextOption()
        }.flatMap {
          case None =>
            ServiceUnavailable()
          case Some(host) =>
            val uri = req.uri.copy(authority = Some(Authority(host = RegName(host.host.getHostAddress), port = Some(host.publicPort))))

            http.use(client => client.fetch[Response[IO]](req.withUri(uri))(resp => IO.pure(resp)))
        })
  }

  def withUpstream(addrs: Set[Addr]) =
    upstreamIterator.set(addrs.iterator).flatMap(_ =>
      upstream.getAndSet(addrs.toList))


  println(s"Prepare loadbalancer on host=$host port=$port")

  val server: IO[Unit] = BlazeBuilder[IO]
    .bindHttp(port, host)
    .mountService(proxy, "/")
    .serve
    .compile
    .drain
}
