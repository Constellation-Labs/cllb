package org.constellation.lb

import cats.data.NonEmptyList
import cats.effect.{ContextShift, ExitCode, IO, Timer}
import org.constellation.node.RestNodeApi
import org.constellation.primitives.node.{Addr, Info, NodeState}
import org.http4s.client.blaze.BlazeClientBuilder
import cats._
import cats.data._
import cats.syntax.all._
import cats.effect.concurrent.Ref
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.constellation.http.Loadbalancer
import org.constellation.LoadbalancerConfig
import org.http4s.client.Client

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.global

class Manager(init: NonEmptyList[Addr], config: LoadbalancerConfig)(
    implicit val C: ContextShift[IO],
    val t: Timer[IO]
) {
  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val http = BlazeClientBuilder[IO](global)
    .withRequestTimeout(5 second)
    .withConnectTimeout(5 second)
    .withMaxTotalConnections(128)
    .resource

  private lazy val hostsRef =
    Ref.unsafe[IO, NonEmptyMap[Addr, Option[List[Info]]]](init.map(addr => addr -> None).toNem)

  def node(addr: Addr)(implicit http: Client[IO]) = new RestNodeApi(addr, config.credentials)

  private val lb = new Loadbalancer(config.port, config.`if`)

  def updateLbSetup(hosts: Set[Addr]): IO[Unit] =
    IO(logger.info(s"Update lb setup with hosts=$hosts"))
      .flatMap(_ => lb.withUpstream(hosts))
      .flatMap(_ => IO.unit)

  private def updateProcedure(implicit client: Client[IO]) =
    hostsRef.get
      .flatTap(hosts => logger.info(s"Init cluster discovery from ${hosts.keys}"))
      .flatMap(
        hosts =>
          clusterStatus(hosts.keys)(client)
            .flatMap(
              status =>
                discoverCluster(status).flatMap {
                  case (activeHosts, inactiveHosts) =>
                  val clusterHosts = (activeHosts ++ inactiveHosts) // TODO: Add Eviction of inactive hosts
                    .filterNot(addr => status.contains(addr))
                    .foldLeft(status)((acc, addr) => acc.add(addr, Option.empty[List[Info]]))

                  hostsRef.set(clusterHosts).flatTap(_ => updateLbSetup(activeHosts))
                }
            )
      )

  private def manager(implicit client: Client[IO]): IO[Unit] =
    updateProcedure
      .flatTap(_ => logger.info("Scheduling next cluster status update round"))
      .flatMap(_ => IO.sleep(60 seconds))
      .flatMap(_ => manager)

  def run: IO[ExitCode] =
    http.use { client =>
      NonEmptyList.of(lb.server, manager(client)).parSequence.map(_ => ExitCode.Success)
    }

  def discoverCluster(init: NonEmptyMap[Addr, Option[List[Info]]]): IO[(Set[Addr], Set[Addr])] = {
    val tresholdLevel = init.keys.size / 2

    def isActive(addr: Addr, proof: List[Info]) =
      init(addr).nonEmpty && proof.count(_.status == NodeState.Ready) > tresholdLevel



      IO {




      val (active, other) = init.toNel
         .collect {
           case (_, Some(el)) => el
         }
         .flatten
         .groupBy(_.ip)
         .toList
         .partition {
          case (addr: Addr, proof: List[Info]) => isActive(addr, proof)
        }

      active.map(_._1).toSet -> other.map(_._1).toSet
    }.flatTap{
      case (active, other)  => logger.info(s"Discovered ${active} hosts and ${other} not ready")
    }
  }

  def clusterStatus(
      hosts: NonEmptySet[Addr]
  )(implicit client: Client[IO]): IO[NonEmptyMap[Addr, Option[List[Info]]]] = {
    IO.apply(logger.info(s"Fetch cluster status from following ${hosts.size} hosts: ${hosts.toList.take(5)}"))
      .flatMap(
        _ =>
          hosts.toNonEmptyList
            .map(
              addr =>
                node(addr).getInfo.flatMap(
                  result =>
                    logger
                      .debug(s"Node $addr returned $result")
                      .map(_ => addr -> Option(result))
                  )
                  .recoverWith {
                    case error =>
                      logger
                        .info(s"Cannot retrieve cluster status from addr=$addr error=$error")
                        .map(_ => addr -> Option.empty[List[Info]])
                  }
            )
            .parSequence
            .map(_.toNem)
      )
  }
}
