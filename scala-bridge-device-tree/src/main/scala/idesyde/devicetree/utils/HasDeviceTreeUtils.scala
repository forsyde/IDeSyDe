package idesyde.devicetree.utils

import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.devicetree.identification.DeviceTreeDesignModel
import idesyde.devicetree.identification.OSDescriptionDesignModel

trait HasDeviceTreeUtils {
  inline def toDeviceTreeDesignModel[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (DeviceTreeDesignModel) => (Set[M], Set[String])
  ): (Set[M], Set[String]) = {
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
      mergedOpt.map(m => body(m)).getOrElse((Set(), Set()))
    } else {
      (Set(), Set())
    }
  }

  inline def toOSDescriptionDesignModel[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (OSDescriptionDesignModel) => (Set[M], Set[String])
  ): (Set[M], Set[String]) = {
    val ms = models.flatMap(_ match {
      case m: OSDescriptionDesignModel => Some(m)
      case _                        => None
    })
    if (!ms.isEmpty) {
      val mergedOpt = ms.tail.foldLeft(ms.headOption)((l, m) =>
        l.flatMap(lm =>
          lm.merge(m)
            .flatMap(result =>
              result match {
                case d: OSDescriptionDesignModel => Some(d)
                case _                        => None
              }
            )
        )
      )
      mergedOpt.map(m => body(m)).getOrElse((Set(), Set()))
    } else {
      (Set(), Set())
    }
  }
  
}
