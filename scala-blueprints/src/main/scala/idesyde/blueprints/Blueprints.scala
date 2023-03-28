package idesyde.blueprints

import idesyde.core.DesignModel
import idesyde.core.DecisionModel

object Blueprints {
  
    inline def standaloneIdentificationAggregate(
        args: Array[String],
        drivers: Set[(os.Path) => Option[DesignModel]],
        identificationRules: Set[(Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]],
    ): Unit = {

    }
}
