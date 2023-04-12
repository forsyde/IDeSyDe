package mixins

import idesyde.identification.CanIdentify
import idesyde.exploration.CanExplore
import idesyde.core.DecisionModel
import idesyde.identification.common.CommonIdentificationLibrary
import idesyde.identification.choco.ChocoIdentificationLibrary
import idesyde.core.Explorer
import idesyde.utils.Logger
import idesyde.core.DesignModel
import idesyde.matlab.identification.SimulinkMatlabIdentificationModule
import idesyde.choco.ChocoExplorationModule
import idesyde.forsydeio.ForSyDeIdentificationLibrary
import idesyde.devicetree.DeviceTreeIdentificationModule
import idesyde.minizinc.MinizincIdentificationModule

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
    chooseExplorersAndModels(identified, Set(ChocoExplorationModule))

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
