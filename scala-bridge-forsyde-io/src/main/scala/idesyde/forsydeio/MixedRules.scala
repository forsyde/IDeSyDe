package idesyde.forsydeio

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.forsydeio.ForSyDeDesignModel
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.common.SDFToTiledMultiCore
import forsyde.io.core.SystemGraph
import idesyde.common.CommunicatingAndTriggeredReactiveWorkload
import idesyde.common.PartitionedSharedMemoryMultiCore
import idesyde.forsydeio.ForSyDeIdentificationUtils
import spire.math.Rational
import scala.jdk.CollectionConverters._
import scala.collection.mutable.Buffer
import forsyde.io.lib.hierarchy.platform.hardware.GenericMemoryModule
import forsyde.io.lib.hierarchy.platform.runtime.AbstractRuntime
import forsyde.io.lib.hierarchy.ForSyDeHierarchy
import forsyde.io.lib.hierarchy.platform.runtime.SuperLoopRuntime
import idesyde.common.InstrumentedComputationTimes
import scala.collection.mutable
import idesyde.common.PeriodicWorkloadAndSDFServerToMultiCoreOld

trait MixedRules {

  def identInstrumentedComputationTimes(
      designModel: Set[DesignModel],
      decisionModel: Set[DecisionModel]
  ): (Set[InstrumentedComputationTimes], Set[String]) = {
    ForSyDeIdentificationUtils.toForSyDe(designModel) { model =>
      var processes               = mutable.Set[String]()
      var processing_elements     = mutable.Set[String]()
      var best_execution_times    = mutable.Map[String, mutable.Map[String, Long]]()
      var average_execution_times = mutable.Map[String, mutable.Map[String, Long]]()
      var worst_execution_times   = mutable.Map[String, mutable.Map[String, Long]]()
      val scale_factor = model
        .vertexSet()
        .stream()
        .mapToLong(v =>
          ForSyDeHierarchy.GenericProcessingModule
            .tryView(model, v)
            .map(_.operatingFrequencyInHertz())
            .orElse(1L)
        )
        .max()
        .orElse(1L)
      // alll executables of task are instrumented
      model
        .vertexSet()
        .forEach(task =>
          ForSyDeHierarchy.InstrumentedBehaviour
            .tryView(model, task)
            .ifPresent(instrumentedBehaviour => {
              val taskName = instrumentedBehaviour.getIdentifier()
              processes += taskName
              best_execution_times(taskName) = mutable.Map()
              average_execution_times(taskName) = mutable.Map()
              worst_execution_times(taskName) = mutable.Map()
              model
                .vertexSet()
                .forEach(proc =>
                  ForSyDeHierarchy.InstrumentedProcessingModule
                    .tryView(model, proc)
                    .ifPresent(instrumentedProc => {
                      val peName = instrumentedProc.getIdentifier()
                      processing_elements += peName
                      instrumentedBehaviour
                        .computationalRequirements()
                        .values()
                        .stream()
                        .flatMapToLong(needs =>
                          instrumentedProc
                            .modalInstructionsPerCycle()
                            .values()
                            .stream()
                            .filter(ops => ops.keySet().containsAll(needs.keySet()))
                            .mapToLong(ops =>
                              ops
                                .entrySet()
                                .stream()
                                .mapToLong(e =>
                                  (needs.get(e.getKey()).toDouble / e
                                    .getValue()).ceil.toLong * scale_factor / instrumentedProc
                                    .operatingFrequencyInHertz()
                                )
                                .sum()
                            )
                        )
                        .max()
                        .ifPresent(execTime => {
                          best_execution_times(taskName)(peName) = execTime
                          average_execution_times(taskName)(peName) = execTime
                          worst_execution_times(taskName)(peName) = execTime
                        })
                    })
                )
            })
        )
      (
        Set(
          InstrumentedComputationTimes(
            processes.toSet,
            processing_elements.toSet,
            best_execution_times.map(_ -> _.toMap).toMap,
            average_execution_times.map(_ -> _.toMap).toMap,
            worst_execution_times.map(_ -> _.toMap).toMap,
            scale_factor
          )
        ),
        Set()
      )
    }
  }

  def integratePeriodicWorkloadToPartitionedSharedMultiCore(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[ForSyDeDesignModel] = {
      // .flatMap(_ match {
      //   case ForSyDeDesignModel(forSyDeSystemGraph) =>
      //     Some(forSyDeSystemGraph)
      //   case _ => None
      // })
      // .foldRight(SystemGraph())((a, b) => b.merge(a))
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
    for (solved <- solveds; rebuilt = SystemGraph()) yield {
      for (
        (taskId, schedId) <- solved.processSchedulings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = ForSyDeHierarchy.Scheduled
          .enforce(rebuilt, rebuilt.newVertex(taskId));
        sched = ForSyDeHierarchy.AbstractRuntime
          .enforce(rebuilt, rebuilt.newVertex(schedId))
      ) {
        task.runtimeHost(sched)
        ForSyDeHierarchy.GreyBox
          .enforce(sched)
          .addContained(ForSyDeHierarchy.Visualizable.enforce(task))
      }
      for (
        (taskId, memId) <- solved.processMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = ForSyDeHierarchy.MemoryMapped
          .enforce(rebuilt, rebuilt.newVertex(taskId));
        mem = ForSyDeHierarchy.GenericMemoryModule
          .enforce(rebuilt, rebuilt.newVertex(memId))
      ) {
        task.mappingHost(mem)
      }
      for (
        (channelId, memId) <- solved.channelMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        channel = ForSyDeHierarchy.MemoryMapped
          .enforce(rebuilt, rebuilt.newVertex(channelId));
        mem = ForSyDeHierarchy.GenericMemoryModule
          .enforce(rebuilt, rebuilt.newVertex(memId))
      ) {
        channel.mappingHost(mem)
        ForSyDeHierarchy.GreyBox.enforce(mem).addContained(ForSyDeHierarchy.Visualizable.enforce(channel))
      }
      ForSyDeDesignModel(rebuilt)
    }
  }

  def integratePeriodicWorkloadToPartitionedSharedMultiCoreFromNothing(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[ForSyDeDesignModel] = {
    val model = designModel
      .flatMap(_ match {
        case ForSyDeDesignModel(forSyDeSystemGraph) =>
          Some(forSyDeSystemGraph)
        case _ => None
      })
      .foldRight(SystemGraph())((a, b) => b.merge(a))
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
    if (model.vertexSet().isEmpty()) {
      for (solved <- solveds; rebuilt = SystemGraph().merge(model)) yield {
        for (
          (taskId, schedId) <- solved.processSchedulings;
          // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
          // TODO: fix it to be stable later
          task = ForSyDeHierarchy.Scheduled
            .enforce(rebuilt, rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId)));
          sched = ForSyDeHierarchy.AbstractRuntime
            .enforce(rebuilt, rebuilt.queryVertex(schedId).orElse(rebuilt.newVertex(schedId)))
        ) {
          task.runtimeHost(sched)
          ForSyDeHierarchy.GreyBox
            .enforce(sched)
            .addContained(ForSyDeHierarchy.Visualizable.enforce(task))
        }
        for (
          (taskId, memId) <- solved.processMappings;
          // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
          // TODO: fix it to be stable later
          task = ForSyDeHierarchy.MemoryMapped
            .enforce(rebuilt, rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId)));
          mem = ForSyDeHierarchy.GenericMemoryModule
            .enforce(rebuilt, rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId)))
        ) {
          task.mappingHost(mem)
        }
        for (
          (channelId, memId) <- solved.channelMappings;
          // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
          // TODO: fix it to be stable later
          channel = ForSyDeHierarchy.MemoryMapped
            .enforce(rebuilt, rebuilt.queryVertex(channelId).orElse(rebuilt.newVertex(channelId)));
          mem = ForSyDeHierarchy.GenericMemoryModule
            .enforce(rebuilt, rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId)))
        ) {
          channel.mappingHost(mem)
        }
        ForSyDeDesignModel(rebuilt)
      }
    } else Set()
  }

  def integratePeriodicWorkloadAndSDFServerToMultiCoreOld(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[ForSyDeDesignModel] = {
    val solveds = decisionModel.flatMap(_ match {
      case dse: PeriodicWorkloadAndSDFServerToMultiCoreOld => {
        if (
          !dse.processesMappings.isEmpty && !dse.processesMappings.isEmpty && !dse.messagesMappings.isEmpty
        )
          Some(dse)
        else None
      }
      case _ => None
    })
    for (solved <- solveds; rebuilt = SystemGraph()) yield {
      val priorities = solved.tasksAndSDFs.workload.prioritiesRateMonotonic
      for (
        (taskId, schedId) <- solved.processesSchedulings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = ForSyDeHierarchy.Scheduled
          .enforce(rebuilt, rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId)));
        sched = ForSyDeHierarchy.AbstractRuntime
          .enforce(rebuilt, rebuilt.queryVertex(schedId).orElse(rebuilt.newVertex(schedId)))
      ) {
        task.runtimeHost(sched)
        ForSyDeHierarchy.GreyBox
          .enforce(sched)
          .addContained(ForSyDeHierarchy.Visualizable.enforce(task))
        val taskIdx = solved.tasksAndSDFs.workload.tasks.indexOf(taskId)
        if (taskIdx > -1) {
          ForSyDeHierarchy.FixedPriorityScheduledRuntime.enforce(sched).priorityAssignments().put(
            taskId,
            priorities(taskIdx)
          )
        }
      }
      for (
        (taskId, memId) <- solved.processesMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = ForSyDeHierarchy.MemoryMapped
          .enforce(rebuilt, rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId)));
        mem = ForSyDeHierarchy.GenericMemoryModule
          .enforce(rebuilt, rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId)))
      ) {
        task.mappingHost(mem)
      }
      for (
        (channelId, memId) <- solved.messagesMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        channel = ForSyDeHierarchy.MemoryMapped
          .enforce(rebuilt, rebuilt.queryVertex(channelId).orElse(rebuilt.newVertex(channelId)));
        mem = ForSyDeHierarchy.GenericMemoryModule
          .enforce(rebuilt, rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId)))
      ) {
        channel.mappingHost(mem)
        ForSyDeHierarchy.GreyBox
          .enforce(mem)
          .addContained(ForSyDeHierarchy.Visualizable.enforce(channel))
      }      
      // now, we put the schedule in each scheduler
      for (
        (list, si) <- solved.sdfOrderBasedSchedules.zipWithIndex;
        proc      = solved.platform.hardware.processingElems(si);
        scheduler = solved.platform.runtimes.schedulers(si)
      ) {
        val scs = ForSyDeHierarchy.SuperLoopRuntime.enforce(
          rebuilt,
          rebuilt.newVertex(scheduler)
        )
        scs.superLoopEntries(list.asJava)
      }
      // finally, the channel comm allocations
      var commAllocs = solved.platform.hardware.communicationElementsMaxChannels.map(maxVc =>
        Buffer.fill(maxVc)(Buffer.empty[String])
      )
      for (
        (maxVc, ce) <- solved.platform.hardware.communicationElementsMaxChannels.zipWithIndex;
        (c, dict)   <- solved.messageSlotAllocations;
        vc          <- 0 until maxVc;
        commElem = solved.platform.hardware.communicationElems(ce);
        if dict.getOrElse(commElem, Vector.fill(maxVc)(false))(vc)
      ) {
        commAllocs(ce)(vc) += c
      }
      for ((ce, i) <- solved.platform.hardware.communicationElems.zipWithIndex) {
        val comm = ForSyDeHierarchy.ConcurrentSlotsReserved.enforce(
          rebuilt,
          rebuilt.newVertex(ce)
        )
        comm.slotReservations(commAllocs(i).map(_.asJava).asJava)
      }
      // add the throughputs for good measure
      for (
        (a, ai) <- solved.tasksAndSDFs.sdfApplications.actorsIdentifiers.zipWithIndex;
        th = solved.tasksAndSDFs.sdfApplications.minimumActorThroughputs(ai)
      ) {
        val act = ForSyDeHierarchy.AnalyzedActor.enforce(
          rebuilt,
          rebuilt.newVertex(a)
        )
        val frac = Rational(th)
        act.setThroughputInSecsNumerator(frac.numeratorAsLong)
        act.setThroughputInSecsDenominator(frac.denominatorAsLong)
      }
      // and the maximum channel sizes
      for (
        (c, ci) <- solved.tasksAndSDFs.sdfApplications.channelsIdentifiers.zipWithIndex;
        maxTokens = solved.tasksAndSDFs.sdfApplications.sdfPessimisticTokensPerChannel(ci)
      ) {
        val channelVec = rebuilt.newVertex(c)
        val bounded    = ForSyDeHierarchy.BoundedBufferLike.enforce(rebuilt, channelVec)
        bounded.elementSizeInBits(solved.tasksAndSDFs.sdfApplications.channelTokenSizes(ci))
        bounded.maxElements(maxTokens)
      }
      ForSyDeDesignModel(rebuilt)
    }
  }

  def integrateSDFToTiledMultiCore(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[ForSyDeDesignModel] = {
    val solveds = decisionModel.flatMap(_ match {
      case dse: SDFToTiledMultiCore => {
        if (!dse.messageMappings.isEmpty && !dse.processMappings.isEmpty)
          Some(dse)
        else None
      }
      case _ => None
    })
    for (solved <- solveds; rebuilt = SystemGraph()) yield {
      // first, we take care of the process mappings
      for (
        (mem, i) <- solved.processMappings.zipWithIndex;
        actorId   = solved.sdfApplications.actorsIdentifiers(i);
        memIdx    = solved.platform.hardware.memories.indexOf(mem);
        proc      = solved.platform.hardware.processors(memIdx);
        scheduler = solved.platform.runtimes.schedulers(memIdx)
      ) {
        val v =
          ForSyDeHierarchy.MemoryMapped.enforce(
            rebuilt,
            rebuilt.newVertex(actorId)
          )
        val m =
          ForSyDeHierarchy.GenericMemoryModule.enforce(
            rebuilt,
            rebuilt.newVertex(mem)
          )
        v.mappingHost(
          m
        )
        val s = ForSyDeHierarchy.AbstractRuntime.enforce(
          rebuilt,
          rebuilt.newVertex(scheduler)
        )
        ForSyDeHierarchy.Scheduled
          .enforce(v)
          .runtimeHost(s)
        ForSyDeHierarchy.GreyBox.enforce(s).addContained(ForSyDeHierarchy.Visualizable.enforce(v))
      }
      // now, we take care of the memory mappings
      for (
        (mem, i) <- solved.messageMappings.zipWithIndex;
        channelID = solved.sdfApplications.channelsIdentifiers(i);
        memIdx    = solved.platform.hardware.memories.indexOf(mem)
      ) {
        val v =
          ForSyDeHierarchy.MemoryMapped.enforce(
            rebuilt,
            rebuilt.newVertex(channelID)
          )
        val m =
          ForSyDeHierarchy.GenericMemoryModule.enforce(
            rebuilt,
            rebuilt.newVertex(mem)
          )
        v.mappingHost(m)
        ForSyDeHierarchy.GreyBox.enforce(m).addContained(ForSyDeHierarchy.Visualizable.enforce(v))
      }
      // now, we put the schedule in each scheduler
      for (
        (list, si) <- solved.schedulerSchedules.zipWithIndex;
        proc      = solved.platform.hardware.processors(si);
        scheduler = solved.platform.runtimes.schedulers(si)
      ) {
        val scs = ForSyDeHierarchy.SuperLoopRuntime.enforce(
          rebuilt,
          rebuilt.newVertex(scheduler)
        )
        scs.superLoopEntries(list.asJava)
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
        val comm = ForSyDeHierarchy.ConcurrentSlotsReserved.enforce(
          rebuilt,
          rebuilt.newVertex(ce)
        )
        comm.slotReservations(commAllocs(i).map(_.asJava).asJava)
      }
      // add the throughputs for good measure
      for (
        (a, ai) <- solved.sdfApplications.actorsIdentifiers.zipWithIndex;
        th = solved.sdfApplications.minimumActorThroughputs(ai)
      ) {
        val act = ForSyDeHierarchy.AnalyzedActor.enforce(
          rebuilt,
          rebuilt.newVertex(a)
        )
        val frac = Rational(th)
        act.setThroughputInSecsNumerator(frac.numeratorAsLong)
        act.setThroughputInSecsDenominator(frac.denominatorAsLong)
      }
      // and the maximum channel sizes
      for (
        (c, ci) <- solved.sdfApplications.channelsIdentifiers.zipWithIndex;
        maxTokens = solved.sdfApplications.sdfPessimisticTokensPerChannel(ci)
      ) {
        val channelVec = rebuilt.newVertex(c)
        val bounded    = ForSyDeHierarchy.BoundedBufferLike.enforce(rebuilt, channelVec)
        bounded.elementSizeInBits(solved.sdfApplications.channelTokenSizes(ci))
        bounded.maxElements(maxTokens)
      }
      ForSyDeDesignModel(rebuilt)
    }
  }

  def identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[PeriodicWorkloadToPartitionedSharedMultiCore], Set[String]) = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      val app = identified
        .filter(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
        .map(_.asInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
      val plat = identified
        .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
        .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
      // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
      (
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
                if peVertex.isPresent() && ForSyDeHierarchy.UtilizationBound
                  .tryView(model, peVertex.get())
                  .isPresent();
                utilVertex = ForSyDeHierarchy.UtilizationBound.tryView(model, peVertex.get()).get()
              )
                yield pe -> utilVertex.maxUtilization().toDouble).toMap
            )
          )
        ),
        Set()
      )
    }
  }
}
