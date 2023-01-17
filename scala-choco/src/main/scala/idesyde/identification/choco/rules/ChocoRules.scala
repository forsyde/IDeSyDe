package idesyde.identification.choco.rules

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.utils.Logger
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHW2
import spire.math.Rational
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.identification.choco.models.mixed.ChocoComDepTasksToMultiCore
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore

object ChocoRules {

  given Fractional[Rational] = spire.compat.fractional[Rational]

  def identChocoSDFToSChedTileHW2(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[ChocoSDFToSChedTileHW2] = identified
    .filter(m => m.isInstanceOf[SDFToTiledMultiCore])
    .map(m => m.asInstanceOf[SDFToTiledMultiCore])
    .map(dse => ChocoSDFToSChedTileHW2(dse))

  def identChocoComDepTasksToMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[ChocoComDepTasksToMultiCore] = identified
    .filter(m => m.isInstanceOf[PeriodicWorkloadToPartitionedSharedMultiCore])
    .map(m => m.asInstanceOf[PeriodicWorkloadToPartitionedSharedMultiCore])
    .map(dse => ChocoComDepTasksToMultiCore(dse))
}
