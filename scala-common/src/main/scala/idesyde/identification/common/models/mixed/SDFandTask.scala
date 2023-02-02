package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.workload.{CommunicatingExtendedDependenciesPeriodicWorkload, InstrumentedWorkloadMixin}
import spire.math.Rational

final case class SDFandTask(
                     sdf:SDFApplication,
                     task:CommunicatingExtendedDependenciesPeriodicWorkload,
                     sdfperiod: Vector[Rational],
                     totalperiod: Vector[Rational],
                     ) extends StandardDecisionMode with InstrumentedWorkloadMixin{
  val coverElements=sdf.coveredElements ++ task.coveredElements
  val coveredElementRelations=sdf.coveredElementRelations ++ task.coveredElementRelations\
  val graph 
  val totalprocessSizes = sdf.actorSizes ++ task.processSizes
  
  val messagesMaxSizes=sdf.messagesMaxSizes ++ task.messagesMaxSizes
  val uniqueIdentifier: String = "SDFandTask"
}
