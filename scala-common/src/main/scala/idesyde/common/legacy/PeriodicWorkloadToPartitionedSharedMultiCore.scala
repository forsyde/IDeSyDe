package idesyde.common.legacy

import scala.jdk.CollectionConverters._

import upickle.default._

import idesyde.core.DecisionModel
import java.{util => ju}

final case class PeriodicWorkloadToPartitionedSharedMultiCore(
    val workload: CommunicatingAndTriggeredReactiveWorkload,
    val platform: PartitionedSharedMemoryMultiCore,
    val processMappings: Vector[(String, String)],
    val processSchedulings: Vector[(String, String)],
    val channelMappings: Vector[(String, String)],
    val channelSlotAllocations: Map[String, Map[String, Vector[Boolean]]],
    val maxUtilizations: Map[String, Double]
) extends DecisionModel
    with WCETComputationMixin(workload, platform.hardware)
    derives ReadWriter {

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }

  override def part(): ju.Set[String] =
    (workload.part().asScala ++ platform.part().asScala ++ (processSchedulings.toSet ++
      processMappings.toSet ++
      channelMappings.toSet ++
      channelSlotAllocations
        .flatMap[String, String]((channel, slots) =>
          platform.hardware.communicationElems
            .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
            .map(ce => (channel, ce))
        )
        .toSet).map(_.toString)).asJava

  val wcets = computeWcets

  /** since the max utilizations are not vertex themselves, we override it to consider the decision
    * model with most information the dominant one.
    */
  // override def dominates(other: DecisionModel): Boolean = other match {
  //   case o: PeriodicWorkloadToPartitionedSharedMultiCore =>
  //     super.dominates(other) && o.maxUtilizations.keySet.subsetOf(maxUtilizations.keySet)
  //   case _ => super.dominates(other)
  // }

  override def category(): String = "PeriodicWorkloadToPartitionedSharedMultiCore"
}
