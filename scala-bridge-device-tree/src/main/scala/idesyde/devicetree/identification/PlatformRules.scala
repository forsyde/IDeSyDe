package idesyde.devicetree.identification

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.common.models.platform.SharedMemoryMultiCore
import idesyde.devicetree.utils.HasDeviceTreeUtils

trait PlatformRules extends HasDeviceTreeUtils {

  def identSharedMemoryMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[SharedMemoryMultiCore] = toDeviceTreeDesignModel(models) { dtm =>
    val roots = dtm.crossLinked
    Set()
  }
}
