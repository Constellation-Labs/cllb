package org.constellation.elb

import cats.data.NonEmptyList
import cats.effect.IO
import cats.effect.concurrent.Ref
import org.constellation.node.RestNodeApi
import org.constellation.primitives.node.{Addr, Info}
import org.http4s.client.blaze.BlazeClientBuilder
import cats._
import cats._
import cats.data._
import cats.syntax.all._
import cats.effect.IO
import org.constellation.http.Loadbalancer
import org.constellation.util.aws.elb.AmazonElasticLoadBalancingScalaClient

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.global

class Manager(init: NonEmptyList[Addr]) {

  private implicit val o = new Order[Addr] {
    override def compare(x: Addr, y: Addr): Int =
      x.host.toString.compareTo(y.host.toString) match {
        case 0 =>x.port.compareTo(y.port)
        case o => o
      }
  }

  private implicit val cs = IO.contextShift(global)
  private implicit val timer = IO.timer(scala.concurrent.ExecutionContext.Implicits.global)
  private val http = BlazeClientBuilder[IO](global).resource

  private lazy val hostsRef =
    Ref.of[IO, NonEmptyMap[Addr, Option[List[Info]]]](init.map(addr => addr -> None).toNem).unsafeRunSync()

  def node(addr: Addr) = new RestNodeApi(addr, http)

  lazy val lb = new Loadbalancer()

  def updateLbSetup(hosts: Set[Addr]): IO[Unit] =
    IO(println(s"Update lb setup with hosts=$hosts")).flatMap( _ => lb.withUpstream(hosts))
      .flatMap(_ => IO.unit)

  def run(): IO[Unit] =
    hostsRef.get.flatMap { hosts =>
      println(s"Current hosts setup is=$hosts")
      clusterStatus(hosts.keys)
        .flatMap { status =>
          val activeHosts = discoverActiveHosts(status)

          val clusterHosts = activeHosts.filterNot(addr => status.contains(addr)).foldLeft(status)((acc, addr) =>
            acc.add(addr, Option.empty[List[Info]])
          )

          println(activeHosts)

          hostsRef.set(clusterHosts).flatTap(_ => updateLbSetup(activeHosts))
        }
  }.flatMap(_ => IO.sleep(1 minute).flatMap(_ => run()))

  def discoverActiveHosts(init: NonEmptyMap[Addr, Option[List[Info]]]): Set[Addr] = {

    val tresholdLevel = Math.floor(init.keys.size / 2)

    init
      .toList
      .collect {
      case Some(el) => el
      }.flatten.groupBy(_.ip).collect {
        case (addr, proof) if proof.length > tresholdLevel => addr
      }.toSet
  }

  def clusterStatus(hosts: NonEmptySet[Addr]):IO[NonEmptyMap[Addr, Option[List[Info]]]] = {
    println(s"Build cluster status on hosts=$hosts")
    hosts.toNonEmptyList
      .map(addr =>
        node(addr).getInfo().map(addr -> Option(_))
          .recover{ case _ => addr -> Option.empty[List[Info]]} )
      .parSequence.map(_.toNem)
  }
}
