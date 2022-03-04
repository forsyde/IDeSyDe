package idesyde.exploration

import idesyde.exploration.api.ExplorationModule
import idesyde.exploration.explorers.ChocoExplorer

class ChocoExplorationModule extends ExplorationModule {

    def explorers = Set(ChocoExplorer())
  
}
