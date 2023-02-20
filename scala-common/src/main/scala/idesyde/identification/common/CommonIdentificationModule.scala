package idesyde.identification.common

import idesyde.identification.IdentificationRule
import idesyde.identification.IdentificationModule
import idesyde.identification.DecisionModel
import idesyde.identification.common.rules.PlatformRules
import idesyde.identification.common.rules.MixedRules
import idesyde.utils.Logger
import idesyde.identification.MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule
import idesyde.identification.MarkedIdentificationRule.DecisionModelOnlyIdentificationRule

class CommonIdentificationModule(using logger: Logger)
  extends IdentificationModule
    with MixedRules
    with PlatformRules {

  val identificationRules = Set(
    SpecificDecisionModelOnlyIdentificationRule(
      identSchedulableTiledMultiCore,
      Set("PartitionedCoresWithRuntimes", "TiledMultiCore")
    ),
    SpecificDecisionModelOnlyIdentificationRule(
      identPartitionedSharedMemoryMultiCore,
      Set("PartitionedCoresWithRuntimes", "SharedMemoryMultiCore")
    ),
    DecisionModelOnlyIdentificationRule(identSDFToPartitionedSharedMemory),
    DecisionModelOnlyIdentificationRule(identSDFToTiledMultiCore),
    DecisionModelOnlyIdentificationRule(identPeriodicWorkloadToPartitionedSharedMultiCore),
    DecisionModelOnlyIdentificationRule(identTaksAndSDFServerToMultiCore)
  )

  val integrationRules = Set()

}