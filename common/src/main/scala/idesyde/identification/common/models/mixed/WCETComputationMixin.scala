package idesyde.identification.models.mixed

import scala.reflect.ClassTag
import idesyde.identification.models.platform.InstrumentedPlatformMixin
import idesyde.identification.models.workload.InstrumentedWorkloadMixin

trait WCETComputationMixin[UnitT](using fracT: Fractional[UnitT])(using conv: Conversion[Double, UnitT])(using ClassTag[UnitT]) extends InstrumentedWorkloadMixin with InstrumentedPlatformMixin {

    lazy val wcets: Array[Array[UnitT]] = {
    // alll executables of task are instrumented
    // scribe.debug(taskModel.executables.mkString("[", ",", "]"))
    // compute the matrix (lazily)
    // scribe.debug(taskModel.taskComputationNeeds.mkString(", "))
    processComputationalNeeds.map(needs => {
      // scribe.debug(needs.mkString(","))
      processorsProvisions.zipWithIndex.map((provisions, j) => {
        // now take the maximum combination
        needs.flatMap((opGroup, opNeeds) => {
            provisions.filter((ipcGroup, ipc) => {
                opNeeds.keySet.subsetOf(ipc.keySet)
            }).map((ipcGroup, ipc) => {
                opNeeds.map((k, v) => v / ipc(k)).sum / processorsFrequency(j)
            })
        }).maxOption
        .map(d => conv(d))
        .getOrElse(fracT.minus(fracT.zero, fracT.one))
      })
    })
  }
}
