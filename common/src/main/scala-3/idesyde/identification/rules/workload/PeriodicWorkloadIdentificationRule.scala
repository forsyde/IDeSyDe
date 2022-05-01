package idesyde.identification.rules.workload

import idesyde.identification.IdentificationRule
import idesyde.identification.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.execution.PeriodicTask
import forsyde.io.java.typed.viewers.impl.Executable

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.execution.Stimulus
import idesyde.identification.models.workload.SimplePeriodicWorkload
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.execution.ReactiveTask
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.impl.DataBlock
import forsyde.io.java.typed.viewers.impl.CommunicatingExecutable
import forsyde.io.java.typed.viewers.impl.TokenizableDataBlock

final class PeriodicWorkloadIdentificationRule(using Numeric[BigFraction])
    extends ForSyDeIdentificationRule[SimplePeriodicWorkload]:

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) =
    var periodicTasks: Array[Task]                = Array.empty
    var dataBlocks: Array[DataBlock]              = Array.empty
    var periodicStimulus: Array[PeriodicStimulus] = Array.empty
    model.vertexSet.stream.forEach(v => {
      // try to classify first as periodic and then later as reactive
      Task
        .safeCast(v)
        .ifPresent(task => periodicTasks :+= task)
      DataBlock.safeCast(v).ifPresent(channel => dataBlocks :+= channel)
      PeriodicStimulus.safeCast(v).ifPresent(stim => periodicStimulus :+= stim)
    })
    // convenience
    lazy val tasks = periodicTasks ++ reactiveTasks
    // build the task-to-executable relationship
    lazy val executables = tasks.map(_.getCallSequencePort(model).asScala.toArray)
    // build the task-to-stimulus relation ship
    var predecessors: Array[Array[Int]] = Array.empty
    var successors: Array[Int]          = Array.emptyIntArray
    // build the precedence arrays
    // it is a bit verbose due to how the comparison is done. THrough IDs it is sure-fire.
    PeriodicStimulus.foreach(stimulus => {
      successors :+= tasks.indexWhere(t =>
        stimulus.getSuccessorPort(model).map(_.getIdentifier == t.getIdentifier).orElse(false)
      )
      SimplePeriodicStimulus
        .safeCast(stimulus)
        .ifPresent(simple => {
          predecessors :+= Array(
            tasks.indexWhere(t =>
              simple.getPredecessorPort(model).map(_.getIdentifier == t.getIdentifier).orElse(false)
            )
          )
        })
      MultiANDPeriodicStimulus
        .safeCast(stimulus)
        .ifPresent(andStimulus => {
          predecessors :+= andStimulus
            .getPredecessorsPort(model)
            .stream
            .mapToInt(predecessor => {
              tasks.indexWhere(t => predecessor.getIdentifier == t.getIdentifier)
            })
            .toArray
        })
    })
    // build the read and write arrays
    lazy val taskChannelReads = executables.zipWithIndex
      .map((es, i) => {
        val t = tasks(i)
        dataBlocks.zipWithIndex
          .map((c, j) => {
            if (model.hasConnection(c, t)) then
              CommunicatingExecutable
                .safeCast(t)
                .map(commTask => {
                  (c, commTask.getPortDataReadSize.getOrDefault(c.getIdentifier, 1).toInt)
                })
                .orElse((c, 1))
            else (c, 0)
          })
          .map((c, elems) =>
            TokenizableDataBlock
              .safeCast(c)
              .map(block => block.getTokenSizeInBits * elems)
              .orElse(c.getMaxSizeInBits)
              .toLong
          )
      })
    lazy val taskChannelWrites = tasks
      .map(t => {
        dataBlocks.zipWithIndex
          .map((c, j) => {
            if (model.hasConnection(t, c)) then
              CommunicatingExecutable
                .safeCast(t)
                .map(commTask => {
                  (c, commTask.getPortDataWrittenSize.getOrDefault(c.getIdentifier, 1).toInt)
                })
                .orElse((c, 1))
            else (c, 0)
          })
          .map((c, elems) =>
            TokenizableDataBlock
              .safeCast(c)
              .map(block => block.getTokenSizeInBits * elems)
              .orElse(c.getMaxSizeInBits)
              .toLong
          )
      })
    if (periodicTasks.isEmpty)
      scribe.debug("No periodic workload model found.")
      (true, Option.empty)
    else if (periodicTasks.exists(_.getPeriodicStimulusPort(model).isEmpty))
      scribe.debug("Some periodic tasks have no periodic stimulus. Skipping.")
      (true, Option.empty)
    else
      val decisionModel = SimplePeriodicWorkload(
        periodicTasks = periodicTasks,
        reactiveTasks = reactiveTasks,
        periodicStimulus = periodicTasks.map(_.getPeriodicStimulusPort(model).get),
        PeriodicStimulus = PeriodicStimulus,
        executables = executables,
        dataBlocks = dataBlocks,
        PeriodicStimulusSrcs = predecessors,
        PeriodicStimulusDst = successors,
        taskChannelReads = taskChannelReads,
        taskChannelWrites = taskChannelWrites
      )
      scribe.debug(
        s"Simple periodic task model found with ${periodicTasks.length} periodic tasks, " +
          s"${reactiveTasks.length} reactive tasks and ${dataBlocks.length} dataBlocks"
      )
      (true, Option(decisionModel))

end PeriodicTaskIdentificationRule
