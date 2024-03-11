package idesyde.identification.choco.rules

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import spire.math.Rational
import idesyde.common.legacy.SDFToTiledMultiCore
import idesyde.choco.CanSolveDepTasksToPartitionedMultiCore
import idesyde.common.legacy.PeriodicWorkloadToPartitionedSharedMultiCore

trait ChocoRules {

  given Fractional[Rational] = spire.compat.fractional[Rational]

  // def identChocoSDFToSChedTileHW2(
  //     models: Set[DesignModel],
  //     identified: Set[DecisionModel]
  // ): Set[ChocoSDFToSChedTileHW2] = identified
  //   .filter(m => m.isInstanceOf[SDFToTiledMultiCore])
  //   .map(m => m.asInstanceOf[SDFToTiledMultiCore])
  //   .map(dse => ChocoSDFToSChedTileHW2(dse))

  // def identChocoComDepTasksToMultiCore(
  //     models: Set[DesignModel],
  //     identified: Set[DecisionModel]
  // ): Set[CanSolveDepTasksToPartitionedMultiCore] = identified
  //   .filter(m => m.isInstanceOf[PeriodicWorkloadToPartitionedSharedMultiCore])
  //   .map(m => m.asInstanceOf[PeriodicWorkloadToPartitionedSharedMultiCore])
  //   .map(dse => CanSolveDepTasksToPartitionedMultiCore(dse))
}
