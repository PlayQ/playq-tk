package net.playq.tk.postgres

import net.playq.tk.postgres.codecs.TkPostgresInstances
import net.playq.tk.postgres.syntax.TkConnectionSyntax

object implicits
  extends doobie.postgres.Instances
  with doobie.syntax.ToSqlInterpolator
  with doobie.syntax.ToConnectionIOOps
  with TkConnectionSyntax
  with TkPostgresInstances
