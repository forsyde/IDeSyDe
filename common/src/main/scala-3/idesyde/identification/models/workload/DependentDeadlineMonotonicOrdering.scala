package idesyde.identification.models.workload

import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.execution.PeriodicTask

final case class DependentDeadlineMonotonicOrdering(val taskModel: SimplePeriodicWorkload)
    extends Ordering[Task] {

  def compare(t1: Task, t2: Task): Int =
    val t1i = taskModel.tasks.indexOf(t1)
    val t2i = taskModel.tasks.indexOf(t2)
    if (t1i > -1 && t2i > -1) {
      //scribe.debug(s"compare ${t1.getIdentifier} ti ${t2.getIdentifier}: ${taskModel.interTaskCanBlock(t1i)(t2i)}")
      if (!taskModel.interTaskCanBlock(t1i)(t2i)) {
        // smallest wins
        //scribe.debug(s"smallest ${-taskModel.periods(t1i).compareTo(taskModel.periods(t2i))}")
        -taskModel.periods(t1i).compareTo(taskModel.periods(t2i))
      }
      // comes before, so higher prio
      else 1
    } else 0
}
