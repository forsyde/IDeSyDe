package idesyde.identification.choco.models

import idesyde.identification.choco.interfaces.ChocoModelMixin
import idesyde.identification.models.platform.TiledMultiCorePlatformMixin
import spire.algebra._
import spire.math._
import spire.implicits._
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.Task

trait TileAsyncInterconnectCommsMixin extends ChocoModelMixin {

  def numProcElems: Int
  def numCommElems: Int
  def numDataSent: Int
  def dataTravelDuration(dataIdx: Int)(ceIdx: Int): Int
  def commElemsPath(srcIdx: Int)(dstIdx: Int): Array[Int]
  
  def messageCommunication(dataIdx: Int)(srcIdx: Int)(dstIdx: Int): BoolVar
  def commStart(dataIdx: Int)(ceIdx: Int): IntVar
  def commEnd(dataIdx: Int)(ceIdx: Int): IntVar
  def commElemLoad(ceIdx: Int): IntVar

  def virtualOptionalDurations(dataIdx: Int)(ceIdx: Int): IntVar = {
    val timeVar = chocoModel.intVar(s"virtual_dur_${dataIdx}_${ceIdx}", Array(0, dataTravelDuration(dataIdx)(ceIdx)))
    for (
        src <- 0 until numProcElems - 1;
        dst <- src + 1 until numProcElems;
        path = commElemsPath(src)(dst)
        if !path.isEmpty && path.contains(ceIdx)
    ) {
        chocoModel.ifThenElse(
            messageCommunication(dataIdx)(src)(dst), 
            timeVar.eq(dataTravelDuration(dataIdx)(ceIdx)).decompose(),
            timeVar.eq(0).decompose()
        )
    }
    timeVar
  }
  
  def virtualCommTasks = (0 until numDataSent)
    .map(mi =>
      (0 until numCommElems).map(ci => Task(commStart(mi)(ci), virtualOptionalDurations(mi)(ci), commEnd(mi)(ci))).toArray
    )
    .toArray

  def postTileAsyncInterconnectComms(): Unit = {}

}
