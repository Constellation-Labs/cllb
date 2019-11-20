package org.constellation.util.validators

import java.net.{InetAddress, UnknownHostException}

import cats.syntax.all._
import cats.data.{NonEmptyList, ValidatedNel}
import org.constellation.primitives.node.Addr

import scala.util.control.Exception.catching

object Hosts {
  def validate(input: List[String]): ValidatedNel[ArgumentValidationError, Option[NonEmptyList[Addr]]] =
    input match {
      case Nil =>
        Option.empty[NonEmptyList[Addr]].validNel
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
          .map(Option(_))
    }

  sealed trait ArgumentValidationError

  case object EmptyListOfHosts extends ArgumentValidationError
  case object EmptyListOfValidHosts extends ArgumentValidationError
  case class HostUnknown(addr: String) extends ArgumentValidationError
  case class AddressMalformed(addr: String) extends ArgumentValidationError
}
