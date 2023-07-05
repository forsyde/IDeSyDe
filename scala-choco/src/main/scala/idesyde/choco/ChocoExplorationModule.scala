package idesyde.choco

import upickle.default._

import idesyde.blueprints.StandaloneExplorationModule
import idesyde.utils.Logger
import idesyde.core.DecisionModel
import idesyde.core.headers.DecisionModelHeader
import idesyde.utils.SimpleStandardIOLogger
import idesyde.common.SDFToTiledMultiCore
import idesyde.choco.ChocoExplorer
import spire.math.Rational
import idesyde.core.ExplorationCombinationDescription
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.common.PeriodicWorkloadAndSDFServerToMultiCore

object ChocoExplorationModule extends StandaloneExplorationModule {

  // def combination(decisionModel: DecisionModel): ExplorationCombinationDescription = {
  //   val combos = explorers.map(e => e.combination(decisionModel))
  //   // keep only the dominant ones and take the biggest
  //   combos
  //     .filter(l => {
  //       combos
  //         .filter(_ != l)
  //         .forall(r => {
  //           l `<?>` r match {
  //             case '>' | '=' => true
  //             case _         => false
  //           }
  //         })
  //     })
  //     .head
  // }

  given Fractional[Rational] = spire.compat.fractional[Rational]

  val logger = SimpleStandardIOLogger("WARN")

  given Logger = logger

  override def uniqueIdentifier: String = "ChocoExplorationModule"

  override def explorers = List(ChocoExplorer())

  def decodeDecisionModels(m: DecisionModelHeader): Option[DecisionModel] = {
    m match {
      case DecisionModelHeader("SDFToTiledMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[SDFToTiledMultiCore])
      case DecisionModelHeader("PeriodicWorkloadToPartitionedSharedMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[PeriodicWorkloadToPartitionedSharedMultiCore])
      case DecisionModelHeader("PeriodicWorkloadAndSDFServerToMultiCore", body_path, _) =>
        body_path.flatMap(decodeFromPath[PeriodicWorkloadAndSDFServerToMultiCore])
      case _ => None
    }
  }

  def main(args: Array[String]): Unit = standaloneExplorationModule(args)

}
