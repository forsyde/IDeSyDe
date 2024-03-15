package idesyde.choco

import scala.jdk.CollectionConverters._

import upickle.default._

import idesyde.blueprints.StandaloneModule
import idesyde.core.DecisionModel
import idesyde.common.legacy.SDFToTiledMultiCore
import idesyde.choco.ChocoExplorer
import spire.math.Rational
import idesyde.common.legacy.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.common.legacy.PeriodicWorkloadAndSDFServerToMultiCoreOld
import idesyde.core.OpaqueDecisionModel
import java.util.Optional

object ChocoExplorationModule extends StandaloneModule {

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

  override def uniqueIdentifier(): String = "ChocoExplorationModule"

  override def explorers() = Set(ChocoExplorer()).asJava

  override def fromOpaqueDecision(opaque: OpaqueDecisionModel): Optional[DecisionModel] = {
    opaque.category() match {
      case "SDFToTiledMultiCore" =>
        opaque
          .bodyJson().map(x => read[SDFToTiledMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadToPartitionedSharedMultiCore" =>
        opaque
          .bodyJson().map(x => read[PeriodicWorkloadToPartitionedSharedMultiCore](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case "PeriodicWorkloadAndSDFServerToMultiCoreOld" =>
        opaque
          .bodyJson().map(x => read[PeriodicWorkloadAndSDFServerToMultiCoreOld](x))
          .map(x => x.asInstanceOf[DecisionModel])
      case _ => Optional.empty()
    }
  }

  // def decisionMessageToModel(m: DecisionModelMessage): Option[DecisionModel] = {
  //   m.header match {
  //     case DecisionModelHeader("SDFToTiledMultiCore", _, _) =>
  //       m.body.map(s => read[SDFToTiledMultiCore](s))
  //     case DecisionModelHeader("PeriodicWorkloadToPartitionedSharedMultiCore", _, _) =>
  //       m.body.map(s => read[PeriodicWorkloadToPartitionedSharedMultiCore](s))
  //     case DecisionModelHeader("PeriodicWorkloadAndSDFServerToMultiCoreOld", _, _) =>
  //       m.body.map(s => read[PeriodicWorkloadAndSDFServerToMultiCoreOld](s))
  //     case _ => None
  //   }
  // }

  def main(args: Array[String]): Unit =
    standaloneModule(args).ifPresent(javalin => javalin.start(0))

}
