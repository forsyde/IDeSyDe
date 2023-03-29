package idesyde.exploration

import idesyde.blueprints.ExplorationModule
import idesyde.exploration.explorers.ChocoExplorer
import idesyde.utils.Logger
import idesyde.core.DecisionModel
import idesyde.core.headers.DecisionModelHeader
import idesyde.utils.SimpleStandardIOLogger

object ChocoExplorationModule extends ExplorationModule {

  val logger = SimpleStandardIOLogger("WARN")

  given Logger = logger

  override def decisionModelDecoders: Set[DecisionModelHeader => Option[DecisionModel]] = ???

  override def uniqueIdentifier: String = "ChocoExplorationModule"

  def explorers = Set(ChocoExplorer())

}
