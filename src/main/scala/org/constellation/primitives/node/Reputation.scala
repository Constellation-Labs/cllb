package org.constellation.primitives.node

import io.circe._

case class Reputation(value: Double) extends AnyVal

object Reputation {

  implicit val reputationCodec: Codec[Reputation] = Codec.from(
    Decoder.decodeDouble.map(Reputation.apply),
    Encoder.encodeDouble.contramap[Reputation](_.value)
  )
}
