package idesyde.identification.models.platform

trait InstrumentedPlatformMixin {
  
    def processorsProvisions: Array[Map[String, Map[String, Double]]]
    def processorsFrequency: Array[Long]
}
