package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.models.mixed.WCETComputationMixin

final case class SDFtaskToMultiCore(val sdfandtask: SDFandTask,
                                    val platform: SchedulableTiledMultiCore,
                                    val processMappings: Vector[String],
                                    val messageMappings: Vector[String],
                                    val schedulerSchedules: Vector[Vector[String]],
                                    val messageSlotAllocations: Vector[Map[String, Vector[Boolean]]]
                                   ) extends StandardDecisionModel
  with WCETComputationMixin(sdfandtask, platform) {

  val coveredElements = sdfandtask.coveredElements ++ platform.coveredElements
  val coveredElementRelations =
    sdfandtask.coveredElementRelations ++ platform.coveredElementRelations

  val processorsFrequency: Vector[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Vector[Map[String, Map[String, spire.math.Rational]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Vector[Long] = sdfandtask.messagesMaxSizes
  //  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
  //   sdfandtask.actorComputationalNeeds
  // val processSizes: Vector[Long] = sdfandtask.totalprocessSizes

 // val wcets = computeWcets

  val uniqueIdentifier: String = "SDFtaskToMultiCore"
}
