package idesyde.identification.choco.models

import idesyde.identification.choco.interfaces.ChocoModelMixin
import idesyde.identification.models.platform.TiledMultiCorePlatformMixin
import spire.algebra._
import spire.math._
import spire.implicits._
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.Task
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.color.LargestDegreeFirstColoring
import org.chocosolver.solver.constraints.Constraint
import idesyde.utils.CoreUtils.wfor
import org.jgrapht.graph.SimpleGraph

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
    for (ce <- commElems) {
      // chocoModel.allDifferentExcept0(messages.map(m => virtualChannelForMessage(m)(ce))).post()
    }
    for (
      src <- procElems;
      dst <- procElems;
      if src != dst;
      c  <- messages;
      ce <- commElemsPath(src)(dst)
    ) {
      chocoModel.ifThen(
        messageIsCommunicated(c)(src)(dst),
        chocoModel.arithm(virtualChannelForMessage(c)(ce), ">", 0)
      )
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
    for (ce <- commElems) {
      val areEquals =
        commElems.filter(otherCe => ce != otherCe && commElemsMustShareChannel(ce)(otherCe))
      for (m <- messages) {
        chocoModel
          .allEqual((ce +: areEquals).map(comms => virtualChannelForMessage(m)(comms)): _*)
          .post()
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

    chocoModel.post(
      new Constraint(
        "tile_async_vc",
        CommunicationContetionFreePropagator(
          procElems,
          commElems,
          messages,
          procElems.map(src => procElems.map(dst => commElemsPath(src)(dst))),
          messages.map(m => commElems.map(ce => virtualChannelForMessage(m)(ce))),
          messages.map(m =>
            procElems.map(src => procElems.map(dst => messageIsCommunicated(m)(src)(dst)))
          )
        )
      )
    )
  }

  class CommunicationContetionFreePropagator(
      val procElems: Array[Int],
      val commElems: Array[Int],
      val messages: Array[Int],
      val commElemsPath: Array[Array[Array[Int]]],
      val messageVirtualChannels: Array[Array[IntVar]],
      val messageIsCommunicated: Array[Array[Array[IntVar]]]
  ) extends Propagator[IntVar](
        messageIsCommunicated.flatten.flatten ++ messageVirtualChannels.flatten,
        PropagatorPriority.VERY_SLOW,
        false
      ) {

    // def numCommElems = messageVirtualChannels.head.size
    // def numMessages = messageVirtualChannels.head.size
    // def numTiles = messageIsCommunicated.head.size

    val interferenceGraph: SimpleGraph[Int, DefaultEdge] = SimpleGraph
      .createBuilder[Int, DefaultEdge](() => DefaultEdge())
      .addVertices(messages: _*)
      .build()
    val coloringAlgorithm = LargestDegreeFirstColoring(interferenceGraph)

    def clearInterferences() = wfor(0, _ < messages.size, _ + 1) { mi =>
      // wfor(mi + 1, _ < messages.size, _ + 1) { mj =>
      //   if (interferenceGraph.containsEdge(messages(mi), messages(mj))) {
      //     interferenceGraph.removeEdge(messages(mi), messages(mj))
      //   }
      // }
      interferenceGraph.removeVertex(messages(mi))
    }

    def addInPlaceInterferences() = wfor(0, _ < messages.size - 1, _ + 1) { mi =>  
        wfor(0, _ < procElems.size, _ + 1) { srci =>
          wfor(0, _ < procElems.size, _ + 1) { dsti =>
            if (srci != dsti && messageIsCommunicated(mi)(srci)(dsti).getLB() > 0) {
              wfor(mi + 1, _ < messages.size, _ + 1) { mj =>
                wfor(0, _ < procElems.size, _ + 1) { srcj =>
                  if (srci != srcj) {
                    wfor(0, _ < procElems.size, _ + 1) { dstj =>
                      if (srcj != dstj && messageIsCommunicated(mj)(srcj)(dstj).getLB() > 0) {
                        wfor(0, _ < commElems.size, _ + 1) { ce =>
                          if (
                            commElemsPath(srci)(dsti).contains(ce) && commElemsPath(srcj)(dstj)
                              .contains(ce)
                          ) {
                            if (!interferenceGraph.containsVertex(messages(mi))) then interferenceGraph.addVertex(messages(mi))
                            if (!interferenceGraph.containsVertex(messages(mj))) then interferenceGraph.addVertex(messages(mj))
                            interferenceGraph.addEdge(messages(mi), messages(mj))
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
      }
    }

    def propagate(evtmask: Int): Unit = {
      // println("vc propagate")
      // var clash = false
      clearInterferences()
      addInPlaceInterferences()
      val coloring = coloringAlgorithm.getColoring()
      // println(coloring.toString())
      val coloredVertices = coloring.getColors()
      wfor(0, _ < messages.size - 1, _ + 1) { miIdx =>
        val mi = messages(miIdx)
        if (coloredVertices.containsKey(mi)) {
          wfor(miIdx, _ < messages.size, _ + 1) { mjIdx =>
            val mj = messages(mjIdx)
            if (coloredVertices.containsKey(mj) && coloredVertices.get(mi) != coloredVertices.get(mj)) {
              wfor(0, _ < commElems.size, _ + 1) { ce =>
                // there is a possible clash, do something
                if (messageVirtualChannels(miIdx)(ce).getUB() >= messageVirtualChannels(mjIdx)(ce).getLB()) {
                  // val mid = (messageVirtualChannels(miIdx)(ce).getUB() + messageVirtualChannels(mjIdx)(ce).getLB()) / 2
                  // model.allDifferent(messageVirtualChannels(miIdx)(ce), messageVirtualChannels(mjIdx)(ce)).post()
                  // println(s"${mi} and $mj are [${messageVirtualChannels(miIdx)(ce).getLB()},${messageVirtualChannels(miIdx)(ce).getUB()}] [${messageVirtualChannels(mjIdx)(ce).getLB()}, ${messageVirtualChannels(mjIdx)(ce).getUB()}]")
                  messageVirtualChannels(miIdx)(ce).updateUpperBound(messageVirtualChannels(miIdx)(ce).getLB(), this)
                  messageVirtualChannels(mjIdx)(ce).updateLowerBound(messageVirtualChannels(miIdx)(ce).getLB() + 1, this)
                }
              }
            }
          }
        }
      }
    }

    def isEntailed(): ESat = {
      // val possible = true
      // clearInterferences()
      // addInPlaceInterferences()
      // val coloring = coloringAlgorithm.getColoring()
      // wfor(0, _ < commElems.size && possible, _ + 1) { ce =>
      //   wfor(0, _ < messages.size, _ + 1) { miIdx =>
      //   // there is a possible clash, do something
      //   if (messageVirtualChannels(miIdx)(ce).getLB() <= messageVirtualChannels(mjIdx)(ce).getLB()) {
          
      //   }
      // }
      // we can do this simple check since the propagation will already throw an error
      // when there is more colors than VCs available
      if (messageVirtualChannels.flatten.forall(v => v.isInstantiated())) then ESat.TRUE
      else ESat.UNDEFINED
    }

  }

}
