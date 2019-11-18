package org.constellation

import java.net.InetAddress

import cats.effect.{ExitCode, IO, IOApp}
import org.constellation.lb.Manager
import org.constellation.util.validators.Hosts
import cats.syntax.all._

case class Config(hosts: List[InetAddress] = Nil)

object NetworkLoadbalancer extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    Hosts.validate(args).fold(
      err => IO(println(s"Cannot run, peer list validation failed with ${err}")).as(ExitCode.Error),
      addr => new Manager(addr).run())
  }
}
