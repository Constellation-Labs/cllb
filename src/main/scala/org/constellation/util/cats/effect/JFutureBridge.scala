package org.constellation.util.cats.effect

import cats.effect.implicits._
import cats.effect.{ContextShift, ExitCase, Sync, Timer}
import cats.syntax.all._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

trait JFutureBridge {
  // Alias for more friendly imports
  type JFuture[A] = java.util.concurrent.Future[A]

  /** Convert a java future to an effectful value, using async sleep polling to get the result.
   *
   * Because this uses async sleep, it won't block a thread.
   *
   * Java future only offers us blocking and polling as the interface
   *
   * @param mayInterruptThreadOnCancel This is passed as an argument to [[JFuture#cancel]]
   *                                   in the case the returned `F[A]` is cancelled.
   */
  def toIO[F[_], A](
    fa: F[JFuture[A]],
    pollInterval: FiniteDuration = 100.millis,
    mayInterruptThreadOnCancel: Boolean = true
  )(
    implicit F: Sync[F],
    timer: Timer[F]
  ): F[A] = {
    def loop(jf: JFuture[A]): F[A] =
      F.delay(jf.isDone).flatMap { isDone =>
        // Not ContextShift.evalOn(blockingPool) here because isDone==true, so this should be very fast to return.
        if (isDone) F.delay(jf.get)
        else timer.sleep(pollInterval) *> loop(jf)
      }

    fa.flatMap { jf =>
      loop(jf)
        .guaranteeCase {
          case ExitCase.Canceled =>
            F.delay(jf.cancel(mayInterruptThreadOnCancel)).void
          case _ => F.unit
        }
    }
  }

  /** Convert a java future to an effectful value, blocking a thread on the specified ExecutionContext
   * to get the result.
   * Java future only offers us blocking and polling as the interface */
  def toIO[F[_], A](
    fa: F[JFuture[A]],
    blockingExecutionContext: ExecutionContext
  )(
    implicit F: Sync[F],
    CS: ContextShift[F]
  ): F[A] =
    fa.flatMap { jf =>
      CS.evalOn(blockingExecutionContext)(F.delay(jf.get))
    }
}
