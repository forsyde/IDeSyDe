package idesyde.common

import scala.jdk.CollectionConverters.*

import upickle.default.*

import idesyde.core.DecisionModel
import java.{util => ju}

final case class SDFToTiledMultiCore(
    val sdfApplications: SDFApplicationWithFunctions,
    val platform: SchedulableTiledMultiCore,
    val processMappings: Vector[String],
    val messageMappings: Vector[String],
    val schedulerSchedules: Vector[Vector[String]],
    val messageSlotAllocations: Vector[Map[String, Vector[Boolean]]],
    val actorThroughputs: Vector[Double]
) extends DecisionModel
    with WCETComputationMixin(sdfApplications, platform)
    derives ReadWriter {

  override def part(): ju.Set[String] =
    (sdfApplications.part().asScala ++ platform.part().asScala ++ (sdfApplications.actorsIdentifiers
      .zip(processMappings) ++
      sdfApplications.channelsIdentifiers.zip(messageMappings) ++
      messageSlotAllocations.zipWithIndex.flatMap((slots, i) =>
        platform.hardware.communicationElems
          .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
          .map(ce => sdfApplications.channelsIdentifiers(i) -> ce)
      )).map(_.toString)).asJava

  val processorsFrequency: Vector[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Vector[Long] = sdfApplications.messagesMaxSizes
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    sdfApplications.actorComputationalNeeds
  val processSizes: Vector[Long] = sdfApplications.processSizes

  val wcets = computeWcets

  override def asJsonString(): java.util.Optional[String] = try {
    java.util.Optional.of(write(this))
  } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try {
    java.util.Optional.of(writeBinary(this))
  } catch { case _ => java.util.Optional.empty() }

  override def category(): String = "SDFToTiledMultiCore"
}
