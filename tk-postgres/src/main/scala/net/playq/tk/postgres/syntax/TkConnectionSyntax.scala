package net.playq.tk.postgres.syntax

import net.playq.tk.postgres.syntax.TkConnectionSyntax.SyntaxConnectionIOOps

import scala.language.implicitConversions

trait TkConnectionSyntax {
  @inline implicit final def toSyntaxConnectionIOOps[A](fr: doobie.ConnectionIO[A]): SyntaxConnectionIOOps[A] = new SyntaxConnectionIOOps(fr)
}

object TkConnectionSyntax {
  implicit final class SyntaxConnectionIOOps[A](private val fr: doobie.ConnectionIO[A]) extends AnyVal {
    def void: doobie.ConnectionIO[Unit] = fr.map(_ => ())
  }
}
