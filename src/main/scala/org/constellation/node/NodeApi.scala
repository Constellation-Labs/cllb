package org.constellation.node

import org.constellation.primitives.node.{Addr, Info}
import org.http4s.{Header, Headers, Request, Uri}
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.client.blaze._
import cats.effect.IO
import io.circe.generic.auto._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s._
import org.http4s.syntax.all._
import org.http4s.headers.Authorization

trait NodeApi {
  def getInfo: IO[List[Info]]
}

class RestNodeApi(node: Addr, identity: Option[BasicCredentials])(implicit http: Client[IO]) extends NodeApi {
  val baseUri = Uri.apply(
    Some(Scheme.http),
    Some(Authority(host = RegName(node.host.getCanonicalHostName), port = Some(node.publicPort)))
  )

  private val getInfoRequest = Request[IO](
    uri = baseUri.withPath("/cluster/info"),
    headers = identity.map(creds => Headers.of(Authorization(creds))).getOrElse(Headers.empty)
  )

  def getInfo: IO[List[Info]] =
    http.expect(getInfoRequest)(jsonOf[IO, List[Info]])

}
