// Copyright (c) 2013-2017 Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package doobie.free

import cats.~>
import cats.effect.Async
import cats.free.{ Free => FF } // alias because some algebras have an op called Free

import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.lang.String
import java.sql.Clob

object clob { module =>

  // Algebra of operations for Clob. Each accepts a visitor as an alternatie to pattern-matching.
  sealed trait ClobOp[A] {
    def visit[F[_]](v: ClobOp.Visitor[F]): F[A]
  }

  // Free monad over ClobOp.
  type ClobIO[A] = FF[ClobOp, A]

  // Module of instances and constructors of ClobOp.
  object ClobOp {

    // Given a Clob we can embed a ClobIO program in any algebra that understands embedding.
    implicit val ClobOpEmbeddable: Embeddable[ClobOp, Clob] =
      new Embeddable[ClobOp, Clob] {
        def embed[A](j: Clob, fa: FF[ClobOp, A]) = Embedded.Clob(j, fa)
      }

    // Interface for a natural tansformation ClobOp ~> F encoded via the visitor pattern.
    // This approach is much more efficient than pattern-matching for large algebras.
    trait Visitor[F[_]] extends (ClobOp ~> F) {
      final def apply[A](fa: ClobOp[A]): F[A] = fa.visit(this)

      // Common
      def raw[A](f: Clob => A): F[A]
      def embed[A](e: Embedded[A]): F[A]
      def delay[A](a: () => A): F[A]
      def handleErrorWith[A](fa: ClobIO[A], f: Throwable => ClobIO[A]): F[A]
      def async[A](k: (Either[Throwable, A] => Unit) => Unit): F[A]

      // Clob
      def free: F[Unit]
      def getAsciiStream: F[InputStream]
      def getCharacterStream: F[Reader]
      def getCharacterStream(a: Long, b: Long): F[Reader]
      def getSubString(a: Long, b: Int): F[String]
      def length: F[Long]
      def position(a: Clob, b: Long): F[Long]
      def position(a: String, b: Long): F[Long]
      def setAsciiStream(a: Long): F[OutputStream]
      def setCharacterStream(a: Long): F[Writer]
      def setString(a: Long, b: String): F[Int]
      def setString(a: Long, b: String, c: Int, d: Int): F[Int]
      def truncate(a: Long): F[Unit]

    }

    // Common operations for all algebras.
    case class Raw[A](f: Clob => A) extends ClobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.raw(f)
    }
    case class Embed[A](e: Embedded[A]) extends ClobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.embed(e)
    }
    case class Delay[A](a: () => A) extends ClobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.delay(a)
    }
    case class HandleErrorWith[A](fa: ClobIO[A], f: Throwable => ClobIO[A]) extends ClobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.handleErrorWith(fa, f)
    }
    case class Async1[A](k: (Either[Throwable, A] => Unit) => Unit) extends ClobOp[A] {
      def visit[F[_]](v: Visitor[F]) = v.async(k)
    }

    // Clob-specific operations.
    case object Free extends ClobOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.free
    }
    case object GetAsciiStream extends ClobOp[InputStream] {
      def visit[F[_]](v: Visitor[F]) = v.getAsciiStream
    }
    case object GetCharacterStream extends ClobOp[Reader] {
      def visit[F[_]](v: Visitor[F]) = v.getCharacterStream
    }
    case class  GetCharacterStream1(a: Long, b: Long) extends ClobOp[Reader] {
      def visit[F[_]](v: Visitor[F]) = v.getCharacterStream(a, b)
    }
    case class  GetSubString(a: Long, b: Int) extends ClobOp[String] {
      def visit[F[_]](v: Visitor[F]) = v.getSubString(a, b)
    }
    case object Length extends ClobOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.length
    }
    case class  Position(a: Clob, b: Long) extends ClobOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.position(a, b)
    }
    case class  Position1(a: String, b: Long) extends ClobOp[Long] {
      def visit[F[_]](v: Visitor[F]) = v.position(a, b)
    }
    case class  SetAsciiStream(a: Long) extends ClobOp[OutputStream] {
      def visit[F[_]](v: Visitor[F]) = v.setAsciiStream(a)
    }
    case class  SetCharacterStream(a: Long) extends ClobOp[Writer] {
      def visit[F[_]](v: Visitor[F]) = v.setCharacterStream(a)
    }
    case class  SetString(a: Long, b: String) extends ClobOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.setString(a, b)
    }
    case class  SetString1(a: Long, b: String, c: Int, d: Int) extends ClobOp[Int] {
      def visit[F[_]](v: Visitor[F]) = v.setString(a, b, c, d)
    }
    case class  Truncate(a: Long) extends ClobOp[Unit] {
      def visit[F[_]](v: Visitor[F]) = v.truncate(a)
    }

  }
  import ClobOp._

  // Smart constructors for operations common to all algebras.
  val unit: ClobIO[Unit] = FF.pure[ClobOp, Unit](())
  def raw[A](f: Clob => A): ClobIO[A] = FF.liftF(Raw(f))
  def embed[F[_], J, A](j: J, fa: FF[F, A])(implicit ev: Embeddable[F, J]): FF[ClobOp, A] = FF.liftF(Embed(ev.embed(j, fa)))
  def delay[A](a: => A): ClobIO[A] = FF.liftF(Delay(() => a))
  def handleErrorWith[A](fa: ClobIO[A], f: Throwable => ClobIO[A]): ClobIO[A] = FF.liftF[ClobOp, A](HandleErrorWith(fa, f))
  def raiseError[A](err: Throwable): ClobIO[A] = delay(throw err)
  def async[A](k: (Either[Throwable, A] => Unit) => Unit): ClobIO[A] = FF.liftF[ClobOp, A](Async1(k))

  // Smart constructors for Clob-specific operations.
  val free: ClobIO[Unit] = FF.liftF(Free)
  val getAsciiStream: ClobIO[InputStream] = FF.liftF(GetAsciiStream)
  val getCharacterStream: ClobIO[Reader] = FF.liftF(GetCharacterStream)
  def getCharacterStream(a: Long, b: Long): ClobIO[Reader] = FF.liftF(GetCharacterStream1(a, b))
  def getSubString(a: Long, b: Int): ClobIO[String] = FF.liftF(GetSubString(a, b))
  val length: ClobIO[Long] = FF.liftF(Length)
  def position(a: Clob, b: Long): ClobIO[Long] = FF.liftF(Position(a, b))
  def position(a: String, b: Long): ClobIO[Long] = FF.liftF(Position1(a, b))
  def setAsciiStream(a: Long): ClobIO[OutputStream] = FF.liftF(SetAsciiStream(a))
  def setCharacterStream(a: Long): ClobIO[Writer] = FF.liftF(SetCharacterStream(a))
  def setString(a: Long, b: String): ClobIO[Int] = FF.liftF(SetString(a, b))
  def setString(a: Long, b: String, c: Int, d: Int): ClobIO[Int] = FF.liftF(SetString1(a, b, c, d))
  def truncate(a: Long): ClobIO[Unit] = FF.liftF(Truncate(a))

  // ClobIO is an Async
  implicit val AsyncClobIO: Async[ClobIO] =
    new Async[ClobIO] {
      val M = FF.catsFreeMonadForFree[ClobOp]
      def pure[A](x: A): ClobIO[A] = M.pure(x)
      def handleErrorWith[A](fa: ClobIO[A])(f: Throwable => ClobIO[A]): ClobIO[A] = module.handleErrorWith(fa, f)
      def raiseError[A](e: Throwable): ClobIO[A] = module.raiseError(e)
      def async[A](k: (Either[Throwable,A] => Unit) => Unit): ClobIO[A] = module.async(k)
      def flatMap[A, B](fa: ClobIO[A])(f: A => ClobIO[B]): ClobIO[B] = M.flatMap(fa)(f)
      def tailRecM[A, B](a: A)(f: A => ClobIO[Either[A, B]]): ClobIO[B] = M.tailRecM(a)(f)
      def suspend[A](thunk: => ClobIO[A]): ClobIO[A] = M.flatten(module.delay(thunk))
    }

}
