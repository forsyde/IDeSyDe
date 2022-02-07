package idesyde.identification.rules.mixed

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import idesyde.identification.models.workload.SimplePeriodicWorkload
import idesyde.identification.models.platform.SchedulableNetworkedDigHW

class PeriodicTaskToSchedHWIdentRule extends IdentificationRule {

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = ???

  def identifyWithDependencies(
      model: ForSyDeSystemGraph,
      workloadModel: SimplePeriodicWorkload,
      platformModel: SchedulableNetworkedDigHW
  ): (Boolean, Option[DecisionModel]) = ???

}
