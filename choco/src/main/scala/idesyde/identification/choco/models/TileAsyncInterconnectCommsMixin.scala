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

  def procElems: Array[Int]
  def commElems: Array[Int]
  def messages: Array[Int]
  def messageTravelTimePerVirtualChannelById(messageId: Int)(ceId: Int): Int
  def numVirtualChannels(ceId: Int): Int
  def commElemsPath(srcId: Int)(dstId: Int): Array[Int]
  def commElemsMustShareChannel(ceId: Int)(otherCeId: Int): Boolean

  def virtualChannelForMessage(messageId: Int)(ceId: Int): IntVar
  def messageIsCommunicated(messageId: Int)(srcId: Int)(dstId: Int): BoolVar
  def messageTravelDuration(messageId: Int)(srcId: Int)(dstId: Int): IntVar

  def postTileAsyncInterconnectComms(): Unit = {
    // first, make sure that data from different sources do not collide in any comm. elem
    for (
      srci <- procElems;
      srcj <- procElems;
      if srci != srcj;
      mi <- messages;
      mj <- messages; // it might seems strange that we consider the same message, but it is required for proper allocation
      if mi != mj;
      dsti <- procElems;
      dstj <- procElems;
      ce   <- commElemsPath(srci)(dsti)
      if commElemsPath(srcj)(dstj).contains(ce)
    ) {
      println(s"$mi and $mj crash in $srci, $dsti ; $srcj, $dstj at $ce")
      chocoModel.ifThen(
        messageIsCommunicated(mi)(srci)(dsti)
          .and(messageIsCommunicated(mj)(srcj)(dstj))
          .decompose(),
        virtualChannelForMessage(mi)(ce).ne(virtualChannelForMessage(mj)(ce)).decompose()
      )
    }

    // now we make sure that adjacent comm elems that must have the same channel for the same
    // message, indeed do.
    for (
      ci <- commElems;
      cj <- commElems;
      if ci != cj && commElemsMustShareChannel(ci)(cj);
      m   <- messages;
      src <- procElems;
      dst <- procElems;
      if commElemsPath(src)(dst).contains(ci) && commElemsPath(src)(dst).contains(cj)
    ) {
      chocoModel.ifThen(
        messageIsCommunicated(m)(src)(dst),
        virtualChannelForMessage(m)(ci).eq(virtualChannelForMessage(m)(cj)).decompose()
      )
    }

    // and finally, we calculate the travel time, for each message, between each src and dst
    for (
      m   <- messages;
      src <- procElems;
      dst <- procElems;
      if src != dst
    ) {
      chocoModel.ifThen(
        messageIsCommunicated(m)(src)(dst),
        messageTravelDuration(m)(src)(dst)
          .eq(commElemsPath(src)(dst).map(ce => messageTravelTimePerVirtualChannelById(m)(ce)).sum)
          .decompose()
      )
    }
  }

}
