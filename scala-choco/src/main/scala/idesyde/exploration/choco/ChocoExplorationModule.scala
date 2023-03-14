package idesyde.exploration

import idesyde.exploration.ExplorationModule
import idesyde.exploration.explorers.ChocoExplorer
import idesyde.utils.Logger

class ChocoExplorationModule(using Logger) extends ExplorationModule {

  def explorers = Set(ChocoExplorer())

}
