package idesyde.identification.choco.models.workload

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.Propagator
import scala.annotation.targetName
import org.chocosolver.solver.variables.Variable
import org.chocosolver.util.ESat
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.variables.BoolVar
import spire.math.*
import idesyde.identification.choco.models.workload.FixedPriorityPreemptivePropagator
import org.chocosolver.solver.Model

class FixedPriorityConstraintsModule(
    val chocoModel: Model,
    val priorities: Array[Int],
    val periods: Array[Int],
    val deadlines: Array[Int],
    val wcets: Array[Array[Int]],
    val taskExecution: Array[IntVar],
    val responseTimes: Array[IntVar],
    val blockingTimes: Array[IntVar],
    val durations: Array[IntVar]
) extends ChocoModelMixin() {

  def sufficientRMSchedulingPoints(taskIdx: Int): Array[Rational] = Array.empty

  def postFixedPrioriPreemtpiveConstraint(schedulerIdx: Int): Unit = {
    chocoModel.post(
      new Constraint(
        s"scheduler_${schedulerIdx}_iter_prop",
        FixedPriorityPreemptivePropagator(
          schedulerIdx,
          priorities,
          periods,
          deadlines,
          wcets,
          taskExecution,
          responseTimes,
          blockingTimes,
          durations
        )
      )
    )
  }
  // TODO: check if this is still correct after durations become vector instead of matrix
  // private def postFixedPrioriPreemtpiveConstraintByTask(taskIdx: Int, schedulerIdx: Int): Unit =
  //   if (sufficientRMSchedulingPoints(taskIdx).isEmpty) then
  //     chocoModel.ifThen(
  //       taskExecution(taskIdx)(schedulerIdx),
  //       responseTimes(taskIdx)
  //         .ge(
  //           durations(taskIdx)(schedulerIdx)
  //             .add(blockingTimes(taskIdx))
  //         )
  //         .decompose
  //     )
  //   else {
  //     chocoModel.ifThen(
  //       taskExecution(taskIdx)(schedulerIdx),
  //       chocoModel.or(
  //         sufficientRMSchedulingPoints(taskIdx).map(t => {
  //           responseTimes(taskIdx)
  //             .ge(
  //               durations(taskIdx)(schedulerIdx)
  //                 .add(blockingTimes(taskIdx))
  //                 .add(
  //                   chocoModel
  //                     .sum(
  //                       s"fp_interference${taskIdx}_${schedulerIdx}",
  //                       durations.zipWithIndex
  //                         .filter((ws, k) => k != taskIdx)
  //                         .filter((ws, k) =>
  //                           // leave tasks k which i occasionally block
  //                           priorities(k) >= priorities(taskIdx)
  //                         )
  //                         .map((w, k) => {
  //                           w(schedulerIdx)
  //                             .mul({
  //                               // we use floor + 1 intead of ceil due to the fact
  //                               // that ceil(s) is not a strict GT but a GE implementation
  //                               (t / (
  //                                 periods(k)
  //                               )).floor.toInt + 1
  //                             })
  //                             .intVar
  //                         })
  //                         .toArray: _*
  //                     )
  //                 )
  //             )
  //             .decompose
  //         }): _*
  //       )
  //     )
  //   }

}
