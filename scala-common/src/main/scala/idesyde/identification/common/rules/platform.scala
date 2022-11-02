package idesyde.identification.common.rules.platform

import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult
import idesyde.identification.DesignModel
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore

def identSchedulableTiledMultiCore(
    models: Set[DesignModel],
    identified: Set[DecisionModel]
): IdentificationResult[SchedulableTiledMultiCore] = IdentificationResult.fixedEmpty()
