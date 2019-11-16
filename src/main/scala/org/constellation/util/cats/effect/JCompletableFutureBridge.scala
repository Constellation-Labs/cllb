package org.constellation.util.cats.effect

import java.util.concurrent.{CancellationException, CompletableFuture, CompletionException}
import java.util.function.BiFunction

import cats.effect.IO

trait JCompletableFutureBridge {

  def fromJavaFuture[A](makeCf: => CompletableFuture[A]): IO[A] =
    IO.cancelable(cb => {
      val cf = makeCf
      cf.handle[Unit](new BiFunction[A, Throwable, Unit] {
        override def apply(result: A, err: Throwable): Unit = {
          err match {
            case null =>
              cb(Right(result))
            case _: CancellationException =>
              ()
            case ex: CompletionException if ex.getCause ne null =>
              cb(Left(ex.getCause))
            case ex =>
              cb(Left(ex))
          }
        }
      })
      IO(cf.cancel(true))
    })
}
