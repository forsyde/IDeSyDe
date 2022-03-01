package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoCPDecisionModel
import org.chocosolver.solver.variables.IntVar
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.FastMath

trait FixedPriorityConstraintsMixin extends ChocoCPDecisionModel {

  val priorities: Array[Int]
  val periods: Array[BigFraction]
  val taskExecution: Array[IntVar]
  val responseTimes: Array[IntVar]
  val blockingTimes: Array[IntVar]
  val wcet: Array[Array[IntVar]]

  def sufficientRMSchedulingPoints(taskIdx: Int): Array[BigFraction]

  def postFixedPrioriPreemtpiveConstraint(schedulerIdx: Int): Unit =
    taskExecution.zipWithIndex.foreach((taskVar, i) => {
      postFixedPrioriPreemtpiveConstraintByTask(i, schedulerIdx)
    })

  private def postFixedPrioriPreemtpiveConstraintByTask(taskIdx: Int, schedulerIdx: Int): Unit =
    if (sufficientRMSchedulingPoints(taskIdx).isEmpty) then
      chocoModel.ifThen(
        taskExecution(taskIdx).eq(schedulerIdx).decompose,
        responseTimes(taskIdx)
          .ge(
            wcet(taskIdx)(schedulerIdx)
              .add(blockingTimes(taskIdx))
          )
          .decompose
      )
    else {
      chocoModel.ifThen(
        taskExecution(taskIdx).eq(schedulerIdx).decompose,
        chocoModel.or(
          sufficientRMSchedulingPoints(taskIdx).map(t => {
            responseTimes(taskIdx)
              .ge(
                wcet(taskIdx)(schedulerIdx)
                  .add(blockingTimes(taskIdx))
                  .add(
                    chocoModel
                      .sum(
                        s"fp_interference${taskIdx}_${schedulerIdx}",
                        wcet.zipWithIndex
                          .filter((ws, k) => k != taskIdx)
                          .filter((ws, k) =>
                            // leave tasks k which i occasionally block
                            priorities(k) >= priorities(taskIdx)
                          )
                          .map((ws, k) => {
                            ws(schedulerIdx)
                              .mul({
                                // we use floor + 1 intead of ceil due to the fact
                                // that ceil(s) is not a strict GT but a GE implementation
                                FastMath
                                  .floor(
                                    t.divide(
                                      periods(k)
                                    ).doubleValue
                                  )
                                  .toInt + 1
                              })
                              .intVar
                          })
                          .toArray: _*
                      )
                  )
              )
              .decompose
          }): _*
        )
      )
    }
}
