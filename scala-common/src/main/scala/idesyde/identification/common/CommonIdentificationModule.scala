package idesyde.identification.common

import idesyde.identification.IdentificationRule
import idesyde.identification.IdentificationModule
import idesyde.identification.DecisionModel
import idesyde.identification.common.rules.PlatformRules
import idesyde.identification.common.rules.MixedRules

class CommonIdentificationModule extends IdentificationModule {

  val identificationRules = Set(
    PlatformRules.identSchedulableTiledMultiCore,
    PlatformRules.identPartitionedSharedMemoryMultiCore,
    MixedRules.identSDFToPartitionedSharedMemory,
    MixedRules.identSDFToTiledMultiCore,
    MixedRules.identPeriodicWorkloadToPartitionedSharedMultiCore
  )

  val integrationRules = Set()

}
