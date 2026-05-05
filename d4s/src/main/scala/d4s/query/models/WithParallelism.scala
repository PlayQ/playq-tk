package d4s.query.models

trait WithParallelism[+D] {
  def maxParallelism: Option[Int]
  def withParallelism(parallelism: Int): D
}
