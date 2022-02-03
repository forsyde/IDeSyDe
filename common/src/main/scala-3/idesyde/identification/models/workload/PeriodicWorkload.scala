package idesyde.identification.models.workload

import forsyde.io.java.core.Vertex
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.execution.Channel

/** Interface that describes a periodic workload model, also commonly known in the real time
  * academic community as "periodic task model". This one in particular closely follows the
  * definitions in:
  *
  * Scheduling Dependent Periodic Tasks Without Synchronization Mechanisms, Julien Forget Frédéric
  * Boniol, E. G. D. L. C. 2010 16th IEEE Real-Time and Embedded Technology and Applications
  * Symposium, 2010, 301-310
  *
  * @tparam TaskT
  *   The type representing each task.
  * @tparam TimeT
  *   The type that represents a time tag.
  */
trait PeriodicWorkload[TaskT, TimeT]()(using Numeric[TimeT]) extends DecisionModel:

  def periodicTasks: Array[TaskT]
  def channels: Array[Channel]
  def tasksNumInstances(t: TaskT): Int
  def instancesReleases(t: TaskT)(int: Int): TimeT
  def instancesDeadlines(t: TaskT)(int: Int): TimeT
  def instancePreceeds(src: TaskT)(dst: TaskT)(srcI: Int)(dstI: Int): Boolean
  def taskSize(src: TaskT): Long
  def channelSize(c: Channel): Long

  def maximalInterference(src: TaskT)(dst: TaskT)(using num: Numeric[TimeT]): Int =
    (0 until tasksNumInstances(dst))
      .map(dstIdx => {
        (0 until tasksNumInstances(src))
          // .filterNot(srcIdx =>
          //   instancePreceeds(src)(dst)(srcIdx)(dstIdx)
          // )
          .count(srcIdx => {
            // check intersection by comparing the endpoints
            (
              num.compare(instancesReleases(src)(srcIdx), instancesReleases(dst)(dstIdx)) <= 0 &&
              num.compare(instancesReleases(dst)(dstIdx), instancesDeadlines(src)(srcIdx)) <= 0
            )
            ||
            (
              num.compare(instancesReleases(dst)(dstIdx), instancesReleases(src)(srcIdx)) <= 0 &&
              num.compare(instancesReleases(src)(srcIdx), instancesDeadlines(dst)(dstIdx)) <= 0
            )
          })
      })
      .max

end PeriodicWorkload
