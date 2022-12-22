package idesyde.exploration

import idesyde.exploration.Explorer

trait ExplorationModule {
  def explorers: Set[Explorer]
}
