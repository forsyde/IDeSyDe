package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar

trait SDFTimingAnalysisSASMixin extends ChocoModelMixin {

  def actors: Array[Int]
  def schedulers: Array[Int]

  def maxRepetitionsPerActors(actorId: Int): Int

  def actorMapping(actorId: Int)(schedulerId: Int): BoolVar
  def startLatency(schedulerId: Int): IntVar
  def slotMaxDuration(schedulerId: Int): IntVar
  def numActorsScheduledSlotsInStaticCyclic(actorId: Int)(tileId: Int)(slotId: Int): IntVar

  def slotsInAll(i: Int) =
    (0 until actors.size)
      .map(q =>
        (0 until schedulers.size).map(s => numActorsScheduledSlotsInStaticCyclic(i)(s)(q)).toArray
      )
      .toArray
  def slots(i: Int)(j: Int) =
    (0 until actors.size).map(numActorsScheduledSlotsInStaticCyclic(i)(j)(_)).toArray

  def postOnlySASOnStaticCyclicSchedulers(): Unit = {
    actors.zipWithIndex.foreach((a, i) => {
      chocoModel.sum(slotsInAll(i).flatten, "=", maxRepetitionsPerActors(a)).post()
      schedulers.zipWithIndex
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
