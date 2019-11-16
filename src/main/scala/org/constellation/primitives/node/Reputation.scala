package org.constellation.primitives.node

import io.circe._, io.circe.generic.semiauto._

case class Reputation(value: Double) extends AnyVal

object Reputation {

  implicit val reputationDecoder: Decoder[Reputation] = Decoder.decodeDouble.map(Reputation.apply)
}