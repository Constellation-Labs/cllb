package org.constellation

import java.net.InetAddress

import cats.data.NonEmptyList
import cats.effect.{ExitCode, IO, IOApp}
import org.constellation.lb.Manager
import org.constellation.util.validators.Hosts
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.chrisdavenport.log4cats.Logger
import pureconfig._
import pureconfig.generic.auto._
import cats.implicits._
import org.constellation.primitives.node.Addr._

case class Config(hosts: List[InetAddress] = Nil)

object NetworkLoadbalancer extends IOApp {
  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {

    Hosts
      .validate(args)
      .leftMap(err => logger.error(s"Cannot run, peer list validation failed with ${err}"))
      .toEither
      .flatMap(
        commandlineNodes =>
          ConfigSource.default
            .load[LoadbalancerConfig]
            .bimap(
              error => logger.error(s"Configuration errors: $error"),
              config => (NonEmptyList.fromList(config.networkNodes.toList) |+| commandlineNodes).map(_ -> config)
            )
      )
      .flatMap {
        case Some(context) => Right(context)
        case _             => Left(logger.error("The list of initial hosts is empty, provide via config or commandline"))
      }
      .fold(
        _.map(_ => ExitCode.Error), {
          case (hosts, config) => new Manager(hosts, config).run
        }
      )
  }
}
