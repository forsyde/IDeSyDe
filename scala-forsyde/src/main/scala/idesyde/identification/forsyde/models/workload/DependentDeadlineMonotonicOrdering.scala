package idesyde.identification.forsyde.models.workload

import forsyde.io.java.typed.viewers.execution.Task

import idesyde.identification.models.workload.PeriodicWorkloadMixin
import idesyde.utils.MultipliableFractional
import idesyde.identification.models.workload.PeriodicWorkloadMixin

final case class DependentDeadlineMonotonicOrdering[TimeT, T <: PeriodicWorkloadMixin[TimeT]](
    val taskModel: T
)(using fracT: MultipliableFractional[TimeT])
    extends Ordering[Int] {

  def compare(t1: Int, t2: Int): Int =
    if (t1 > -1 && t2 > -1) {
      //scribe.debug(s"compare ${t1.getIdentifier} ti ${t2.getIdentifier}: ${taskModel.interTaskCanBlock(t1i)(t2i)}")
      if (false) { //!taskModel.interTaskCanBlock(t1i)(t2i)) {
        // smallest wins
        //scribe.debug(s"smallest ${-taskModel.periods(t1i).compareTo(taskModel.periods(t2i))}")
        //-taskModel.periods(t1).compareTo(taskModel.periods(t2))
        0
      }
      // comes before, so higher prio
      else 1
    } else 0
}
