package net.playq.tk.postgres

import doobie.syntax.SqlInterpolator.SingleFragment
import doobie.util.fragment.Fragment

import scala.language.implicitConversions

trait RawSQL {
  def sqlString: String

  override final def toString: String = sqlString
  final def fragment: Fragment        = Fragment.const0(sqlString)
}

object RawSQL {
  def apply(s: String): RawSQL           = new RawSQL { final val sqlString = s }
  def unapply(arg: RawSQL): Some[String] = Some(arg.sqlString)

  implicit def ToSingleFragment(rawSql: RawSQL): SingleFragment[Nothing] = SingleFragment(rawSql.fragment)
}
