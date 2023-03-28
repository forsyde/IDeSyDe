package idesyde.exploration

import idesyde.core.Explorer

trait ExplorationModule {
  def explorers: Set[Explorer]
}
