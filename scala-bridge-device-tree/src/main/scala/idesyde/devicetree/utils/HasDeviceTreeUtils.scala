package idesyde.devicetree.utils

import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel
import idesyde.devicetree.identification.DeviceTreeDesignModel

trait HasDeviceTreeUtils {
  inline def toDeviceTreeDesignModel[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (DeviceTreeDesignModel) => Set[M]
  ): Set[M] = {
    val ms = models.flatMap(_ match {
      case m: DeviceTreeDesignModel => Some(m)
      case _                        => None
    })
    if (!ms.isEmpty) {
      val mergedOpt = ms.tail.foldLeft(ms.headOption)((l, m) =>
        l.flatMap(lm =>
          lm.merge(m)
            .flatMap(result =>
              result match {
                case d: DeviceTreeDesignModel => Some(d)
                case _                        => None
              }
            )
        )
      )
      mergedOpt.map(m => body(m)).getOrElse(Set())
    } else {
      Set()
    }
  }
}
