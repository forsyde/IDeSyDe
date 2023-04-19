package idesyde.identification.common.models.mixed

import upickle.default.*

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.workload.InstrumentedWorkloadMixin
import idesyde.identification.models.mixed.WCETComputationMixin
import idesyde.core.CompleteDecisionModel

final case class SDFToTiledMultiCore(
    val sdfApplications: SDFApplication,
    val platform: SchedulableTiledMultiCore,
    val processMappings: Vector[String],
    val messageMappings: Vector[String],
    val schedulerSchedules: Vector[Vector[String]],
    val messageSlotAllocations: Vector[Map[String, Vector[Boolean]]],
    val actorThroughputs: Vector[Double]
) extends StandardDecisionModel
    with CompleteDecisionModel
    with WCETComputationMixin(sdfApplications, platform) derives ReadWriter {

  val coveredElements = sdfApplications.coveredElements ++ platform.coveredElements
  val coveredElementRelations =
    sdfApplications.coveredElementRelations ++ platform.coveredElementRelations ++
      sdfApplications.actorsIdentifiers.zip(processMappings) ++
      sdfApplications.channelsIdentifiers.zip(messageMappings) ++
      messageSlotAllocations.zipWithIndex.flatMap((slots, i) =>
        platform.hardware.communicationElems
          .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
          .map(ce => sdfApplications.channelsIdentifiers(i) -> ce)
      )

  val processorsFrequency: Vector[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Vector[Long] = sdfApplications.messagesMaxSizes
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    sdfApplications.actorComputationalNeeds
  val processSizes: Vector[Long] = sdfApplications.processSizes

  val wcets = computeWcets

  val bodyAsBinary: Array[Byte] = writeBinary(this)

  val bodyAsText: String = write(this)

  val uniqueIdentifier: String = "SDFToTiledMultiCore"
}
