package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar

trait SDFTimingAnalysisSASMixin extends ChocoModelMixin {

  def actors: Array[Int]
  def schedulers: Array[Int]

  def maxRepetitionsPerActors(actorId: Int): Int
  def isStaticCyclic(schedulerId: Int): Boolean

  def actorMapping(actorId: Int)(schedulerId: Int): BoolVar
  def startTimeOfActorFirings(actorId: Int)(instance: Int): IntVar
  def numActorsScheduledSlotsInStaticCyclic(actorId: Int)(tileId: Int)(slotId: Int): IntVar

  def postOnlySASOnStaticCyclicSchedulers(): Unit = {
    def slots(i: Int)(j: Int) =
      (0 until actors.size).map(numActorsScheduledSlotsInStaticCyclic(i)(j)(_)).toArray
    actors.zipWithIndex.foreach((a, i) => {
      // chocoModel.sum(numActorsScheduledSlotsInStaticCyclic(i).flatten, "=", maxRepetitionsPerActors(a)).post()
      schedulers.zipWithIndex
        .filter((s, j) => isStaticCyclic(s))
        .foreach((s, j) => {
          if (actors.size > 1) {
              chocoModel
                .min(s"0_in_${i}_${j}", (0 until actors.size).map(slots(i)(j)(_)): _*)
                .eq(0)
                .post()
          }
          chocoModel.ifThenElse(
            actorMapping(i)(j),
            chocoModel.sum(slots(i)(j), ">=", 1),
            chocoModel.sum(slots(i)(j), "=", 0)
            )
          chocoModel.atMostNValues(slots(i)(j), chocoModel.intVar(2), true).post()
          chocoModel.sum(slots(i)(j), "<=", maxRepetitionsPerActors(a)).post()
        })
    })
  }

}
