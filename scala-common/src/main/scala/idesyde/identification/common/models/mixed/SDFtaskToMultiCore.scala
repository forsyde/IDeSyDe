package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.models.mixed.WCETComputationMixin

final case class SDFtaskToMultiCore(
       val sdfandtask: SDFandTask,
       val platform: SchedulableTiledMultiCore,
       val actorprocessMappings: Vector[String],
       val processMappings: Vector[(String, String)],
       val actormessageMappings: Vector[String],
       val channelMappings: Vector[(String, String)],
       val schedulerSchedules: Vector[Vector[String]],
       val messageMappings: Vector[(String, String)],
       val messageSlotAllocations: Vector[Map[String, Vector[Boolean]]]
                                   ) extends StandardDecisionModel
  with WCETComputationMixin(sdfandtask, platform) {

  val coveredElements = sdfandtask.coveredElements ++ platform.coveredElements
  val coveredElementRelations =
    sdfandtask.coveredElementRelations ++ platform.coveredElementRelations ++
      sdfandtask.sdf.actorsIdentifiers.zip(actorprocessMappings)  ++ processMappings.toSet ++
      channelMappings.toSet ++ sdfandtask.sdf.channelsIdentifiers.zip(actormessageMappings) ++
      messageSlotAllocations.zipWithIndex.flatMap((i,slots) =>
        platform.hardware.communicationElems
          .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
          .map(ce => (i, ce))
      )



  val processorsFrequency: Vector[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Vector[Map[String, Map[String, spire.math.Rational]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Vector[Long] = sdfandtask.messagesMaxSizes


  val wcets = computeWcets

  val uniqueIdentifier: String = "SDFtaskToMultiCore"
}
