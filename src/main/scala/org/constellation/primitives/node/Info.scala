package org.constellation.primitives.node

import java.net.{InetAddress, UnknownHostException}

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.JsonCodec
import io.circe._
import io.circe.generic.semiauto._
import io.circe._, io.circe.generic.semiauto._

import scala.util.control.Exception.catching

case class Info(
    id: Id,
    ip: Addr,
    status: NodeState.State,
    reputation: Reputation,
    alias: String
)

object Info extends Codecs {

  implicit val infoCodec: Codec[Info] = deriveCodec[Info]
}
