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
  def numMessages: Int
  def messageTravelTimePerVirtualChannelById(messageIdx: Int)(ceId: Int): Int
  def numVirtualChannels(ceIdx: Int): Int
  def commElemsPath(srcIdx: Int)(dstIdx: Int): Array[Int]
  def commElemsMustShareChannel(ceIdx: Int)(otherCeIdx: Int): Boolean
  
  def virtualChannelForMessage(messageIdx: Int)(ceIdx: Int): IntVar
  def messageIsCommunicated(messageIdx: Int)(srcIdx: Int)(dstIdx: Int): BoolVar
  def messageTravelDuration(messageIdx: Int)(srcIdx: Int)(dstIdx: Int): IntVar

  def postTileAsyncInterconnectComms(): Unit = {
    // first, make sure that data from different sources do not collide in any comm. elem
    for (
      srci <- 0 until numProcElems;
      srcj <- 0 until numProcElems;
      if srci != srcj;
      mi <- 0 until numMessages;
      mj <- mi until numMessages; // it might seems strange that we consider the same message, but it is required for proper allocation
      ce <- 0 until numCommElems;
      dsti <- 0 until numProcElems;
      dstj <- 0 until numProcElems;
      if commElemsPath(srci)(dsti).contains(ce) && commElemsPath(srcj)(dstj).contains(ce)
    ) {
      chocoModel.ifThen(messageIsCommunicated(mi)(srci)(dsti).and(messageIsCommunicated(mj)(srcj)(dstj)).decompose(),
        virtualChannelForMessage(mi)(ce).ne(virtualChannelForMessage(mj)(ce)).decompose()
      )
    }

    // now we make sure that adjacent comm elems that must have the same channel for the same
    // message, indeed do.
    for (
      ci <- 0 until numCommElems;
      cj <- ci + 1 until numCommElems;
      if commElemsMustShareChannel(ci)(cj);
      m <- 0 until numMessages;
      src <- 0 until numProcElems;
      dst <- 0 until numProcElems;
      if commElemsPath(src)(dst).contains(ci) && commElemsPath(src)(dst).contains(cj)
    ) {
      chocoModel.ifThen(messageIsCommunicated(m)(src)(dst),
        virtualChannelForMessage(m)(ci).eq(virtualChannelForMessage(m)(cj)).decompose()
      )
    }

    // and finally, we calculate the travel time, for each message, between each src and dst
    for (
      m <- 0 until numMessages;
      src <- 0 until numProcElems;
      dst <- 0 until numProcElems;
      if src != dst
    ) {
      chocoModel.ifThen(messageIsCommunicated(m)(src)(dst),
        messageTravelDuration(m)(src)(dst).eq(commElemsPath(src)(dst).map(ce => messageTravelTimePerVirtualChannelById(m)(ce)).sum).decompose()
      )
    }
  }

}
