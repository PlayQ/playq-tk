package d4s.query.models

trait WithConsistent[+D] {
  def withConsistent(consistentRead: Boolean): D

  final def consistent: D = withConsistent(true)
}
