package idesyde.identification.forsyde.rules.workload

import scala.jdk.StreamConverters._
import scala.jdk.CollectionConverters._

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.utils.Logger
import idesyde.identification.common.models.workload.PeriodicDependentWorkload
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

object WorkloadRules {

  def identPeriodicDependentWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Option[PeriodicDependentWorkload] = {
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
      var createdTasks            = mutable.Map[String, Set[(Rational, Rational, Rational)]]()
      var affineControlGraphEdges = Buffer[(String, String, Set[(Int, Int, Int, Int)])]()
      val stimulusIter            = TopologicalOrderIterator(stimulusGraph)
      while (stimulusIter.hasNext) {
        val next = stimulusIter.next
        // gather all incomin stimulus
        val incomingEvents = stimulusGraph
          .incomingEdgesOf(next)
          .stream
          .map(e => createdTasks(stimulusGraph.getEdgeSource(e).getIdentifier()))
          .reduce((s1, s2) => s1 | s2)
          .orElse(Set())
        PeriodicStimulus
          .safeCast(next)
          .ifPresent(periodicStimulus => {
            createdTasks(next.getIdentifier()) = Set(
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
        // // decide what to do next based on the vertex type and its event merge semantics
        // stimulusGraphTuples(next) = Stimulatable
        //   .safeCast(next)
        //   .filter(s => !s.getHasORSemantics)
        //   .map(s => {
        //     Array(incomingEvents.maxBy((p, o, d) => p))
        //   })
        //   .orElse(incomingEvents)
        // // change the stimulus in the vertex depending on traits it has
        // next match {
        //   case perStim: PeriodicStimulus =>
        //     stimulusGraphTuples(next) = Array(
        //       (
        //         Rational(perStim.getPeriodNumerator, perStim.getPeriodDenominator), // period
        //         Rational(perStim.getOffsetNumerator, perStim.getOffsetDenominator), // offset
        //         Rational(perStim.getPeriodNumerator, perStim.getPeriodDenominator)  // rel. deadline
        //       )
        //     )
        //   case task: Task =>
        //     val stimulus = stimulusGraphTuples(next)
        //     // do not forget to check if the periodic task has some sort
        //     // of tighter deadline
        //     createdPerTasks = createdPerTasks.appendAll(
        //       stimulus.map(s =>
        //         ConstrainedTask
        //           .safeCast(task)
        //           .map(t =>
        //             val deadline =
        //               Rational(t.getRelativeDeadlineNumerator, t.getRelativeDeadlineDenominator)
        //             (
        //               task,
        //               s._1,
        //               s._2,
        //               if (deadline.compareTo(s._3) >= 0) then s._3 else deadline
        //             )
        //           )
        //           .orElse((task, s._1, s._2, s._3))
        //       )
        //     )
        //   case upsample: Upsample =>
        //     stimulusGraphTuples(next) = stimulusGraphTuples(next).map(e => {
        //       (
        //         e._1 / Rational(upsample.getRepetitivePredecessorHolds),
        //         e._2 + (e._1 / Rational(upsample.getInitialPredecessorHolds)),
        //         e._3 / Rational(upsample.getRepetitivePredecessorHolds)
        //       )
        //     })
        //   case downsample: Downsample =>
        //     stimulusGraphTuples(next) = stimulusGraphTuples(next).map(e => {
        //       (
        //         e._1 * (Rational(downsample.getRepetitivePredecessorSkips)),
        //         e._2 + (e._1 * (Rational(downsample.getInitialPredecessorSkips))),
        //         e._3 * (Rational(downsample.getRepetitivePredecessorSkips))
        //       )
        //     })
        // }
      }
      // val affineRelationsGraphBuilder =
      //   SimpleDirectedGraph.createBuilder[Int, (Int, Int, Int, Int)](() => (1, 0, 1, 0))
      // // first consider task-to-task connections
      // stimulusGraph.vertexSet.stream
      //   .flatMap(v => Task.safeCast(v).stream())
      //   .forEach(dstTask => {
      //     stimulusGraph
      //       .incomingEdgesOf(dstTask)
      //       .stream
      //       .flatMap(e => Task.safeCast(stimulusGraph.getEdgeSource(e)).stream())
      //       .forEach(srcTask => {
      //         if (dstTask.getHasORSemantics)
      //           for (
      //             (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
      //             (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
      //             if dst._2 == src._2 && dst._3 == src._3
      //           ) affineRelationsGraphBuilder.addEdge(i, j, (1, 0, 1, 0))
      //         else
      //           for (
      //             (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
      //             (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
      //             pRatio = (dst._2 / src._2).toDouble.ceil.toInt
      //           ) affineRelationsGraphBuilder.addEdge(i, j, (pRatio, 0, 1, 0))
      //       })
      //   })
      // // now consider upsampling connections
      // stimulusGraph.vertexSet.stream
      //   .flatMap(v => Upsample.safeCast(v).stream())
      //   .forEach(upsample => {
      //     stimulusGraph
      //       .incomingEdgesOf(upsample)
      //       .stream
      //       .flatMap(e => Task.safeCast(stimulusGraph.getEdgeSource(e)).stream())
      //       .forEach(srcTask => {
      //         stimulusGraph
      //           .outgoingEdgesOf(upsample)
      //           .stream
      //           .flatMap(e => Task.safeCast(stimulusGraph.getEdgeTarget(e)).stream())
      //           .forEach(dstTask => {
      //             if (dstTask.getHasORSemantics)
      //               for (
      //                 (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
      //                 (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
      //                 if dst._2 * Rational(upsample.getRepetitivePredecessorHolds) == src._2 &&
      //                   dst._3 - (dst._2 * Rational(upsample.getInitialPredecessorHolds)) == src._3
      //               )
      //                 affineRelationsGraphBuilder.addEdge(
      //                   i,
      //                   j,
      //                   (
      //                     upsample.getRepetitivePredecessorHolds.toInt,
      //                     upsample.getInitialPredecessorHolds.toInt,
      //                     1,
      //                     0
      //                   )
      //                 )
      //             else
      //               for (
      //                 (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
      //                 (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
      //                 pRatio = (dst._2 / src._2).toDouble.ceil.toInt;
      //                 offset = ((dst._3 - src._3) / src._2).toDouble.toInt
      //               ) affineRelationsGraphBuilder.addEdge(i, j, (pRatio, offset, 1, 0))
      //           })
      //       })
      //   })
      // // now finally consider downsample connections
      // stimulusGraph.vertexSet.stream
      //   .flatMap(v => Downsample.safeCast(v).stream())
      //   .forEach(downsample => {
      //     stimulusGraph
      //       .incomingEdgesOf(downsample)
      //       .stream
      //       .flatMap(e => Task.safeCast(stimulusGraph.getEdgeSource(e)).stream())
      //       .forEach(srcTask => {
      //         stimulusGraph
      //           .outgoingEdgesOf(downsample)
      //           .stream
      //           .flatMap(e => Task.safeCast(stimulusGraph.getEdgeTarget(e)).stream())
      //           .forEach(dstTask => {
      //             if (dstTask.getHasORSemantics)
      //               for (
      //                 (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
      //                 (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
      //                 if dst._2 / Rational(downsample.getRepetitivePredecessorSkips) == src._2 &&
      //                   dst._3 + (dst._2 / Rational(
      //                     downsample.getInitialPredecessorSkips
      //                   )) == src._3
      //               )
      //                 affineRelationsGraphBuilder.addEdge(
      //                   i,
      //                   j,
      //                   (
      //                     1,
      //                     0,
      //                     downsample.getRepetitivePredecessorSkips.toInt,
      //                     downsample.getInitialPredecessorSkips.toInt
      //                   )
      //                 )
      //             else
      //               for (
      //                 (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
      //                 (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
      //                 pRatio = (src._2 / dst._2).toDouble.ceil.toInt;
      //                 offset = ((dst._3 - src._3) / dst._2).toDouble.toInt
      //               ) affineRelationsGraphBuilder.addEdge(i, j, (1, 0, pRatio, offset))
      //           })
      //       })
      //   })
      // affineRelationsGraphBuilder.buildAsUnmodifiable
      Option.empty
    }
  }
}
