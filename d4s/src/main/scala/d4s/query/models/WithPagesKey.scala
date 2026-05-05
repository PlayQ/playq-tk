package d4s.query.models

trait WithPagesKey[PageKey, +D] {
  def withPageKey(pageKey: Option[PageKey]): D
}
