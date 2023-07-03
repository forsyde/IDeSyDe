package idesyde.common

import upickle.default._

import idesyde.core.DecisionModel
import idesyde.core.CompleteDecisionModel

final case class PeriodicWorkloadToPartitionedSharedMultiCore(
    val workload: CommunicatingAndTriggeredReactiveWorkload,
    val platform: PartitionedSharedMemoryMultiCore,
    val processMappings: Vector[(String, String)],
    val processSchedulings: Vector[(String, String)],
    val channelMappings: Vector[(String, String)],
    val channelSlotAllocations: Map[String, Map[String, Vector[Boolean]]],
    val maxUtilizations: Map[String, Double]
) extends StandardDecisionModel
    with CompleteDecisionModel
    with WCETComputationMixin(workload, platform.hardware)
    derives ReadWriter {

  override def bodyAsText: String = write(this)

  override def bodyAsBinary: Array[Byte] = writeBinary(this)

  val coveredElements: Set[String] =
    workload.coveredElements ++ platform.coveredElements ++ (processSchedulings.toSet ++
      processMappings.toSet ++
      channelMappings.toSet ++
      channelSlotAllocations
        .flatMap[String, String]((channel, slots) =>
          platform.hardware.communicationElems
            .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
            .map(ce => (channel, ce))
        )
        .toSet).map(_.toString)

  val wcets = computeWcets

  /** since the max utilizations are not vertex themselves, we override it to consider the decision
    * model with most information the dominant one.
    */
  override def dominates(other: DecisionModel): Boolean = other match {
    case o: PeriodicWorkloadToPartitionedSharedMultiCore =>
      super.dominates(other) && o.maxUtilizations.keySet.subsetOf(maxUtilizations.keySet)
    case _ => super.dominates(other)
  }

  def category: String = "PeriodicWorkloadToPartitionedSharedMultiCore"
}
