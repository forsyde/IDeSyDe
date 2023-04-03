package idesyde.exploration

import upickle.default._

import idesyde.blueprints.ExplorationModule
import idesyde.exploration.explorers.ChocoExplorer
import idesyde.utils.Logger
import idesyde.core.DecisionModel
import idesyde.core.headers.DecisionModelHeader
import idesyde.utils.SimpleStandardIOLogger
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore

object ChocoExplorationModule extends ExplorationModule {

  val logger = SimpleStandardIOLogger("WARN")

  given Logger = logger

  override def decisionModelDecoders: Set[DecisionModelHeader => Option[DecisionModel]] = Set()

  override def uniqueIdentifier: String = "ChocoExplorationModule"

  def explorers = Set(ChocoExplorer())

  def decodeDecisionModels(m: DecisionModelHeader): Option[DecisionModel] = {
    m match {
      case DecisionModelHeader("SDFToTiledMultiCore", body_path, _, _) =>
        body_path.flatMap(decodeFromPath[SDFToTiledMultiCore])
      case _ => None
    }
  }

}
