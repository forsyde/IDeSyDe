package idesyde.identification.forsyde.models.platform

import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.runtime.{
  FixedPriorityScheduler,
  RoundRobinScheduler,
  TimeTriggeredScheduler
}
import idesyde.identification.forsyde.ForSyDeDecisionModel
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.graph.{DefaultEdge, SimpleGraph}

import java.util.stream.Collectors

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler

final case class SchedulableNetworkedDigHW(
    val hardware: NetworkedDigitalHardware,
    val schedulers: Array[AbstractScheduler],
    val schedulerAllocation: Array[Int]
    // val bandWidthFromCEtoPE: Map[GenericCommunicationModule, GenericProcessingModule],
) extends ForSyDeDecisionModel {

  val coveredElements = hardware.coveredElements ++
    schedulers.map(_.getViewedVertex)

  val coveredElementRelations = Set()

  val allocatedSchedulers =
    schedulers.zipWithIndex.filter((s, i) => schedulerAllocation(i) > -1).map(_._1)

  val isFixedPriority = allocatedSchedulers.map(FixedPriorityScheduler.conforms(_))
  val isTimeTriggered = allocatedSchedulers.map(TimeTriggeredScheduler.conforms(_))
  val isRoundRobin    = allocatedSchedulers.map(RoundRobinScheduler.conforms(_))
  val isStaticCycle   = allocatedSchedulers.map(StaticCyclicScheduler.conforms(_))

  val fixedPrioritySchedulers =
    allocatedSchedulers
      .filter(FixedPriorityScheduler.conforms(_))
      .map(FixedPriorityScheduler.enforce(_))
  val timeTriggeredSchedulers =
    allocatedSchedulers
      .filter(TimeTriggeredScheduler.conforms(_))
      .map(TimeTriggeredScheduler.enforce(_))
  val roundRobinSchedulers =
    allocatedSchedulers.filter(RoundRobinScheduler.conforms(_)).map(RoundRobinScheduler.enforce(_))
  val staticCycleSchedulers =
    allocatedSchedulers
      .filter(StaticCyclicScheduler.conforms(_))
      .map(StaticCyclicScheduler.enforce(_))

  val fixedPriorityPEs: Array[GenericProcessingModule] = hardware.processingElems.zipWithIndex
    .filter((pe, i) => schedulerAllocation.contains(i) && isFixedPriority(i))
    .map(_._1)
  val timeTriggeredPEs: Array[GenericProcessingModule] = hardware.processingElems.zipWithIndex
    .filter((pe, i) => schedulerAllocation.contains(i) && isTimeTriggered(i))
    .map(_._1)
  val roundRobinPEs: Array[GenericProcessingModule] = hardware.processingElems.zipWithIndex
    .filter((pe, i) => schedulerAllocation.contains(i) && isRoundRobin(i))
    .map(_._1)
  val staticCyclicPEs: Array[GenericProcessingModule] = hardware.processingElems.zipWithIndex
    .filter((pe, i) => schedulerAllocation.contains(i) && isStaticCycle(i))
    .map(_._1)

  lazy val schedulersFromPEs: Map[
    GenericProcessingModule,
    AbstractScheduler
  ] = (for (
    (pe, i)    <- hardware.processingElems.zipWithIndex;
    (sched, j) <- allocatedSchedulers.zipWithIndex;
    if schedulerAllocation(j) == i
  ) yield pe -> sched).toMap
  /*
  lazy val fixedPriorityTopologySymmetryRelation
      : Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (
      p  <- fixedPriorityPEs;
      pp <- fixedPriorityPEs;
      m  <- hardware.storageElems
      // check if exists one AND ONLY ONE memory element in which the path bandwidths
      // are exactly equal.
      if p == pp || hardware.storageElems.count(mm => {
        hardware.paths.getOrElse((m, p), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths.getOrElse((p, m), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths
            .getOrElse((mm, pp), Seq())
            .map(c => hardware.bandWidthBitPerSec(c, pp))
            .sum +
          hardware.paths.getOrElse((pp, mm), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
      }) == 1
    ) yield (p, pp)

  lazy val timeTriggeredTopologySymmetryRelation
      : Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (
      p  <- timeTriggeredPEs;
      pp <- timeTriggeredPEs;
      m  <- hardware.storageElems
      // check if exists one AND ONLY ONE memory element in which the path bandwidths
      // are exactly equal.
      if p == pp || hardware.storageElems.count(mm => {
        hardware.paths.getOrElse((m, p), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths.getOrElse((p, m), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths
            .getOrElse((mm, pp), Seq())
            .map(c => hardware.bandWidthBitPerSec(c, pp))
            .sum +
          hardware.paths.getOrElse((pp, mm), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
      }) == 1
    ) yield (p, pp)

  lazy val roundRobinTopologySymmetryRelation
      : Set[(GenericProcessingModule, GenericProcessingModule)] =
    for (
      p  <- roundRobinPEs;
      pp <- roundRobinPEs;
      m  <- hardware.storageElems
      // check if exists one AND ONLY ONE memory element in which the path bandwidths
      // are exactly equal.
      if p == pp || hardware.storageElems.count(mm => {
        hardware.paths.getOrElse((m, p), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum +
          hardware.paths.getOrElse((p, m), Seq()).map(c => hardware.bandWidthBitPerSec(c, p)).sum ==
          hardware.paths
            .getOrElse((mm, pp), Seq())
            .map(c => hardware.bandWidthBitPerSec(c, pp))
            .sum +
          hardware.paths.getOrElse((pp, mm), Seq()).map(c => hardware.bandWidthBitPerSec(c, pp)).sum
      }) == 1
    ) yield (p, pp)
   */

  lazy val topologySymmetryRelationGraph: SimpleGraph[GenericProcessingModule, DefaultEdge] =
    val graph = SimpleGraph[GenericProcessingModule, DefaultEdge](classOf[DefaultEdge])
    hardware.processingElems.foreach(p => graph.addVertex(p))
    for
      p  <- hardware.processingElems
      pp <- hardware.processingElems
      if p != pp
    // TODO: this check should be a bit more robust... is it always master to slave?
    // can it be in any direction? After this design decision, it becomes better.
    // Currently we assume master to slace
    // TODO: the fact that the bandwith must get with a default value is not safe,
    // this should be removed later
    /*
      if hardware.storageElems.forall(m => {
        hardware.storageElems.exists(mm => {
          hardware.minTraversalTimePerBit(m)(p).flatMap(p2m => {
            hardware.minTraversalTimePerBit(mm)(pp).map(pp2mm => {
              p2m.equals(pp2mm)
            })
          }).orElse(false)
        })
      })
     */
    do graph.addEdge(p, pp)
    graph

  lazy val topologicallySymmetricGroups: Set[Set[GenericProcessingModule]] =
    ConnectivityInspector(topologySymmetryRelationGraph).connectedSets.stream
      .map(g => g.asScala.toSet)
      .collect(Collectors.toSet)
      .asScala
      .toSet

  override val uniqueIdentifier = "SchedulableNetworkedDigHW"
}
