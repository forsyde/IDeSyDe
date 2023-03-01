package idesyde.identification.forsyde.rules

import scala.jdk.StreamConverters._
import scala.jdk.CollectionConverters._

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.utils.Logger
import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.impl.DataBlock
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.execution.Upsample
import forsyde.io.java.typed.viewers.execution.Downsample
import forsyde.io.java.typed.viewers.execution.CommunicatingTask
import scala.collection.mutable.Buffer
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.alg.connectivity.ConnectivityInspector
import spire.math._
import scala.collection.mutable
import org.jgrapht.traverse.TopologicalOrderIterator
import forsyde.io.java.typed.viewers.execution.Stimulatable
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.execution.LoopingTask
import java.util.stream.Collectors

object WorkloadRules {

  def identPeriodicDependentWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[CommunicatingAndTriggeredReactiveWorkload] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var tasks                   = Buffer[Task]()
      var dataBlocks              = Buffer[DataBlock]()
      var periodicStimulus        = Buffer[PeriodicStimulus]()
      var upsamples               = Buffer[Upsample]()
      var downsamples             = Buffer[Downsample]()
      var communicationGraphEdges = Buffer[(String, String, Long)]()
      model.vertexSet.forEach(v =>
        Task
          .safeCast(v)
          .ifPresent(task => tasks :+= task)
        DataBlock
          .safeCast(v)
          .ifPresent(channel => dataBlocks :+= channel)
        PeriodicStimulus
          .safeCast(v)
          .ifPresent(stim => periodicStimulus :+= stim)
        Upsample
          .safeCast(v)
          .ifPresent(upsample => {
            upsamples :+= upsample
          })
        Downsample
          .safeCast(v)
          .ifPresent(downsample => {
            downsamples :+= downsample
          })
      )
      // nothing can be done if there are no tasks
      // so we terminate early to avoid undefined analysis results
      logger.debug(s"Num of tasks found in model: ${tasks.size}")
      if (tasks.isEmpty)
        return Set.empty
      // now take a look which of the relevant vertexes are connected
      // taskStimulusGraph.vertexSet.forEach(src =>
      //   taskStimulusGraph.vertexSet.forEach(dst =>
      //     if (model.hasConnection(src, dst)) then taskStimulusGraph.addEdge(src, dst)
      //   )
      // )
      // do the task communication calculations
      for (
        task      <- tasks;
        dataBlock <- dataBlocks
      ) {
        CommunicatingTask
          .safeCast(task)
          .ifPresent(commTask => {
            if (model.hasConnection(commTask, dataBlock)) {
              val dataWritten = model
                .getAllEdges(commTask.getViewedVertex, dataBlock.getViewedVertex)
                .stream
                .mapToLong(e =>
                  e.getSourcePort
                    .map(outPort =>
                      commTask.getPortDataWrittenSize
                        .getOrDefault(outPort, dataBlock.getMaxSizeInBits)
                    )
                    .orElse(0L)
                )
                .sum
              communicationGraphEdges :+= (commTask.getIdentifier(), dataBlock
                .getIdentifier(), dataWritten)
            } else if (model.hasConnection(dataBlock, commTask)) {
              val dataRead = model
                .getAllEdges(dataBlock.getViewedVertex, commTask.getViewedVertex)
                .stream
                .mapToLong(e =>
                  e.getTargetPort
                    .map(inPort =>
                      commTask.getPortDataReadSize.getOrDefault(inPort, dataBlock.getMaxSizeInBits)
                    )
                    .orElse(0L)
                )
                .sum
              communicationGraphEdges :+= (dataBlock.getIdentifier(), commTask
                .getIdentifier(), dataRead)
            }
          })
      }
      // check if every task has a periodic stimulus
      val stimulusGraph =
        AsSubgraph(
          model,
          (tasks ++ periodicStimulus ++ upsamples ++ downsamples)
            .map(_.getViewedVertex())
            .toSet
            .asJava
        )
      val dataGraph = AsSubgraph(model, (tasks ++ dataBlocks).map(_.getViewedVertex()).toSet.asJava)
      val connectivityInspector = ConnectivityInspector(stimulusGraph)
      val allTasksAreStimulated = tasks.forall(task =>
        periodicStimulus.exists(stim =>
          connectivityInspector.pathExists(stim.getViewedVertex(), task.getViewedVertex())
        )
      )
      logger.debug(s"Are all tasks reachable by a periodic stimulus? ${allTasksAreStimulated}")
      if (!allTasksAreStimulated) return Set.empty
      Set(
        CommunicatingAndTriggeredReactiveWorkload(
          tasks.map(_.getIdentifier()).toVector,
          tasks
            .map(
              InstrumentedExecutable.safeCast(_).map(_.getSizeInBits().toLong).orElse(0L)
            )
            .toVector,
          tasks.map(t => taskComputationNeeds(t, model)).toVector,
          dataBlocks.map(_.getIdentifier()).toVector,
          dataBlocks.map(_.getMaxSizeInBits().toLong).toVector,
          communicationGraphEdges.toSet,
          periodicStimulus.map(_.getIdentifier()).toVector,
          periodicStimulus.map(_.getPeriodNumerator().toLong).toVector,
          periodicStimulus.map(_.getPeriodDenominator().toLong).toVector,
          periodicStimulus.map(_.getOffsetNumerator().toLong).toVector,
          periodicStimulus.map(_.getOffsetDenominator().toLong).toVector,
          upsamples.map(_.getIdentifier()).toVector,
          upsamples.map(_.getRepetitivePredecessorHolds().toLong).toVector,
          upsamples.map(_.getInitialPredecessorHolds().toLong).toVector,
          downsamples.map(_.getIdentifier()).toVector,
          downsamples.map(_.getRepetitivePredecessorSkips().toLong).toVector,
          downsamples.map(_.getInitialPredecessorSkips().toLong).toVector,
          stimulusGraph
            .edgeSet()
            .stream()
            .map(e =>
              (
                stimulusGraph.getEdgeSource(e).getIdentifier(),
                stimulusGraph.getEdgeTarget(e).getIdentifier()
              )
            )
            .collect(Collectors.toList())
            .asScala
            .toVector,
          tasks.filter(_.getHasORSemantics()).map(_.getIdentifier()).toSet ++ upsamples
            .filter(_.getHasORSemantics())
            .map(_.getIdentifier())
            .toSet ++ downsamples
            .filter(_.getHasORSemantics())
            .map(_.getIdentifier())
            .toSet
        )
      )
    }
  }

  private def taskComputationNeeds(
      task: Task,
      model: ForSyDeSystemGraph
  ): Map[String, Map[String, Long]] = {
    var maps = mutable.Map[String, mutable.Map[String, Long]]()
    LoopingTask
      .safeCast(task)
      .ifPresent(lt => {
        java.util.stream.Stream
          .concat(lt.getInitSequencePort(model).stream(), lt.getLoopSequencePort(model).stream())
          .forEach(exec => {
            InstrumentedExecutable
              .safeCast(exec)
              .ifPresent(iexec => {
                iexec
                  .getOperationRequirements()
                  .forEach((opName, opReqs) => {
                    if (!maps.contains(opName)) maps(opName) = mutable.Map[String, Long]()
                    opReqs.forEach((opKey, opVal) => {
                      if (!maps(opName).contains(opKey)) maps(opName)(opKey) = 0L
                      maps(opName)(opKey) += opVal
                    })
                  })
              })
          })
      })
    InstrumentedExecutable
      .safeCast(task)
      .ifPresent(itask => {
        itask
          .getOperationRequirements()
          .forEach((opName, opReqs) => {
            if (!maps.contains(opName)) maps(opName) = mutable.Map[String, Long]()
            opReqs.forEach((opKey, opVal) => {
              maps(opName)(opKey) += opVal
            })
          })
      })
    maps.map((k, v) => k -> v.toMap).toMap
  }
}
