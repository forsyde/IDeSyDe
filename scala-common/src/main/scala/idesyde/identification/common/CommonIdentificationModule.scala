package idesyde.identification.common

import idesyde.identification.IdentificationRule
import idesyde.identification.IdentificationModule
import idesyde.identification.DecisionModel
import idesyde.identification.common.rules.PlatformRules
import idesyde.identification.common.rules.MixedRules
import idesyde.utils.Logger

class CommonIdentificationModule(using logger: Logger) extends IdentificationModule with MixedRules with PlatformRules {

  val identificationRules = Set(
    identSchedulableTiledMultiCore,
    identPartitionedSharedMemoryMultiCore,
    identSDFToPartitionedSharedMemory,
    identSDFToTiledMultiCore,
    identPeriodicWorkloadToPartitionedSharedMultiCore
  )

  val integrationRules = Set()

}
