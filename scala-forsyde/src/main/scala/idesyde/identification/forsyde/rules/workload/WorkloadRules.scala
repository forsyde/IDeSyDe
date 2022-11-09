package idesyde.identification.forsyde.rules.workload

import scala.jdk.StreamConverters._
import scala.jdk.CollectionConverters._

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.utils.Logger
import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
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

object WorkloadRules {

  def identPeriodicDependentWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Option[CommunicatingExtendedDependenciesPeriodicWorkload] = {
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
        return Option.empty
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
      val connectivityInspector = ConnectivityInspector(stimulusGraph)
      val allTasksAreStimulated = tasks.forall(task =>
        periodicStimulus.exists(stim =>
          connectivityInspector.pathExists(stim.getViewedVertex(), task.getViewedVertex())
        )
      )
      logger.debug(s"Are all tasks reachable by a periodic stimulus? ${allTasksAreStimulated}")
      if (!allTasksAreStimulated) return Option.empty
      // now building all the periodic workload elements ditto
      var propagatedEvents = mutable.Map[String, Set[(Rational, Rational, Rational)]]()
      val processes        = Buffer[(String, Rational, Rational, Rational)]()
      val stimulusIter     = TopologicalOrderIterator(stimulusGraph)
      while (stimulusIter.hasNext) {
        val next = stimulusIter.next
        // gather all incomin stimulus
        val incomingEvents = stimulusGraph
          .incomingEdgesOf(next)
          .stream
          .map(e => propagatedEvents(stimulusGraph.getEdgeSource(e).getIdentifier()))
          .reduce((s1, s2) => s1 | s2)
          .orElse(Set())
        val events = Stimulatable
          .safeCast(next)
          .filter(stimulatable => stimulatable.getHasORSemantics())
          .map(stimulatable => {
            val maxP = incomingEvents.map((p, o, d) => p).max
            val minO = incomingEvents.map((p, o, d) => o).min
            val minD = incomingEvents.map((p, o, d) => d).min
            Set((maxP, minO, minD))
          })
          .orElse(incomingEvents)
        // decide what to do next based on the vertex type and its event merge semantics
        PeriodicStimulus
          .safeCast(next)
          .ifPresent(periodicStimulus => {
            propagatedEvents(next.getIdentifier()) = Set(
              (
                Rational(
                  periodicStimulus.getPeriodNumerator,
                  periodicStimulus.getPeriodDenominator
                ), // period
                Rational(
                  periodicStimulus.getOffsetNumerator,
                  periodicStimulus.getOffsetDenominator
                ), // offset
                Rational(
                  periodicStimulus.getPeriodNumerator,
                  periodicStimulus.getPeriodDenominator
                ) // rel. deadline
              )
            )
          })
        Task
          .safeCast(next)
          .ifPresent(task => {
            propagatedEvents(next.getIdentifier()) = events
            processes ++= events.map((p, o, d) => (next.getIdentifier(), p, o, d))
          })
        Upsample
          .safeCast(next)
          .ifPresent(upsample => {
            propagatedEvents(next.getIdentifier()) = events.map(e => {
              (
                e._1 / Rational(upsample.getRepetitivePredecessorHolds),
                e._2 + (e._1 / Rational(upsample.getInitialPredecessorHolds)),
                e._3 / Rational(upsample.getRepetitivePredecessorHolds)
              )
            })
          })
        Downsample
          .safeCast(next)
          .ifPresent(downsample => {
            propagatedEvents(next.getIdentifier()) = events.map(e => {
              (
                e._1 * (Rational(downsample.getRepetitivePredecessorSkips)),
                e._2 + (e._1 * (Rational(downsample.getInitialPredecessorSkips))),
                e._3 * (Rational(downsample.getRepetitivePredecessorSkips))
              )
            })
          })
      }
      // first consider task-to-task connections
      var affineControlGraphEdges = Buffer[(Int, Int, Int, Int, Int, Int)]()
      for (srcTask <- tasks) {
        model
          .outgoingEdgesOf(srcTask.getViewedVertex())
          .stream()
          .map(edge => model.getEdgeTarget(edge))
          .flatMap(dst => Task.safeCast(dst).stream())
          .forEach(dstTask => {
            if (dstTask.getHasORSemantics()) {
              for (
                (srcEvent, i) <- processes.zipWithIndex
                  .filter((p, i) => p._1 == srcTask.getIdentifier());
                (dstEvent, j) <- processes.zipWithIndex
                  .filter((p, j) => p._1 == dstTask.getIdentifier())
                if srcEvent._2 == dstEvent._2
              ) {
                affineControlGraphEdges :+= (i, j, 1, 0, 1, 0)
              }
            } else {
              for (
                (srcEvent, i) <- processes.zipWithIndex
                  .filter((p, i) => p._1 == srcTask.getIdentifier());
                (dstEvent, j) <- processes.zipWithIndex
                  .filter((p, j) => p._1 == dstTask.getIdentifier())
              ) {
                affineControlGraphEdges :+= (i, j, (dstEvent._2 / srcEvent._2).ceil.toInt, 0, 1, 0)
              }
            }
          })
      }
      // now consider upsampling connections
      for (upsample <- upsamples) {
        model
          .outgoingEdgesOf(upsample.getViewedVertex())
          .stream()
          .map(edge => model.getEdgeTarget(edge))
          .flatMap(dst => Task.safeCast(dst).stream())
          .forEach(dstTask => {
            model
              .incomingEdgesOf(upsample.getViewedVertex())
              .stream()
              .map(edge => model.getEdgeSource(edge))
              .flatMap(src => Task.safeCast(src).stream())
              .forEach(srcTask => {
                if (dstTask.getHasORSemantics) {
                  for (
                    (srcEvent, i) <- processes.zipWithIndex
                      .filter((p, i) => p._1 == srcTask.getIdentifier());
                    (dstEvent, j) <- processes.zipWithIndex
                      .filter((p, j) => p._1 == dstTask.getIdentifier())
                    if dstEvent._2 * Rational(
                      upsample.getRepetitivePredecessorHolds
                    ) == srcEvent._2 &&
                      dstEvent._3 - (dstEvent._2 * Rational(
                        upsample.getInitialPredecessorHolds
                      )) == srcEvent._3
                  ) {
                    affineControlGraphEdges :+= (i, j, upsample.getRepetitivePredecessorHolds.toInt, upsample.getInitialPredecessorHolds.toInt, 1, 0)
                  }
                } else {
                  for (
                    (srcEvent, i) <- processes.zipWithIndex
                      .filter((p, i) => p._1 == srcTask.getIdentifier());
                    (dstEvent, j) <- processes.zipWithIndex
                      .filter((p, j) => p._1 == dstTask.getIdentifier());
                    pRatio = (dstEvent._2 / srcEvent._2);
                    offset = ((dstEvent._3 - srcEvent._3) / srcEvent._2)
                  ) {
                    affineControlGraphEdges :+= (i, j, pRatio.ceil.toInt, offset.ceil.toInt, 1, 0)
                  }
                }
              })
          })
      }
      // now finally consider downsample connections
      for (downsample <- downsamples) {
        model
          .outgoingEdgesOf(downsample.getViewedVertex())
          .stream()
          .map(edge => model.getEdgeTarget(edge))
          .flatMap(dst => Task.safeCast(dst).stream())
          .forEach(dstTask => {
            model
              .incomingEdgesOf(downsample.getViewedVertex())
              .stream()
              .map(edge => model.getEdgeSource(edge))
              .flatMap(src => Task.safeCast(src).stream())
              .forEach(srcTask => {
                if (dstTask.getHasORSemantics) {
                  for (
                    (srcEvent, i) <- processes.zipWithIndex
                      .filter((p, i) => p._1 == srcTask.getIdentifier());
                    (dstEvent, j) <- processes.zipWithIndex
                      .filter((p, j) => p._1 == dstTask.getIdentifier())
                    if dstEvent._2 / Rational(
                      downsample.getRepetitivePredecessorSkips
                    ) == srcEvent._2 &&
                      dstEvent._3 + (dstEvent._2 / Rational(
                        downsample.getInitialPredecessorSkips
                      )) == srcEvent._3
                  )
                    affineControlGraphEdges :+= (
                      i,
                      j,
                      1,
                      0,
                      downsample.getRepetitivePredecessorSkips.toInt,
                      downsample.getInitialPredecessorSkips.toInt
                    )
                } else {
                  for (
                    (srcEvent, i) <- processes.zipWithIndex
                      .filter((p, i) => p._1 == srcTask.getIdentifier());
                    (dstEvent, j) <- processes.zipWithIndex
                      .filter((p, j) => p._1 == dstTask.getIdentifier());
                    pRatio = (srcEvent._2 / dstEvent._2).toDouble.ceil.toInt;
                    offset = ((dstEvent._3 - srcEvent._3) / dstEvent._2).toDouble.toInt
                  ) affineControlGraphEdges :+= (i, j, 1, 0, pRatio, offset)
                }
              })
          })
      }
      val additionalCovered =
        (tasks ++ dataBlocks ++ upsamples ++ downsamples ++ periodicStimulus).toSet
      val additionalRelations = additionalCovered.flatMap(src =>
        additionalCovered
          .filter(dst => model.hasConnection(src, dst))
          .map(dst => (src.getIdentifier(), dst.getIdentifier()))
      )
      Option(
        CommunicatingExtendedDependenciesPeriodicWorkload(
          processes.map((n, p, o, d) => n).toArray,
          processes.map((n, p, o, d) => p).toArray,
          processes.map((n, p, o, d) => o).toArray,
          processes.map((n, p, o, d) => d).toArray,
          processes
            .map((n, _, _, _) => tasks.find(_.getIdentifier() == n).get)
            .map(InstrumentedExecutable.safeCast(_).map(_.getSizeInBits()).orElse(0L).toLong)
            .toArray,
          processes
            .map((n, _, _, _) =>
              tasks
                .find(_.getIdentifier() == n)
                .map(t => taskComputationNeeds(t, model))
                .get
            )
            .toArray,
          dataBlocks.map(_.getIdentifier()).toArray,
          dataBlocks.map(_.getMaxSizeInBits().toLong).toArray,
          processes
            .map((n, _, _, _) =>
              dataBlocks
                .map(db =>
                  communicationGraphEdges
                    .find((src, dst, _) => dst == n && src == db.getIdentifier())
                    .map((_, _, d) => d.toLong)
                    .getOrElse(0L)
                )
                .toArray
            )
            .toArray,
          processes
            .map((n, _, _, _) =>
              dataBlocks
                .map(db =>
                  communicationGraphEdges
                    .find((src, dst, _) => src == n && dst == db.getIdentifier())
                    .map((_, _, d) => d.toLong)
                    .getOrElse(0L)
                )
                .toArray
            )
            .toArray,
          affineControlGraphEdges.map(_._1).toArray,
          affineControlGraphEdges.map(_._2).toArray,
          affineControlGraphEdges.map(_._3).toArray,
          affineControlGraphEdges.map(_._4).toArray,
          affineControlGraphEdges.map(_._5).toArray,
          affineControlGraphEdges.map(_._6).toArray,
          additionalCovered.map(_.getIdentifier()).toSet,
          additionalRelations
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
