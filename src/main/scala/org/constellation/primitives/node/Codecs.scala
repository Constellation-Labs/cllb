package org.constellation.primitives.node

import java.net.{InetAddress, UnknownHostException}

import io.circe.{Codec, Decoder, Encoder}

import scala.util.control.Exception.catching

trait Codecs {

  implicit val inetCodec: Codec[InetAddress] = Codec.from(
    Decoder.decodeString.emapTry(s =>
      catching(classOf[UnknownHostException]).withTry(InetAddress.getByName(s))),
    Encoder.encodeString.contramap[InetAddress](addr => addr.getCanonicalHostName)
  )

  implicit val stateCodec: Codec[NodeState.State] = Codec.from(
    Decoder.decodeString.emapTry(s =>
      catching(classOf[NoSuchElementException]).withTry(NodeState.withName(s))),
    Encoder.encodeString.contramap[NodeState.State](state => state.toString)
  )

}
