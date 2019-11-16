package org.constellation.http

import java.util.concurrent.Executors

import cats.effect.IO
import cats.effect.concurrent.Ref
import org.constellation.primitives.node.Addr
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.HttpService
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.Uri.{Authority, RegName}

class Loadbalancer(port: Int = 9000, host: String = "localhost")  {

  private val upstream: Ref[IO, Set[Addr]] = Ref.unsafe(Set.empty[Addr])

  private implicit val exc = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(24))
  private implicit val cs = IO.contextShift(exc)
  private implicit val timer = IO.timer(scala.concurrent.ExecutionContext.Implicits.global)

  private val http = BlazeClientBuilder[IO](exc).resource

  private val proxy = HttpService[IO] {
    case req =>
      upstream.get.flatMap{
        case hosts if hosts.isEmpty =>
          ServiceUnavailable()
        case hosts =>

          val uri = req.uri.copy(authority = Some(Authority(host = RegName(""), port = Some(9000))))
          http.use(client => client.fetch[Response[IO]](req.withUri(uri))(resp => IO.pure(resp)))
      }
  }

  def withUpstream(addrs: Set[Addr]) = upstream.getAndSet(addrs)

  def run() {
    BlazeBuilder[IO]
      .bindHttp(port, host)
      .mountService(proxy, "/")
      .serve
  }
}
