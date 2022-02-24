package idesyde.identification.rules.workload

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.execution.PeriodicTask
import forsyde.io.java.typed.viewers.execution.Channel
import forsyde.io.java.typed.viewers.impl.Executable

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.execution.Stimulus
import idesyde.identification.models.workload.SimplePeriodicWorkload
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.execution.ReactiveStimulus
import forsyde.io.java.typed.viewers.execution.ReactiveTask
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.execution.SimpleReactiveStimulus
import forsyde.io.java.typed.viewers.execution.MultiANDReactiveStimulus

final class PeriodicTaskIdentificationRule(using Numeric[BigFraction]) extends IdentificationRule:

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) =
    var periodicTasks: Array[PeriodicTask] = Array.empty
    var reactiveTasks: Array[ReactiveTask] = Array.empty
    var channels: Array[Channel]           = Array.empty
    var reactiveStimulus: Array[ReactiveStimulus] = Array.empty
    model.vertexSet.stream.forEach(v => {
      // try to classify first as periodic and then later as reactive
      PeriodicTask.safeCast(v).ifPresentOrElse(task => periodicTasks :+= task, () => {
        ReactiveTask.safeCast(v).ifPresent(task => reactiveTasks :+= task)
      })
      Channel.safeCast(v).ifPresent(channel => channels :+= channel)
      ReactiveStimulus.safeCast(v).ifPresent(stim => reactiveStimulus :+= stim)
    })
    // convenience
    val tasks = periodicTasks ++ reactiveTasks
    // build the task-to-executable relationship
    val executables = tasks.map(_.getCallSequencePort(model).asScala.toArray)
    // build the task-to-stimulus relation ship
    var predecessors: Array[Array[Int]] = Array.empty
    var successors: Array[Int] = Array.emptyIntArray
    // build the precedence arrays
    // it is a bit verbose due to how the comparison is done. THrough IDs it is sure-fire.
    reactiveStimulus.foreach(stimulus => {
      successors :+= tasks.indexWhere(t => 
        stimulus.getSuccessorPort(model).map(_.getIdentifier == t.getIdentifier).orElse(false)
      )
      SimpleReactiveStimulus.safeCast(stimulus).ifPresent(simple => {
        predecessors :+= Array(tasks.indexWhere(t => 
          simple.getPredecessorPort(model).map(_.getIdentifier == t.getIdentifier).orElse(false)
        ))
      })
      MultiANDReactiveStimulus.safeCast(stimulus).ifPresent(andStimulus => {
        predecessors :+= andStimulus.getPredecessorsPort(model).stream.mapToInt(predecessor => {
          tasks.indexWhere(t => predecessor.getIdentifier == t.getIdentifier)
        }).toArray
      })
    })
    // build the read and write arrays
    val (taskChannelRead, taskChannelWrite) = tasks
      .map(t => {
        (
          channels.zipWithIndex.filter((c, j) => {
            model.hasConnection(c, t)
          }).map((c, j) => j),
          channels.zipWithIndex.filter((c, j) => {
            model.hasConnection(t, c)
          }).map((c, j) => j)
        )
      }).unzip
    if (periodicTasks.exists(_.getPeriodicStimulusPort(model).isEmpty))
      scribe.debug("Some periodic tasks have no periodic stimulus. Skipping.")
      (true, Option.empty)
    else
      val decisionModel = SimplePeriodicWorkload(
        periodicTasks = periodicTasks,
        reactiveTasks = reactiveTasks,
        periodicStimulus = periodicTasks.map(_.getPeriodicStimulusPort(model).get),
        reactiveStimulus = reactiveStimulus,
        executables = executables,
        channels = channels,
        reactiveStimulusSrcs = predecessors,
        reactiveStimulusDst = successors,
        taskChannelRead = taskChannelRead, 
        taskChannelWrite = taskChannelWrite
      )
      scribe.debug(
        s"Simple periodic task model found with ${periodicTasks.length} periodic tasks, " +
          s"${reactiveTasks.length} reactive tasks and ${channels.length} channels"
      )
      (true, Option(decisionModel))

end PeriodicTaskIdentificationRule
