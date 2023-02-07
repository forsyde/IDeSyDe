package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.workload.{CommunicatingExtendedDependenciesPeriodicWorkload, InstrumentedWorkloadMixin}
import spire.math.Rational

final case class SDFandTask(
                     sdf:SDFApplication,
                     task:CommunicatingExtendedDependenciesPeriodicWorkload,
                     sdfServerperiod: Vector[Rational],
                     sdfServerBudget: Vector[Rational])
  extends StandardDecisionModel
  with InstrumentedWorkloadMixin{

    val coveredElements = sdf.coveredElements ++ task.coveredElements
    val coveredElementRelations=sdf.coveredElementRelations ++ task.coveredElementRelations
    val processComputationalNeeds=sdf.processComputationalNeeds ++ task.processComputationalNeeds
    val processSizes = sdf.actorSizes ++ task.processSizes

    val messagesMaxSizes=sdf.messagesMaxSizes ++ task.messagesMaxSizes
    val uniqueIdentifier: String = "SDFandTask"
}
