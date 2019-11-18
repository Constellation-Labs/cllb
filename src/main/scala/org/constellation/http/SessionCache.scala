package org.constellation.http

import cats.effect.IO
import org.constellation.primitives.node.Addr
import org.http4s.Request
import scalacache.guava.GuavaCache
import scalacache.{CacheConfig, Mode}
import scala.concurrent.duration._
import scala.language.postfixOps

class SessionCache {

  private def clientId(req: Request[IO]) = req.from.map(_.getHostAddress)

  private implicit val mode: Mode[IO] = scalacache.CatsEffect.modes.async

  private val clientUpstreamCache = GuavaCache[Addr](CacheConfig())

   def memoizeUpstream(req: Request[IO], addr: Addr): IO[Unit] =
    clientId(req).map(clientUpstreamCache.doPut(_, addr, Some(1 hour)).flatMap(_ => IO.unit)).getOrElse(IO.unit)

  def resolveUpstream(req: Request[IO], validHosts: List[Addr]): IO[Option[Addr]] = //TODO: HashSet wold be faster
      clientId(req).map(clientUpstreamCache.doGet(_))
        .getOrElse(IO.pure(Option.empty[Addr]))
        .map(_.filter(addr => validHosts.contains(addr)))
}
