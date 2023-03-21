package mixins

import idesyde.identification.CanIdentify
import idesyde.exploration.CanExplore
import idesyde.identification.forsyde.ForSyDeDesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.common.CommonIdentificationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.ForSyDeIdentificationModule
import idesyde.identification.minizinc.MinizincIdentificationModule
import idesyde.exploration.ChocoExplorationModule
import idesyde.exploration.Explorer
import idesyde.utils.Logger
import idesyde.identification.DesignModel
import idesyde.devicetree.identification.DeviceTreeIdentificationModule

trait HasShortcuts(using Logger) extends CanExplore with CanIdentify {

  def identify(model: DesignModel): Set[DecisionModel] = identify(Set(model))

  def identify(models: Set[DesignModel]): Set[DecisionModel] = identifyDecisionModels(
    models,
    Set(
      CommonIdentificationModule(),
      ChocoIdentificationModule(),
      ForSyDeIdentificationModule(),
      MinizincIdentificationModule(),
      DeviceTreeIdentificationModule()
    )
  )

  def getExplorerAndModel(identified: Set[DecisionModel]): Set[(Explorer, DecisionModel)] =
    chooseExplorersAndModels(identified, Set(ChocoExplorationModule()))

  def integrate(model: DesignModel, solution: DecisionModel): Set[DesignModel] =
    integrateDecisionModel(
      model,
      solution,
      Set(
        CommonIdentificationModule(),
        ChocoIdentificationModule(),
        ForSyDeIdentificationModule(),
        MinizincIdentificationModule()
      )
    )
}
