package d4s.query.models

trait WithActions[A, +D] {
  def actions: List[A]

  /** Overwrites action list, subsequent calls do not append. */
  def withActions(actions: List[A]): D
}
