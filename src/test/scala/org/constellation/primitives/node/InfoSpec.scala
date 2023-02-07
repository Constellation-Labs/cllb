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
      """{"id":"8804651be951ea09a660124499e4f7878c3e0faf52c282b360156d39d6a2cefe916b35ba8452c8a9beed02ea25f83118270d4637449ac0905b1e333436f18b45","ip":"147.182.209.217","publicPort":9000,"p2pPort":9001,"session":543234,"state":"Ready"}"""

    it("parses as an object") {
      decode[Info](validJson) shouldBe a[Right[_, _]]
    }
  }

  describe("valid json array string") {

    val validJson =
      """[{"id":"46daea11ca239cb8c0c8cdeb27db9dbe9c03744908a8a389a60d14df2ddde409260a93334d74957331eec1af323f458b12b3a6c3b8e05885608aae7e3a77eac7","ip":"54.177.146.221","publicPort":9000,"p2pPort":9001,"session":123456,"state":"Ready"},{"id":"25366a0db122b098fd551a7b2104970748dcdded84d06eba85dda558f01ecf5c5133d0d6554a30735cdd7eddd49b5942a29d2e93295d26cbf3deb8a1f73f5581","ip":"54.148.112.254","publicPort":9000,"p2pPort":9001,"session":123456,"state":"WaitingForReady"},{"id":"56ab39214bb79e9e60d1f9bd54bafa086e594a93830438631330e7f5144408611b0d21cc9e0c7039ac93f7a2dd1b70bcdee18b1eafa3756b90085b1208369cd5","ip":"143.198.104.221","publicPort":9000,"p2pPort":9001,"session":1231231231,"state":"WaitingForObserving"},{"id":"bb95a0c6f17b9a0cf8d6205a14735a6f5e6272a2f4385f8f57859ab76935b3dfd437bab917c60e9fc2e003ddf757675d0996407b4ea61b97058979cb8cff8c58","ip":"91.67.190.27","publicPort":9000,"p2pPort":9001,"session":12939123,"state":"Observing"}]"""

    it("parses as list of objects") {
      decode[List[Info]](validJson) shouldBe a[Right[_, _]]
    }
  }
}
