package d4s.query.models

trait WithScanIndexForward[+D] {
  def withScanIndexForward(sif: Boolean): D
}
