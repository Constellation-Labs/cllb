package org.constellation.lb

import cats.data.NonEmptyList
import cats.effect.{ContextShift, ExitCode, IO, Timer}
import org.constellation.node.RestNodeApi
import org.constellation.primitives.node.{Addr, Info}
import org.http4s.client.blaze.BlazeClientBuilder
import cats._
import cats.data._
import cats.syntax.all._
import cats.effect.concurrent.Ref
import fs2.concurrent.SignallingRef
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.constellation.http.Loadbalancer

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.global

class Manager(init: NonEmptyList[Addr])(implicit val C: ContextShift[IO], val t: Timer[IO]) {
  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val http = BlazeClientBuilder[IO](global)
    .withRequestTimeout(1 second)
    .withConnectTimeout(1 second)
    .withMaxTotalConnections(128)
    .resource

  private lazy val hostsRef = Ref.of[IO, NonEmptyMap[Addr, Option[List[Info]]]](init.map(addr => addr -> None).toNem).unsafeRunSync()

  def node(addr: Addr) = new RestNodeApi(addr, http)

  private val lbTerminator = SignallingRef.apply[IO, Boolean](false).unsafeRunSync()

  private val lb = new Loadbalancer(lbTerminator)

  def updateLbSetup(hosts: Set[Addr]): IO[Unit] =
    IO(logger.info(s"Update lb setup with hosts=$hosts")).flatMap( _ => lb.withUpstream(hosts))
      .flatMap(_ => IO.unit)

  val updateProcedure =
    hostsRef.get
      .flatTap(hosts => logger.info(s"Init cluster discovery from ${hosts.keys}"))
      .flatMap(hosts =>
        clusterStatus(hosts.keys)
          .flatMap ( status =>
            discoverActiveHosts(status).flatMap{ activeHosts =>
              val clusterHosts = activeHosts.filterNot(addr => status.contains(addr)).foldLeft(status)((acc, addr) =>
                acc.add(addr, Option.empty[List[Info]])
              )

              hostsRef.set(clusterHosts).flatTap(_ => updateLbSetup(activeHosts))
            }
          ))

  val manager : IO[Unit] =
    updateProcedure
      .flatTap(_ => logger.info("Scheduling next update round"))
      .flatMap(_ => IO.sleep(10 seconds))
      .flatTap(_ => logger.info("Next iteration"))
      .flatMap(_ => manager)

  def run(): IO[ExitCode] = manager.map(_ => ExitCode.Success)
  // lb.server.map(_ => ExitCode.Success)
    // NonEmptyList.of(lb.server, manager).parSequence.map(_ => ExitCode.Success)

  def discoverActiveHosts(init: NonEmptyMap[Addr, Option[List[Info]]]): IO[Set[Addr]] = IO {

    val tresholdLevel = Math.floor(init.keys.size / 2)

    val s : Set[Addr] = init
      .toList
      .collect {
        case Some(el) => el
      }.flatten.groupBy(_.ip).collect {
      case (addr, proof) if proof.length > tresholdLevel => addr
    }.toSet

    s
  }.flatTap(hosts => logger.info(s"Active hosts $hosts"))

  def clusterStatus(hosts: NonEmptySet[Addr]):IO[NonEmptyMap[Addr, Option[List[Info]]]] = {
    IO.apply(logger.info(s"Fetch cluster status on hosts=$hosts")).flatMap( _ =>
      hosts.toNonEmptyList
        .map(addr =>
          node(addr).getInfo().map(addr -> Option(_))
            .recover{ case _ => addr -> Option.empty[List[Info]]} )
        .parSequence.map(_.toNem))
  }
}
