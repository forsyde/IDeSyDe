package idesyde.identification.forsyde.rules

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.forsydeio.ForSyDeDesignModel
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.decision.results.AnalysedGenericProcessingModule
import forsyde.io.java.typed.viewers.decision.Scheduled
import forsyde.io.java.typed.viewers.visualization.GreyBox
import forsyde.io.java.typed.viewers.visualization.Visualizable
import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler
import forsyde.io.java.typed.viewers.decision.MemoryMapped
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
import forsyde.io.java.typed.viewers.nonfunctional.UtilizationBoundedProcessingElem
import spire.math.Rational
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import scala.jdk.CollectionConverters._
import forsyde.io.java.core.VertexProperty
import forsyde.io.java.typed.viewers.decision.platform.runtime.AllocatedSingleSlotSCS
import forsyde.io.java.typed.viewers.decision.platform.runtime.AllocatedSharedSlotSCS
import scala.collection.mutable.Buffer
import forsyde.io.java.typed.viewers.decision.results.AnalyzedActor

trait MixedRules {

  def integratePeriodicWorkloadToPartitionedSharedMultiCore(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[? <: DesignModel] = {
    val model = designModel
      .flatMap(_ match {
        case ForSyDeDesignModel(forSyDeSystemGraph) =>
          Some(forSyDeSystemGraph)
        case _ => None
      })
      .foldRight(ForSyDeSystemGraph())((a, b) => b.merge(a))
    val solveds = decisionModel.flatMap(_ match {
      case dse: PeriodicWorkloadToPartitionedSharedMultiCore => {
        if (
          !dse.processMappings.isEmpty && !dse.processSchedulings.isEmpty && !dse.channelMappings.isEmpty
        )
          Some(dse)
        else None
      }
      case _ => None
    })
    for (solved <- solveds; rebuilt = ForSyDeSystemGraph().merge(model)) yield {
      for (
        (taskId, schedId) <- solved.processSchedulings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = rebuilt
          .vertexSet()
          .stream()
          .filter(v => taskId.contains(v.getIdentifier()))
          .findAny()
          .get();
        sched = rebuilt.queryVertex(schedId).get()
      ) {
        AbstractScheduler
          .safeCast(sched)
          .ifPresent(scheduler => {
            Scheduled.enforce(task).insertSchedulersPort(rebuilt, scheduler)
            GreyBox.enforce(sched).insertContainedPort(rebuilt, Visualizable.enforce(task))
          })
      }
      for (
        (taskId, memId) <- solved.processMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = rebuilt
          .vertexSet()
          .stream()
          .filter(v => taskId.contains(v.getIdentifier()))
          .findAny()
          .get();
        mem = rebuilt.queryVertex(memId).get()
      ) {
        GenericMemoryModule
          .safeCast(mem)
          .ifPresent(memory => MemoryMapped.enforce(task).insertMappingHostsPort(rebuilt, memory))
      }
      for (
        (channelId, memId) <- solved.channelMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        channel = rebuilt
          .vertexSet()
          .stream()
          .filter(v => channelId.contains(v.getIdentifier()))
          .findAny()
          .get();
        mem = rebuilt.queryVertex(memId).get()
      ) {
        GenericMemoryModule
          .safeCast(mem)
          .ifPresent(memory =>
            MemoryMapped.enforce(channel).insertMappingHostsPort(rebuilt, memory)
          )
      }
      ForSyDeDesignModel(rebuilt)
    }
  }

  def integratePeriodicWorkloadToPartitionedSharedMultiCoreFromNothing(
      designModel: Set[DesignModel],
      decisionModel: Set[DecisionModel]
  ): Set[? <: DesignModel] = {
    val model = designModel
      .flatMap(_ match {
        case ForSyDeDesignModel(forSyDeSystemGraph) =>
          Some(forSyDeSystemGraph)
        case _ => None
      })
      .foldRight(ForSyDeSystemGraph())((a, b) => b.merge(a))
    val solveds = decisionModel.flatMap(_ match {
      case dse: PeriodicWorkloadToPartitionedSharedMultiCore => {
        if (
          !dse.processMappings.isEmpty && !dse.processSchedulings.isEmpty && !dse.channelMappings.isEmpty
        )
          Some(dse)
        else None
      }
      case _ => None
    })
    for (solved <- solveds; rebuilt = ForSyDeSystemGraph().merge(model)) yield {
      for (
        (taskId, schedId) <- solved.processSchedulings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task  = rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId));
        sched = rebuilt.queryVertex(schedId).orElse(rebuilt.newVertex(schedId))
      ) {
        AbstractScheduler
          .safeCast(sched)
          .ifPresent(scheduler => {
            Scheduled.enforce(task).insertSchedulersPort(rebuilt, scheduler)
            GreyBox.enforce(sched).insertContainedPort(rebuilt, Visualizable.enforce(task))
          })
      }
      for (
        (taskId, memId) <- solved.processMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId));
        mem  = rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId))
      ) {
        GenericMemoryModule
          .safeCast(mem)
          .ifPresent(memory => MemoryMapped.enforce(task).insertMappingHostsPort(rebuilt, memory))
      }
      for (
        (channelId, memId) <- solved.channelMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        channel = rebuilt.queryVertex(channelId).orElse(rebuilt.newVertex(channelId));
        mem     = rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId))
      ) {
        GenericMemoryModule
          .safeCast(mem)
          .ifPresent(memory =>
            MemoryMapped.enforce(channel).insertMappingHostsPort(rebuilt, memory)
          )
      }
      ForSyDeDesignModel(rebuilt)
    }
  }

  def integrateSDFToTiledMultiCore(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[? <: DesignModel] = {
    val model = designModel
      .flatMap(_ match {
        case ForSyDeDesignModel(forSyDeSystemGraph) =>
          Some(forSyDeSystemGraph)
        case _ => None
      })
      .foldRight(ForSyDeSystemGraph())((a, b) => b.merge(a))
    val solveds = decisionModel.flatMap(_ match {
      case dse: SDFToTiledMultiCore => {
        if (!dse.messageMappings.isEmpty && !dse.processMappings.isEmpty)
          Some(dse)
        else None
      }
      case _ => None
    })
    for (solved <- solveds; rebuilt = ForSyDeSystemGraph().merge(model)) yield {
      // first, we take care of the process mappings
      for (
        (mem, i) <- solved.processMappings.zipWithIndex;
        actorId   = solved.sdfApplications.actorsIdentifiers(i);
        memIdx    = solved.platform.hardware.memories.indexOf(mem);
        proc      = solved.platform.hardware.processors(memIdx);
        scheduler = solved.platform.runtimes.schedulers(memIdx)
      ) {
        rebuilt
          .queryVertex(actorId)
          .ifPresent(actor => {
            rebuilt
              .queryVertex(mem)
              .ifPresent(m => {
                val v = MemoryMapped.enforce(actor)
                v.setMappingHostsPort(
                  rebuilt,
                  java.util.Set.of(GenericMemoryModule.enforce(m))
                )
              })
            rebuilt
              .queryVertex(scheduler)
              .ifPresent(s => {
                val v = Scheduled.enforce(actor)
                v.setSchedulersPort(rebuilt, java.util.Set.of(AbstractScheduler.enforce(s)))
              })
          })
      }
      // now, we take care of the memory mappings
      for (
        (mem, i) <- solved.messageMappings.zipWithIndex;
        channelID = solved.sdfApplications.channelsIdentifiers(i);
        memIdx    = solved.platform.hardware.memories.indexOf(mem)
      ) {
        rebuilt
          .queryVertex(channelID)
          .ifPresent(actor => {
            rebuilt
              .queryVertex(mem)
              .ifPresent(m => {
                val v = MemoryMapped.enforce(actor)
                v.setMappingHostsPort(
                  rebuilt,
                  java.util.Set.of(GenericMemoryModule.enforce(m))
                )
              })
          })
      }
      // now, we put the schedule in each scheduler
      for (
        (list, si) <- solved.schedulerSchedules.zipWithIndex;
        proc      = solved.platform.hardware.processors(si);
        scheduler = solved.platform.runtimes.schedulers(si)
      ) {
        rebuilt
          .queryVertex(scheduler)
          .ifPresent(sched => {
            val scs = AllocatedSingleSlotSCS.enforce(sched)
            scs.setEntries(list.asJava)
          })
      }
      // finally, the channel comm allocations
      var commAllocs = solved.platform.hardware.communicationElementsMaxChannels.map(maxVc =>
        Buffer.fill(maxVc)(Buffer.empty[String])
      )
      for (
        (maxVc, ce) <- solved.platform.hardware.communicationElementsMaxChannels.zipWithIndex;
        (dict, c)   <- solved.messageSlotAllocations.zipWithIndex;
        vc          <- 0 until maxVc;
        commElem = solved.platform.hardware.communicationElems(ce);
        if dict.getOrElse(commElem, Vector.fill(maxVc)(false))(vc);
        cId = solved.sdfApplications.channelsIdentifiers(c)
      ) {
        commAllocs(ce)(vc) += cId
      }
      for ((ce, i) <- solved.platform.hardware.communicationElems.zipWithIndex) {
        rebuilt
          .queryVertex(ce)
          .ifPresent(comm =>
            AllocatedSharedSlotSCS
              .enforce(comm)
              .setEntries(commAllocs(i).map(_.asJava).asJava)
          )
      }
      // add the throughputs for good measure
      for (
        (a, ai) <- solved.sdfApplications.actorsIdentifiers.zipWithIndex;
        th = solved.sdfApplications.minimumActorThroughputs(ai)
      ) {
        rebuilt
          .queryVertex(a)
          .ifPresent(actor => {
            val frac = Rational(th)
            val act  = AnalyzedActor.enforce(actor)
            act.setThroughputInSecsNumerator(frac.numeratorAsLong)
            act.setThroughputInSecsDenominator(frac.denominatorAsLong)
          })
      }
      ForSyDeDesignModel(rebuilt)
    }
  }

  def identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[PeriodicWorkloadToPartitionedSharedMultiCore] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      val app = identified
        .filter(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
        .map(_.asInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
      val plat = identified
        .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
        .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
      // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
      app.flatMap(a =>
        plat.map(p =>
          PeriodicWorkloadToPartitionedSharedMultiCore(
            workload = a,
            platform = p,
            processMappings = Vector.empty,
            processSchedulings = Vector.empty,
            channelMappings = Vector.empty,
            channelSlotAllocations = Map(),
            maxUtilizations = (for (
              pe <- p.hardware.processingElems;
              peVertex = model.queryVertex(pe);
              if peVertex.isPresent() && UtilizationBoundedProcessingElem.conforms(peVertex.get());
              utilVertex = UtilizationBoundedProcessingElem.safeCast(peVertex.get()).get()
            )
              yield pe -> utilVertex.getMaxUtilizationNumerator().toDouble / utilVertex
                .getMaxUtilizationDenominator()
                .toDouble).toMap
          )
        )
      )
    }
  }
}
