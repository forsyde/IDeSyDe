package idesyde.identification.models.mixed

import scala.reflect.ClassTag
import idesyde.identification.common.models.platform.InstrumentedPlatformMixin
import idesyde.identification.common.models.workload.InstrumentedWorkloadMixin
import spire._
import spire.math._
import spire.implicits._

trait WCETComputationMixin[RealT](using fracT: spire.math.Fractional[RealT])(using ClassTag[RealT])
    extends InstrumentedWorkloadMixin
    with InstrumentedPlatformMixin[RealT] {

  def wcets: Array[Array[RealT]] = {
    // alll executables of task are instrumented
    // scribe.debug(taskModel.executables.mkString("[", ",", "]"))
    // compute the matrix (lazily)
    // scribe.debug(taskModel.taskComputationNeeds.mkString(", "))
    processComputationalNeeds.map(needs => {
      // scribe.debug(needs.mkString(","))
      processorsProvisions.zipWithIndex.map((provisions, j) => {
        // now take the maximum combination
        needs
          .flatMap((opGroup, opNeeds) => {
            provisions
              .filter((ipcGroup, ipc) => {
                opNeeds.keySet.subsetOf(ipc.keySet)
              })
              .map((ipcGroup, ipc) => {
                fracT.sum(
                  opNeeds
                    .map((k, v) => fracT.fromLong(v) / ipc(k))
                ) / fracT.fromLong(processorsFrequency(j))
              })
          })
          .maxByOption(_.toDouble)
          .getOrElse(fracT.minus(fracT.zero, fracT.one))
      })
    })
  }
}
