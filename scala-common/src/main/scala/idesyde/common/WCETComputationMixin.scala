package idesyde.common

import scala.reflect.ClassTag
import spire._
import spire.math._
import spire.implicits._

trait WCETComputationMixin[RealT](
    val instruWorkload: InstrumentedWorkloadMixin,
    val intruPlatform: InstrumentedPlatformMixin[RealT]
)(using fracT: spire.math.Fractional[RealT])(using ClassTag[RealT]) {

  def computeWcets: Vector[Vector[RealT]] = {
    // alll executables of task are instrumented
    // scribe.debug(taskModel.executables.mkString("[", ",", "]"))
    // compute the matrix (lazily)
    // scribe.debug(taskModel.taskComputationNeeds.mkString(", "))
    instruWorkload.processComputationalNeeds.map(needs => {
      // scribe.debug(needs.mkString(","))
      intruPlatform.processorsProvisions.zipWithIndex.map((provisions, j) => {
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
                ) / fracT.fromLong(intruPlatform.processorsFrequency(j))
              })
          })
          .maxByOption(_.toDouble)
          .getOrElse(fracT.minus(fracT.zero, fracT.one))
      })
    })
  }
}
