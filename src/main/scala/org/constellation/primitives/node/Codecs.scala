package org.constellation.primitives.node

import java.net.{InetAddress, UnknownHostException}

import io.circe.Decoder

import scala.util.control.Exception.catching

trait Codecs {

  implicit val inetDecoder: Decoder[InetAddress] = Decoder.decodeString.emapTry(s =>
    catching(classOf[UnknownHostException]).withTry(InetAddress.getByName(s)))

  implicit val stateDecoder: Decoder[State.State] = Decoder.decodeString.emapTry(s =>
    catching(classOf[NoSuchElementException]).withTry(State.withName(s)))

}
