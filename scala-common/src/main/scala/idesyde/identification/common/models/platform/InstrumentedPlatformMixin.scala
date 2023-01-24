package idesyde.identification.common.models.platform

trait InstrumentedPlatformMixin[RealT] {

  def processorsProvisions: Vector[Map[String, Map[String, RealT]]]
  def processorsFrequency: Vector[Long]
}
