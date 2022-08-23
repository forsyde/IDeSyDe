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
      ce <- commElems
    ) {
      // chocoModel.allDifferentExcept0(messages.map(m => virtualChannelForMessage(m)(ce))).post()
    }
    for (
      src <- procElems;
      dst <- procElems;
      if src != dst;
      c <- messages;
      ce <- commElemsPath(src)(dst)
    ) {
      chocoModel.ifThen(messageIsCommunicated(c)(src)(dst), chocoModel.arithm(virtualChannelForMessage(c)(ce), ">", 0))
    }
    // for (
    //   srci <- procElems;
    //   srcj <- procElems;
    //   if srci != srcj;
    //   dsti <- procElems;
    //   dstj <- procElems;
    //   ce   <- commElemsPath(srci)(dsti);
    //   if commElemsPath(srcj)(dstj).contains(ce)
    // ) {
    //   // println(s"$srci -> $dsti and $srcj -> $dstj crash at $ce")
    //   for (
    //     (mi, mii) <- messages.zipWithIndex.dropRight(1);
    //     mj <- messages.drop(
    //       mii + 1
    //     ) // it might seems strange that we consider the same message, but it is required for proper allocation
    //   ) {
    //     // println(s"posting for $mi and $mj")
    //     chocoModel.ifThen(
    //       chocoModel.and(messageIsCommunicated(mi)(srci)(dsti), 
    //         messageIsCommunicated(mj)(srcj)(dstj)),
    //       chocoModel.notAllEqual(virtualChannelForMessage(mi)(ce), virtualChannelForMessage(mj)(ce))
    //     )
    //   }
    // }

    // now we make sure that adjacent comm elems that must have the same channel for the same
    // message, indeed do.
    for (
      ce <- commElems
    ) {
      val areEquals = commElems.filter(otherCe => ce != otherCe && commElemsMustShareChannel(ce)(otherCe))
      for (m <- messages) {
        chocoModel.allEqual((ce +: areEquals).map(comms => virtualChannelForMessage(m)(comms)):_*).post()
      }
      // chocoModel.allEqual()
      // chocoModel.ifThen(
      //   messageIsCommunicated(m)(src)(dst),
        
      // )
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
