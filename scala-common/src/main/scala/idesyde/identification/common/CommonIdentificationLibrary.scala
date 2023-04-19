package idesyde.identification.common

import idesyde.core.DecisionModel
import idesyde.core.IdentificationLibrary
import idesyde.identification.common.rules.PlatformRules
import idesyde.identification.common.rules.MixedRules
import idesyde.utils.Logger
import idesyde.core.MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule
import idesyde.core.MarkedIdentificationRule.DecisionModelOnlyIdentificationRule

class CommonIdentificationLibrary(using logger: Logger)
    extends IdentificationLibrary
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
    DecisionModelOnlyIdentificationRule(identTaksAndSDFServerToMultiCore),
    DecisionModelOnlyIdentificationRule(identTaskdAndSDFServer)
  )

  val reverseIdentificationRules = Set()

}
