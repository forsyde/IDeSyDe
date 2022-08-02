package idesyde.identification.models.platform

import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler
import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.typed.viewers.platform.runtime.FixedPriorityScheduler
import forsyde.io.java.typed.viewers.platform.runtime.TimeTriggeredScheduler
import forsyde.io.java.typed.viewers.platform.runtime.RoundRobinScheduler
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import forsyde.io.java.core.Vertex
import idesyde.identification.IdentificationResult
import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.decision.Allocated
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule

final case class SchedulableTiledDigitalHardware(
    val tiledDigitalHardware: TiledDigitalHardware,
    val schedulers: Array[AbstractScheduler]
) extends ForSyDeDecisionModel {

  def coveredVertexes: Iterable[Vertex] = schedulers.map(_.getViewedVertex())
    ++ tiledDigitalHardware.coveredVertexes

  def isFixedPriority = schedulers.map(FixedPriorityScheduler.conforms(_))
  def isTimeTriggered = schedulers.map(TimeTriggeredScheduler.conforms(_))
  def isRoundRobin    = schedulers.map(RoundRobinScheduler.conforms(_))
  def isStaticCycle   = schedulers.map(StaticCyclicScheduler.conforms(_))

  def fixedPrioritySchedulers =
    schedulers.zipWithIndex
      .filter((s, i) => isFixedPriority(i))
      .map((s, i) => FixedPriorityScheduler.enforce(s))
  def timeTriggeredSchedulers =
    schedulers.zipWithIndex
      .filter((s, i) => isTimeTriggered(i))
      .map((s, i) => TimeTriggeredScheduler.enforce(s))
  def roundRobinSchedulers =
    schedulers.zipWithIndex
      .filter((s, i) => isRoundRobin(i))
      .map((s, i) => RoundRobinScheduler.enforce(s))
  def staticCycleSchedulers =
    schedulers.zipWithIndex
      .filter((s, i) => isStaticCycle(i))
      .map((s, i) => StaticCyclicScheduler.enforce(s))

  def fixedPriorityTiles: Array[Int] = tiledDigitalHardware.tileSet
    .filter(i => isFixedPriority(i))
  def timeTriggeredTiles: Array[Int] = tiledDigitalHardware.tileSet
    .filter(i => isTimeTriggered(i))
  def roundRobinTiles: Array[Int] = tiledDigitalHardware.tileSet
    .filter(i => isRoundRobin(i))
  def staticCyclicTiles: Array[Int] = tiledDigitalHardware.tileSet
    .filter(i => isStaticCycle(i))

  def uniqueIdentifier: String = "SchedulableTiledDigitalHardware"
}

object SchedulableTiledDigitalHardware {

  def identFromAny(
    model: Any,
    identified: Set[DecisionModel]
  ): IdentificationResult[SchedulableTiledDigitalHardware] = 
    model match {
      case m: ForSyDeSystemGraph =>
        val tiledDigitalHardware = identified.find(_.isInstanceOf[TiledDigitalHardware]).map(_.asInstanceOf[TiledDigitalHardware])
        tiledDigitalHardware.map(t => fromForSyDeSystemGraphWithDeps(m, t)).getOrElse(IdentificationResult.unfixedEmpty())
      case _ => IdentificationResult.fixedEmpty()
    }

  def fromForSyDeSystemGraphWithDeps(
    model: ForSyDeSystemGraph,
    tiledDigitalHardware: TiledDigitalHardware
  ): IdentificationResult[SchedulableTiledDigitalHardware] = {
    val schedulers = model.vertexSet.stream
      .filter(AbstractScheduler.conforms(_))
      .map(AbstractScheduler.enforce(_))
      .collect(Collectors.toList())
      .asScala
      .toArray
    // check if all schedulers are allocated to each tile
    val schedulerAllocations: Array[Int] = schedulers.map(scheduler => {
      Allocated
        .safeCast(scheduler)
        .flatMap(allocated => {
          allocated
            .getAllocationHostsPort(model)
            .stream
            .flatMap(host => {
              GenericProcessingModule
                .safeCast(host)
                .stream
                .mapToInt(peHost => {
                  tiledDigitalHardware.processors.indexOf(peHost)
                })
                .boxed
            })
            .findAny
        })
        .orElse(-1)
    })
    if (!tiledDigitalHardware.tileSet.forall(i => schedulerAllocations.contains(i)))
      scribe.debug("Some processing elements are not allocated. Skipping.")
      IdentificationResult.fixedEmpty()
    else
      scribe.debug(
        "found a conforming Schedulable Networked HW Model with: " +
          s"${schedulers.size} schedulers"
      )
      IdentificationResult.fixed(
        SchedulableTiledDigitalHardware(
          tiledDigitalHardware = tiledDigitalHardware,
          schedulers = schedulers
        )
      )
  }

}

