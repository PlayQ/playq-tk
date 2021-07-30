package net.playq.tk.postgres

import doobie.syntax.string._
import net.playq.tk.postgres.RawSQL
import net.playq.tk.postgres.partitioning.model.TableName
import org.scalatest.wordspec.AnyWordSpec

class DynamicSQLInterpolatorTest extends AnyWordSpec {

  "produce correct fragment (TableName)" in {
    val table1 = TableName("public", "my_table_1")
    val table2 = TableName("public", "my_table_2")

    val i = 5
    val z = "asdkjfgj"

    assert(
      sql"select (abc, cba) from $table1 join $table2 where x = $i and y = $z".query[Unit].sql
      == sql"""select (abc, cba) from "public"."my_table_1" join "public"."my_table_2" where x = $i and y = $z""".query[Unit].sql
    )
  }

  "produce correct fragment (RawSQL)" in {
    val table1: RawSQL = TableName("public", "my_table_1"): RawSQL
    val table2: RawSQL = TableName("public", "my_table_2"): RawSQL

    val i = 5
    val z = "asdkjfgj"

    assert(
      sql"select (abc, cba) from $table1 join $table2 where x = $i and y = $z".query[Unit].sql
      == sql"""select (abc, cba) from "public"."my_table_1" join "public"."my_table_2" where x = $i and y = $z""".query[Unit].sql
    )
  }

  "work without raws" in {
    val i = 5
    val z = "asdkjfgj"

    assert(
      sql"select (abc, cba) from tab where x = $i and y = $z".query[Unit].sql
      == sql"""select (abc, cba) from tab where x = $i and y = $z""".query[Unit].sql
    )
  }

  "work with different raws" in {
    val table1 = TableName("public", "my_table_1")
    val table2 = TableName("public", "my_table_2")

    val i    = 5
    val z    = "asdkjfgj"
    val name = RawSQL("xyz")

    assert(
      sql"select (abc, cba) from $table1 join $table2 where $name = $i and y = $z".query[Unit].sql
      == sql"""select (abc, cba) from "public"."my_table_1" join "public"."my_table_2" where xyz = $i and y = $z""".query[Unit].sql
    )
  }

}
