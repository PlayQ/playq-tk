package tk.sbtgen

import izumi.sbtgen._
import izumi.sbtgen.model._

object Main {
  def main(args: Array[String]): Unit = {
    Entrypoint.main(
      new TkBuild(_ => ".").tk_project,
      GlobalSettings(
        groupId    = "net.playq",
        sbtVersion = None,
      ),
      Seq("-o", ".") ++ args,
    )
  }
}
