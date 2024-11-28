package org.constellation.lb

import cats.data.{NonEmptyList, NonEmptyMap, NonEmptySet}
import cats.implicits.catsKernelStdOrderForInt
import org.constellation.lb.NemOListOps._
import org.constellation.primitives.node.{Addr, Id, Info, NodeState}
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalatest.{FunSpec, Matchers}

import java.net.InetAddress
import scala.collection.immutable.{SortedMap, SortedSet}

class NemOListOpsSpec extends FunSpec with Matchers {

  describe("extractNewKeys") {
    it("extract new keys") {
      extractNewKeys(NonEmptyMap.of(1 -> Some(NonEmptyList.of(1, 3, 4)), 2 -> Some(NonEmptyList.of(1, 3, 5))))(identity) shouldBe SortedSet(
        3,
        4,
        5
      )
    }
    it("no new keys") {
      extractNewKeys(NonEmptyMap.of(1 -> Some(NonEmptyList.of(1, 3)), 2 -> Some(NonEmptyList.of(2, 1)), 3 -> None))(identity) shouldBe empty
    }
  }

  describe("filterKeys") {

    it("filter keys from a map") {
      filterKeys(NonEmptySet.of(2), NonEmptyMap.of(1 -> "a", 2 -> "b", 3 -> "c")) shouldBe Map(2 -> "b")
    }

  }

  describe("rotateMap") {

    it("rotate of map of none") {
      rotateMap[Int, Int](NonEmptyMap.of(1 -> None))(identity) shouldBe empty
    }

    it("rotate of unit map") {
      rotateMap(NonEmptyMap.of(1 -> Some(NonEmptyList.of(2 -> "2"))))(_._1) shouldBe SortedMap(2 -> Some(NonEmptyList.of(2 -> "2")))
    }

    it("rotate an assorted map") {
      rotateMap(
        NonEmptyMap.of(
          1 -> Some(NonEmptyList.of(1 -> "1", 2 -> "2", 3 -> "3")),
          2 -> Some(NonEmptyList.of(1 -> "1", 2 -> "2", 3 -> "3")),
          3 -> None,
          4 -> Some(NonEmptyList.of(1 -> "1", 2 -> "2", 3 -> "3"))
        )
      )(_._1) shouldBe
        SortedMap(
          1 -> Some(NonEmptyList.of(1 -> "1", 1 -> "1", 1 -> "1")),
          2 -> Some(NonEmptyList.of(2 -> "2", 2 -> "2", 2 -> "2")),
          3 -> Some(NonEmptyList.of(3 -> "3", 3 -> "3", 3 -> "3"))
        )
    }

  }

  describe("findFirstElement") {
    it("find a session from a nodes map") {
      val nodes = Map(
        Addr(ipv4"127.0.0.1".toInet4Address, 1000) -> Some(
          NonEmptyList.of(Info(Id("node-1"), InetAddress.getLoopbackAddress, 9997, 9998, 111L, 123L, NodeState.Ready))
        ),
        Addr(ipv4"127.0.0.30".toInet4Address, 444) -> Some(
          NonEmptyList.of(Info(Id("node-2"), InetAddress.getLoopbackAddress, 9999, 10000, 111L, 456L, NodeState.Ready))
        ),
        Addr(ipv4"127.0.0.5".toInet4Address, 88) -> Some(
          NonEmptyList.of(Info(Id("node-2"), InetAddress.getLoopbackAddress, 9999, 10000, 111L, 550L, NodeState.Ready))
        )
      )
      findFirstElement(nodes) shouldBe Some(
        Info(Id("node-1"), InetAddress.getLoopbackAddress, 9997, 9998, 111L, 123L, NodeState.Ready)
      )
    }

    it("find a session from a nodes map with empty values") {
      val nodes = Map(
        Addr(ipv4"127.0.0.30".toInet4Address, 444) -> None,
        Addr(ipv4"127.0.0.5".toInet4Address, 88)   -> None,
        Addr(ipv4"127.0.0.1".toInet4Address, 1000) -> Some(
          NonEmptyList.of(Info(Id("node-1"), InetAddress.getLoopbackAddress, 9997, 9998, 111L, 123L, NodeState.Ready))
        )
      )
      findFirstElement(nodes) shouldBe Some(
        Info(Id("node-1"), InetAddress.getLoopbackAddress, 9997, 9998, 111L, 123L, NodeState.Ready)
      )
    }

    it("return None if there's no session") {
      val nodes = Map(
        Addr(ipv4"127.0.0.30".toInet4Address, 444) -> None,
        Addr(ipv4"127.0.0.5".toInet4Address, 88)   -> None
      )
      findFirstElement(nodes) shouldBe None
    }

  }

  describe("splitByElementProp") {

    val seedHosts = Map(
      Addr(ipv4"127.0.0.1".toInet4Address, 1000) ->
        Some(NonEmptyList.of(Info(Id("node-1"), InetAddress.getLoopbackAddress, 9997, 9998, 111L, 123L, NodeState.Ready))),
      Addr(ipv4"127.0.0.30".toInet4Address, 444) ->
        Some(NonEmptyList.of(Info(Id("node-2"), InetAddress.getLoopbackAddress, 9999, 10000, 111L, 456L, NodeState.Ready)))
    )

    val sameSessionHosts = Map(
      Addr(ipv4"127.0.0.4".toInet4Address, 888) ->
        Some(NonEmptyList.of(Info(Id("node-2"), InetAddress.getLoopbackAddress, 9999, 10000, 111L, 500L, NodeState.Ready))),
      Addr(ipv4"127.0.0.5".toInet4Address, 88) ->
        Some(NonEmptyList.of(Info(Id("node-2"), InetAddress.getLoopbackAddress, 9999, 10000, 111L, 550L, NodeState.Ready)))
    )

    val otherSessionHosts = Map(
      Addr(ipv4"127.0.0.14".toInet4Address, 888) ->
        Some(NonEmptyList.of(Info(Id("node-2"), InetAddress.getLoopbackAddress, 9999, 10000, 222L, 500L, NodeState.Ready))),
      Addr(ipv4"127.0.0.15".toInet4Address, 88) ->
        Some(NonEmptyList.of(Info(Id("node-2"), InetAddress.getLoopbackAddress, 9999, 10000, 222L, 550L, NodeState.Ready)))
    )

    it("node map is unchanged for the same clusterSession") {

      val sessionHosts     = seedHosts ++ sameSessionHosts
      val (valid, invalid) = splitByElementProp(sessionHosts)(_.clusterSession === 111L)

      valid shouldBe sessionHosts
      invalid shouldBe empty
    }

    it("remove nodes with different clusterSession") {

      val (valid, invalid) =
        splitByElementProp(seedHosts ++ sameSessionHosts ++ otherSessionHosts)(_.clusterSession === 111L)
      valid shouldBe seedHosts ++ sameSessionHosts
      invalid shouldBe otherSessionHosts

    }

    it("If there's no cluster session, maintain") {

      val (valid, invalid) =
        splitByElementProp(seedHosts ++ sameSessionHosts ++ otherSessionHosts)(_.clusterSession === 999L)
      valid shouldBe empty
      invalid shouldBe seedHosts ++ sameSessionHosts ++ otherSessionHosts

    }

  }

}
