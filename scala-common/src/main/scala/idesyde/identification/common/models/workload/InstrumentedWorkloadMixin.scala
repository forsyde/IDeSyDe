package idesyde.identification.common.models.workload

trait InstrumentedWorkloadMixin {

  def processComputationalNeeds: Vector[Map[String, Map[String, Long]]]
  def processSizes: Vector[Long]

  def messagesMaxSizes: Vector[Long]

}
