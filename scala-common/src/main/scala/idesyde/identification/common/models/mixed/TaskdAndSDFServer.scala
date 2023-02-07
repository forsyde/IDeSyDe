package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.workload.{
  CommunicatingExtendedDependenciesPeriodicWorkload,
  InstrumentedWorkloadMixin
}
import spire.math.Rational

final case class TaskdAndSDFServer(
    val sdf: SDFApplication,
    val task: CommunicatingExtendedDependenciesPeriodicWorkload,
    val sdfServerPeriod: Vector[Rational],
    val sdfServerBudget: Vector[Rational]
) extends StandardDecisionModel
    with InstrumentedWorkloadMixin {

  val coveredElements         = sdf.coveredElements ++ task.coveredElements
  val coveredElementRelations = sdf.coveredElementRelations ++ task.coveredElementRelations
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    sdf.processComputationalNeeds ++ task.processComputationalNeeds
  val processSizes: Vector[Long] = sdf.actorSizes ++ task.processSizes

  val messagesMaxSizes: Vector[Long] = sdf.messagesMaxSizes ++ task.messagesMaxSizes
  val uniqueIdentifier: String       = "TaskdAndSDFServer"
}
