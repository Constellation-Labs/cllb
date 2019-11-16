package org.constellation

import java.net.{InetAddress, UnknownHostException}

import cats.effect.{ExitCode, IO, IOApp}
import cats._
import cats.data._
import cats.syntax.all._
import org.constellation.elb.Manager
import org.constellation.primitives.node.Addr

import scala.util.control.Exception.catching

case class Config(hosts: List[InetAddress] = Nil)

object ElbManager extends IOApp {

  def validateHosts(input: List[String])
    : ValidatedNel[ArgumentValidationError, NonEmptyList[Addr]] =
    input match {
      case Nil =>
        EmptyListOfHosts.invalidNel
      case head :: tail =>
        NonEmptyList(head, tail)
          .map(_.split(':').toList)
          .map {
            case addr :: port :: Nil =>
              catching(classOf[UnknownHostException], classOf[NumberFormatException])
                .either(Addr(InetAddress.getByName(addr), port.toInt))
                .leftMap{
                  case _ : NumberFormatException => AddressMalformed(s"$addr:$port")
                  case _ => HostUnknown(addr)
                }
                .toValidatedNel
            case invalid => AddressMalformed(s"${invalid}").invalidNel[Addr]
          }
          .sequence
    }

  sealed trait ArgumentValidationError

  case object EmptyListOfHosts extends ArgumentValidationError
  case object EmptyListOfValidHosts extends ArgumentValidationError
  case class HostUnknown(addr: String) extends ArgumentValidationError
  case class AddressMalformed(addr: String) extends ArgumentValidationError

  override def run(args: List[String]): IO[ExitCode] = {

    validateHosts(args)
      .fold(err =>
              IO.apply {
                println(s"Cannot run, peer list validation failed with ${err}")
                ExitCode.Error
            },
            addr => new Manager(addr).run().flatMap(_ => IO.pure(ExitCode.Success)))
  }
}
