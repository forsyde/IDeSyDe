package idesyde.identification.models.platform

trait InstrumentedPlatformMixin[RealT](using spire.math.Fractional[RealT]) {

  def processorsProvisions: Array[Map[String, Map[String, RealT]]]
  def processorsFrequency: Array[Long]
}
