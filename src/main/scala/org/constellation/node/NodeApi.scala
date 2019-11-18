package org.constellation.node

import org.constellation.primitives.node.{Addr, Info}
import org.http4s.Uri
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.client.blaze._
import cats.effect.{IO, Resource}
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.client.Client

trait NodeApi {
  def getInfo: IO[List[Info]]
}

class RestNodeApi(node: Addr, http: Resource[IO, Client[IO]]) extends NodeApi {
  // this should be moved as a common space

  val baseUri = Uri.apply(
    Some(Scheme.http),
    Some(Authority(host = RegName(node.host.getCanonicalHostName), port = Some(node.publicPort)))
  )

  def getInfo(): IO[List[Info]] =
    http.use(_.expect(baseUri.withPath("/cluster/info"))(jsonOf[IO, List[Info]]))

}
