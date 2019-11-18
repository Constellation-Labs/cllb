package org.constellation.primitives.node

import java.net.InetAddress

import cats.Order
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto.deriveDecoder
import io.circe.Decoder

case class Addr(host: InetAddress, port: Int) {
  @transient val publicPort:Int = port - 1
}

object Addr extends Codecs {

  implicit val addrDecoder: Decoder[Addr] = deriveDecoder[Addr]

  implicit val o = new Order[Addr] {
    override def compare(x: Addr, y: Addr): Int =
      x.host.toString.compareTo(y.host.toString) match {
        case 0 => x.port.compareTo(y.port)
        case o => o
      }
  }
}
