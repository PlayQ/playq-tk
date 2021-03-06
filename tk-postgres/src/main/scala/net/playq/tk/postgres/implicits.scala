package net.playq.tk.postgres

import doobie.postgres
import doobie.syntax.ToSqlInterpolator
import net.playq.tk.postgres.codecs.TkDoobieInstances
import net.playq.tk.postgres.syntax.LoggedQuerySyntax

object implicits extends LoggedQuerySyntax with ToSqlInterpolator with postgres.Instances with TkDoobieInstances
