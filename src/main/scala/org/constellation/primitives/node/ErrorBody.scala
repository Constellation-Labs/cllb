package org.constellation.primitives.node

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ErrorBody(reason: String)

object ErrorBody extends Codecs {
  implicit val errorBodyCodec: Codec[ErrorBody] = deriveCodec
}
