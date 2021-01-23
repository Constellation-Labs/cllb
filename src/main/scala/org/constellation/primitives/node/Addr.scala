package org.constellation.primitives.node

import java.net.{InetAddress, UnknownHostException}
import cats.Order
import io.circe.generic.semiauto.deriveDecoder
import io.circe.Decoder
import pureconfig.ConfigReader

import scala.util.control.Exception.catching

case class Addr(host: InetAddress, port: Int) {
  @transient val publicPort: Int = port - 1

  override def toString = s"${host.getHostAddress}:${port}"
}

trait AddrOrdering {
  implicit val order: Order[Addr] = new Order[Addr] {
    override def compare(x: Addr, y: Addr): Int =
      x.host.toString.compareTo(y.host.toString) match {
        case 0 => x.port.compareTo(y.port)
        case o => o
      }
  }
  implicit val ordering: Ordering[Addr] = order.toOrdering
}

object Addr extends Codecs with AddrOrdering {

  implicit val addrDecoder: Decoder[Addr] = deriveDecoder[Addr]

  implicit val inetAddressReader: ConfigReader[InetAddress] = ConfigReader[String].map(InetAddress.getByName)

  def unapply(in: String): Option[Addr] = {
    val addr = in.takeWhile(_ != ':')

    Option(in.drop(addr.length + 1))
      .filter(_.nonEmpty)
      .map(_.toIntOption)
      .getOrElse(Option(9001))
      .flatMap(
        port =>
          catching(classOf[UnknownHostException])
            .opt(InetAddress.getByName(addr))
            .map(apply(_, port))
      )
  }
}
