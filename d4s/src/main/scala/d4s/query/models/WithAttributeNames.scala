package d4s.query.models

trait WithAttributeNames[+D] {
  def withAttributeNames(f: Map[String, String] => Map[String, String]): D

  final def withAttributeNames(add: Map[String, String]): D = withAttributeNames(_ ++ add)
  final def withAttributeNames(value: (String, String)): D  = withAttributeNames(_ + value)
}
