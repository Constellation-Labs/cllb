package org.constellation.primitives.node

import java.net.InetAddress

import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.Decoder

case class Addr(host: InetAddress, port: Int)

object Addr extends Codecs {

  implicit val addrDecoder: Decoder[Addr] = deriveDecoder[Addr]
}
