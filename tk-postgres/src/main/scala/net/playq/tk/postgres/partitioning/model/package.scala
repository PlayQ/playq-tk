package net.playq.tk.postgres.partitioning

package object model {
  @inline private[model] final def quoted(s: String): String = "" + '"' + s + '"'
}
