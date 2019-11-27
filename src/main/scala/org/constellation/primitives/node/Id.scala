package org.constellation.primitives.node

case class Id(hex: String) {

  @transient
  lazy val short: String = hex.toString.slice(0, 5)

  @transient
  lazy val medium: String = hex.toString.slice(0, 10)

  override def toString = s"#$short"
}
