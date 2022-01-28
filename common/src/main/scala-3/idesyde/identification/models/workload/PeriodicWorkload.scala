package idesyde.identification.models.workload

import forsyde.io.java.core.Vertex
import idesyde.identification.DecisionModel

/**
 * Interface that describes a periodic workload model, also commonly known in the
 * real time academic community as "periodic task model". This one in particular
 * closely follows the definitions in:
 *
 * Scheduling Dependent Periodic Tasks Without Synchronization Mechanisms,
 * Julien Forget Frédéric Boniol, E. G. D. L. C.
 * 2010 16th IEEE Real-Time and Embedded Technology and Applications Symposium, 2010, 301-310
 *
 *
 * @tparam TaskT The type representing each task.
 * @tparam TimeT The type that represents a time tag.
 */
trait PeriodicWorkload[TaskT, TimeT]() extends DecisionModel:

  def periodicTasks: Array[TaskT]
  def tasksNumInstances(t: TaskT): Int
  def instancesReleases(t: TaskT)(int: Int): TimeT
  def instancesDeadlines(t: TaskT)(int: Int): TimeT
  def instancePreceeds(src :TaskT)(dst: TaskT)(srcI: Int)(dstI: Int): Boolean

end PeriodicWorkload
