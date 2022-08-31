package org.constellation.primitives.node

import io.circe.Decoder
import io.circe.Encoder

case class Id(hex: String) {

  @transient
  lazy val short: String = hex.toString.slice(0, 5)

  @transient
  lazy val medium: String = hex.toString.slice(0, 10)

  override def toString = s"#$short"
}

object Id {
  implicit val decoder: Decoder[Id] = Decoder.decodeString.map(Id(_))
  implicit val encoder: Encoder[Id] = Encoder.encodeString.contramap(_.hex)
}
