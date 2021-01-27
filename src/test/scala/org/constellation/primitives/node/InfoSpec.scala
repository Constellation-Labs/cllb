package org.constellation.primitives.node

import org.scalatest.{FunSpec, Matchers}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._, io.circe.syntax._

class InfoSpec extends FunSpec with Matchers {

  describe("valid json object string") {

    val validJson =
      """{"id":{"hex":"4256bee35df47fd49885d0a5931178e9d0cf33f314b74680646cd391a96ba25fc33cc7c3e1274ffc29a64828d5feeb2f99242e0dffb13c006250d35b3f810551"},"ip":{"host":"35.236.125.81","port":9001},"status":"PendingDownload","reputation":0,"alias":"someAlias"}"""

    it("parses as an object") {
      decode[Info](validJson) shouldBe a[Right[_, _]]
    }
  }

  describe("valid json array string") {

    val validJson =
      """[{"id":{"hex":"4256bee35df47fd49885d0a5931178e9d0cf33f314b74680646cd391a96ba25fc33cc7c3e1274ffc29a64828d5feeb2f99242e0dffb13c006250d35b3f810551"},"ip":{"host":"35.236.125.81","port":9001},"status":"PendingDownload","reputation":0,"alias":"someAlias"}]"""

    it("parses as list of objects") {
      decode[List[Info]](validJson) shouldBe a[Right[_, _]]
    }
  }
}
