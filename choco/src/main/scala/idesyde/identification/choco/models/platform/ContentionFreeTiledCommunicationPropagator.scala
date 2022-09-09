package idesyde.identification.choco.models.platform

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.Propagator
import org.jgrapht.graph.SimpleGraph
import org.jgrapht.graph.DefaultEdge
import idesyde.utils.CoreUtils.wfor
import org.chocosolver.solver.constraints.PropagatorPriority
import org.jgrapht.alg.color.LargestDegreeFirstColoring
import org.chocosolver.util.ESat

class ContentionFreeTiledCommunicationPropagator(
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
                    wfor(0, _ < commElemsPath(srci)(dsti).size, _ + 1) { ce =>
                      if (commElemsPath(srcj)(dstj).contains(commElemsPath(srci)(dsti)(ce))) {
                        if (!interferenceGraph.containsVertex(messages(mi))) then
                          interferenceGraph.addVertex(messages(mi))
                        if (!interferenceGraph.containsVertex(messages(mj))) then
                          interferenceGraph.addVertex(messages(mj))
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
          if (
            coloredVertices.containsKey(mj) && coloredVertices.get(mi) != coloredVertices.get(mj)
          ) {
            wfor(0, _ < commElems.size, _ + 1) { ce =>
              // there is a possible clash, do something
              if (
                messageVirtualChannels(miIdx)(ce).getUB() >= messageVirtualChannels(mjIdx)(ce)
                  .getLB()
              ) {
                // val mid = (messageVirtualChannels(miIdx)(ce).getUB() + messageVirtualChannels(mjIdx)(ce).getLB()) / 2
                // model.allDifferent(messageVirtualChannels(miIdx)(ce), messageVirtualChannels(mjIdx)(ce)).post()
                // println(s"${mi} and $mj are [${messageVirtualChannels(miIdx)(ce).getLB()},${messageVirtualChannels(miIdx)(ce).getUB()}] [${messageVirtualChannels(mjIdx)(ce).getLB()}, ${messageVirtualChannels(mjIdx)(ce).getUB()}]")
                messageVirtualChannels(miIdx)(ce)
                  .instantiateTo(messageVirtualChannels(miIdx)(ce).getLB(), this)
                messageVirtualChannels(mjIdx)(ce)
                  .updateLowerBound(messageVirtualChannels(miIdx)(ce).getLB() + 1, this)
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
