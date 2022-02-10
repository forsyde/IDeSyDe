package idesyde.identification.rules.workload

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.execution.PeriodicTask
import forsyde.io.java.typed.viewers.execution.Channel
import forsyde.io.java.typed.viewers.execution.PrecedenceConstraint
import forsyde.io.java.typed.viewers.impl.Executable

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.execution.Stimulus
import idesyde.identification.models.workload.SimplePeriodicWorkload
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.execution.ReactiveStimulus

final class PeriodicTaskIdentificationRule(using Numeric[BigFraction]) extends IdentificationRule:

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) =
    var periodicTasks: Array[PeriodicTask] = Array.empty
    var channels: Array[Channel]           = Array.empty
    var reactiveStimulus: Array[ReactiveStimulus] = Array.empty
    model.vertexSet.stream.forEach(v => {
      if (PeriodicTask.conforms(v)) periodicTasks = periodicTasks.appended(PeriodicTask.enforce(v))
      else if (Channel.conforms(v)) channels = channels.appended(Channel.enforce(v))
      //else if (Executable.conforms(v)) executablesArray = executablesArray.appended(Executable.enforce(v))
      else if (ReactiveStimulus.conforms(v))
        reactiveStimulus = reactiveStimulus.appended(ReactiveStimulus.enforce(v))
    })
    // build the task-to-executable relationship
    val executables = periodicTasks.map(_.getCallSequencePort(model).asScala.toArray)
    // build the task-to-stimulus relation ship
    val stimulusOpt = periodicTasks.map(_.getPeriodicStimulusPort(model))
    // build the precedence matrix
    lazy val precedences = periodicTasks.map(src =>
      periodicTasks.map(dst =>
        reactiveStimulus.find(prec =>
          prec.getPredecessorPort(model).contains(src) &&
            prec.getSucessorPort(model).contains(dst)
        )
      )
    )
    if (stimulusOpt.exists(_.isEmpty))
      scribe.debug("Some tasks have no periodic stimulus. Skipping.")
      (true, Option.empty)
    else 
      val stimulus = stimulusOpt.map(_.get)
      val decisionModel = SimplePeriodicWorkload(
          periodicTasks,
          stimulus,
          executables,
          channels,
          precedences
      )
      (true, Option(decisionModel))
    (true, Option.empty)

end PeriodicTaskIdentificationRule
