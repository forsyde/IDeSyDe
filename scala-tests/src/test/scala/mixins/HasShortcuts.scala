package mixins

import idesyde.identification.CanIdentify
import idesyde.exploration.CanExplore
import idesyde.identification.forsyde.ForSyDeDesignModel
import idesyde.core.DecisionModel
import idesyde.identification.common.CommonIdentificationLibrary
import idesyde.identification.choco.ChocoIdentificationLibrary
import idesyde.identification.forsyde.ForSyDeIdentificationLibrary
import idesyde.identification.minizinc.MinizincIdentificationModule
import idesyde.exploration.ChocoExplorationModule
import idesyde.core.Explorer
import idesyde.utils.Logger
import idesyde.core.DesignModel
import idesyde.devicetree.identification.DeviceTreeIdentificationModule
import idesyde.matlab.identification.SimulinkMatlabIdentificationModule

trait HasShortcuts(using Logger) extends CanExplore with CanIdentify {

  def identify(model: DesignModel): Set[DecisionModel] = identify(Set(model))

  def identify(models: Set[DesignModel]): Set[DecisionModel] = identifyDecisionModels(
    models,
    Set(
      CommonIdentificationLibrary(),
      ChocoIdentificationLibrary(),
      ForSyDeIdentificationLibrary(),
      MinizincIdentificationModule(),
      DeviceTreeIdentificationModule,
      SimulinkMatlabIdentificationModule
    )
  )

  def getExplorerAndModel(identified: Set[DecisionModel]): Set[(Explorer, DecisionModel)] =
    chooseExplorersAndModels(identified, Set(ChocoExplorationModule)).map(combo => (combo.explorer, combo.decisionModel))

  def integrate(model: DesignModel, solution: DecisionModel): Set[DesignModel] =
    integrateDecisionModel(
      model,
      solution,
      Set(
        CommonIdentificationLibrary(),
        ChocoIdentificationLibrary(),
        ForSyDeIdentificationLibrary(),
        MinizincIdentificationModule()
      )
    )
}
