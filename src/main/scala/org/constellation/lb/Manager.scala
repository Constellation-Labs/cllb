package org.constellation.lb

import cats.Applicative
import cats.data._
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, ExitCode, IO, Timer}
import cats.instances.long._
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.constellation.LoadbalancerConfig
import org.constellation.http.Loadbalancer
import org.constellation.node.RestNodeApi
import org.constellation.primitives.node.{Addr, AddrOrdering, Info, NodeState}
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.io._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import NemOListOps._

import scala.collection.immutable.SortedMap
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.language.postfixOps

class Manager(init: NonEmptyList[Addr], config: LoadbalancerConfig)(
    implicit val C: ContextShift[IO],
    val t: Timer[IO]
) extends AddrOrdering {

  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  private val underMaintenance: Ref[IO, Boolean] = Ref.unsafe(false)

  private val http = BlazeClientBuilder[IO](global)
    .withRequestTimeout(5 second)
    .withConnectTimeout(5 second)
    .withMaxTotalConnections(128)
    .resource

  private lazy val hostsRef =
    Ref.unsafe[IO, NonEmptyMap[Addr, Option[NonEmptyList[Info]]]](init.map(addr => addr -> None).toNem)

  private lazy val sessionRef = Ref.unsafe[IO, Option[Long]](None)

  def node(addr: Addr)(implicit http: Client[IO]) = new RestNodeApi(addr, config.networkCredentials)

  private val lb = new Loadbalancer(config.port, config.`if`, config.retryAfterMinutes)

  def reset: IO[Unit] =
    logger.info("reset") >>
      hostsRef
        .modify(_ => (init.map(addr => addr -> None).toNem, ()))
        .flatMap(_ => sessionRef.set(None))
        .flatTap(_ => logger.info(s"Hosts list and cluster session has been reverted to initial state."))

  def updateLbSetup(hosts: Set[Addr]): IO[Unit] =
    IO(logger.info(s"Update lb setup with hosts=$hosts"))
      .flatMap(_ => lb.withUpstream(hosts))
      .flatMap(_ => IO.unit)

  private def toAddress(n: Info) = Addr(n.ip, n.publicPort)

  private def updateProcedure(implicit client: Client[IO]) = {
    hostsRef.get
      .flatTap(hosts => logger.info(s"Init cluster discovery from ${hosts.keys}"))
      .flatMap {
        case hosts =>
          getClusterInfo(hosts.keys)(client)
            .flatMap { currentStatus =>
              sessionRef.get
                .flatMap {
                  case oSession @ Some(_) => IO.pure(oSession)
                  case None =>
                    val session = findFirstElement(filterKeys(init.toNes, currentStatus)).map(_.clusterSession)
                    sessionRef.set(session).as(session)
                }
                .flatMap { session =>
                  session match {
                    case Some(clusterSession) =>
                      NonEmptySet
                        .fromSet(extractNewKeys(currentStatus)(toAddress))
                        .map(
                          getClusterInfo(_)
                            .map(newHostsStatus => currentStatus ++ newHostsStatus)
                        )
                        .getOrElse(IO.pure(currentStatus))
                        .flatMap { currentNodes =>
                          val (sameSessionNodes, otherSessionNodes) =
                            splitByElementProp(rotateMap(currentNodes)(toAddress))(_.clusterSession === clusterSession)
                          Applicative[IO].whenA(otherSessionNodes.nonEmpty) {
                            logger.warn(
                              s"Evicted nodes not in cluster session ${clusterSession}: ${otherSessionNodes.keySet} "
                            )
                          } >> IO.pure(NonEmptyMap.fromMapUnsafe(SortedMap.from(sameSessionNodes)))
                        }
                    case _ =>
                      logger.warn(
                        s"Undefined cluster session, node discovery skipped "
                      ) >> IO.pure(currentStatus)
                  }
                }
            }
      }
      .flatMap(
        status =>
          buildClusterStatus(status).flatMap {
            case (activeHosts, inactiveHosts) =>
              val clusterHosts = (activeHosts ++ inactiveHosts) // TODO: Add Eviction of inactive hosts
                .filterNot(addr => status.contains(addr))
                .foldLeft(status)((acc, addr) => acc.add(addr, None))

              hostsRef.set(clusterHosts).flatTap(_ => updateLbSetup(activeHosts))
          }
      )
  }

  private def manager(implicit client: Client[IO]): IO[Unit] = {
    val procedure = isUnderMaintenance.ifM(
      logger.info("Load balancer is in maintenance mode. Omitting update procedure."),
      updateProcedure
        .flatMap(
          _ =>
            lb.shouldRedirectToMaintenance.ifM(
              lb.disableRedirectingToMaintenance,
              IO.unit
            )
        )
    )

    procedure
      .flatTap(_ => logger.info("Scheduling next cluster status update round."))
      .flatMap(_ => IO.sleep(config.clusterUpdateRoundDelay))
      .flatMap(_ => manager)
  }

  def run: IO[ExitCode] =
    http
      .use { client =>
        NonEmptyList.of(settingsServer, lb.server, manager(client)).parSequence
      }
      .as(ExitCode.Success)

  def isUnderMaintenance: IO[Boolean] = underMaintenance.get

  def enableMaintenanceMode: IO[Unit] =
    for {
      _ <- underMaintenance.modify(_ => (true, ()))
      _ <- logger.info("Enabled maintenance mode.")
      _ <- lb.enableRedirectingToMaintenance
    } yield ()

  def disableMaintenanceMode: IO[Unit] =
    for {
      _ <- lb.reset
      _ <- reset
      _ <- underMaintenance.modify(_ => (false, ()))
      _ <- logger.info(
        "Disabled maintenance mode. Load balancer will be available after next cluster status update round."
      )
    } yield ()

  private val utilsHealth = HttpRoutes.of[IO] {
    case GET -> Root / "health" => Ok()
    case GET -> Root / "health" / addrStr =>
      Addr
        .unapply(addrStr)
        .map { addr =>
          hostsRef.get.flatMap(
            addrs =>
              addrs(addr)
                .map {
                  case None    => NoContent()
                  case Some(_) => Ok()
                }
                .getOrElse(NotFound())
          )
        }
        .getOrElse(BadRequest())
  }

  private val settingsServer: IO[Unit] =
    logger
      .info(s"Setup Settings instance on ${config.`if`}:${config.settingsPort}")
      .flatMap(
        _ =>
          http.use(
            _ => {
              val httpApp = Router("/utils" -> utilsHealth, "/settings" -> settingsRoutes).orNotFound
              BlazeServerBuilder[IO]
                .bindHttp(config.settingsPort, config.`if`)
                .withHttpApp(httpApp)
                .serve
                .compile
                .drain
            }
          )
      )

  private val settingsRoutes = HttpRoutes.of[IO] {
    case POST -> Root / "maintenance" =>
      enableMaintenanceMode >> Ok()
    case DELETE -> Root / "maintenance" =>
      disableMaintenanceMode >> Ok()
  }

  def buildClusterStatus(init: NonEmptyMap[Addr, Option[NonEmptyList[Info]]]): IO[(Set[Addr], Set[Addr])] = {
    val tresholdLevel = init.keys.size / 2

    def isActive(addr: Addr, proof: List[Info]) =
      init(addr).nonEmpty && proof.count(_.state == NodeState.Ready) > tresholdLevel

    IO {
      val (active, other) = rotateMap(init)(toAddress).toList
        .partition {
          case (addr, Some(proof)) => isActive(addr, proof.toList)
          case _                   => false
        }

      active.map(_._1).toSet -> other.map(_._1).toSet
    }.flatTap {
      case (active, other) =>
        logger.info(s"As a result of status analysis we have ${active} hosts and ${other} not ready")
    }
  }

  def getClusterInfo(
      hosts: NonEmptySet[Addr]
  )(implicit client: Client[IO]): IO[NonEmptyMap[Addr, Option[NonEmptyList[Info]]]] = {
    IO.apply(logger.info(s"Fetch cluster status from following ${hosts.size} hosts: ${hosts.toList.take(5)}"))
      .flatMap(
        _ =>
          hosts.toNonEmptyList
            .map(
              addr =>
                node(addr).getInfo
                  .flatMap {
                    case result @ x :: xs =>
                      logger
                        .debug(s"Node $addr returned $result")
                        .map(_ => addr -> Some(NonEmptyList(x, xs)))
                    case Nil =>
                      logger
                        .warn(s"Node $addr returned empty node list")
                        .map(_ => addr -> None)
                  }
                  .recoverWith {
                    case error =>
                      logger
                        .info(s"Cannot retrieve cluster info from addr=$addr error=$error")
                        .map(_ => addr -> None)
                  }
            )
            .parSequence
            .map(_.toNem)
      )
  }
}
