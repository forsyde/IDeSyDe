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

  private val numCommElems = messageVirtualChannels.head.size
  // def numMessages = messageVirtualChannels.head.size
  // def numTiles = messageIsCommunicated.head.size

  val interferenceGraphPerComm: Array[SimpleGraph[Int, DefaultEdge]] = commElems.map(ce => SimpleGraph
    .createBuilder[Int, DefaultEdge](() => DefaultEdge())
    .addVertices(messages: _*)
    .build())
  val coloringAlgorithms = interferenceGraphPerComm.map(g => LargestDegreeFirstColoring(g)) 

  def clearInterferences() = {
    wfor(0, _ < numCommElems, _ + 1) { ceIdx =>
      wfor(0, _ < messages.size, _ + 1) { mi =>
        interferenceGraphPerComm(ceIdx).removeVertex(messages(mi))
      }
    }
  }

  def addInPlaceInterferences(): Boolean = {
    var allCommunicationsInstantiated = false
    wfor(0, _ < messages.size - 1, _ + 1) { mi =>
      wfor(0, _ < procElems.size, _ + 1) { srci =>
        wfor(0, _ < procElems.size, _ + 1) { dsti =>
          if (srci != dsti && messageIsCommunicated(mi)(srci)(dsti).getLB() > 0) {
            // this extra line is not fully part of the reference, but it is here for performance reasons,
            // i.e. to avoid duplication of looping
            allCommunicationsInstantiated = allCommunicationsInstantiated && messageIsCommunicated(mi)(srci)(dsti).isInstantiated()
            wfor(mi + 1, _ < messages.size, _ + 1) { mj =>
              wfor(0, _ < procElems.size, _ + 1) { srcj =>
                if (srci != srcj) {
                  wfor(0, _ < procElems.size, _ + 1) { dstj =>
                    if (srcj != dstj && messageIsCommunicated(mj)(srcj)(dstj).getLB() > 0) {
                      wfor(0, _ < commElemsPath(srci)(dsti).size, _ + 1) { ceIdx =>
                        if (commElemsPath(srcj)(dstj).contains(commElemsPath(srci)(dsti)(ceIdx))) {
                          if (!interferenceGraphPerComm(ceIdx).containsVertex(messages(mi))) then
                            interferenceGraphPerComm(ceIdx).addVertex(messages(mi))
                          if (!interferenceGraphPerComm(ceIdx).containsVertex(messages(mj))) then
                            interferenceGraphPerComm(ceIdx).addVertex(messages(mj))
                          interferenceGraphPerComm(ceIdx).addEdge(messages(mi), messages(mj))
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
    allCommunicationsInstantiated
  }

  def propagate(evtmask: Int): Unit = {
    println("vc propagate")
    // var clash = false
    clearInterferences()
    val isAllCommunicationsInstantiated = addInPlaceInterferences()
    wfor(0, _ < numCommElems, _ + 1) { ceIdx =>
      val coloring = coloringAlgorithms(ceIdx).getColoring()
      println(ceIdx + " :: " + coloring.toString())
      val coloredVertices = coloring.getColors()
      wfor(0, _ < messages.size, _ + 1) { mIdx =>
        val message = messages(mIdx)
        if (coloredVertices.containsKey(message)) {
            // there is a possible clash, instantiate the lower message to the first available slot
          if (!messageVirtualChannels(mIdx)(ceIdx).isInstantiated()) {
            println(s"LB $message in $ceIdx to ${coloredVertices.get(message) + 1}")
            messageVirtualChannels(mIdx)(ceIdx).instantiateTo(coloredVertices.get(message) + 1, this)
          }
        }
      }
    }
      //   wfor(mLowerIdx + 1, _ < messages.size, _ + 1) { mHigherIdx =>
      //     val mHigher = messages(mHigherIdx)
      //     if (
      //       coloredVertices.containsKey(mHigher) && coloredVertices.get(mLower) != coloredVertices.get(mHigher)
      //     ) {
      //       wfor(0, _ < commElems.size, _ + 1) { ce =>
      //         // there is a possible clash, instantiate the lower message to the first available slot
      //         if (!messageVirtualChannels(mLowerIdx)(ce).isInstantiated()) {
      //           messageVirtualChannels(mLowerIdx)(ce).instantiateTo(coloredVertices.get(mLower), this)
      //         }
      //         if (!messageVirtualChannels(mHigherIdx)(ce).isInstantiated()) {
      //           messageVirtualChannels(mHigherIdx)(ce).removeValue(coloredVertices.get(mLower), this)
      //         }
      //       //   if (
      //       //     messageVirtualChannels(mLowerIdx)(ce).getUB() >= messageVirtualChannels(mHigherIdx)(ce)
      //       //       .getLB()
      //       //   ) {
      //       //     // val mid = (messageVirtualChannels(miIdx)(ce).getUB() + messageVirtualChannels(mjIdx)(ce).getLB()) / 2
      //       //     // model.allDifferent(messageVirtualChannels(miIdx)(ce), messageVirtualChannels(mjIdx)(ce)).post()
      //       //     // messageVirtualChannels(mLowerIdx)(ce)
      //       //     //   .instantiateTo(messageVirtualChannels(mLowerIdx)(ce).getLB(), this)
      //       //     println(s"LB $mHigher in $ce to ${messageVirtualChannels(mLowerIdx)(ce).getLB() + 1} because of $mLower")
      //       //     messageVirtualChannels(mHigherIdx)(ce)
      //       //     .updateLowerBound(messageVirtualChannels(mLowerIdx)(ce).getLB() + 1, this)
      //       //   }
      //       }
      //     }
      //   }
      // }
    // }
    // finally, if all transmissions are defined, we finish the propagations by assingning
    // all variables to their lowerst values, since the previous algorithm would have made
    // it contention free
    if (isAllCommunicationsInstantiated) {
      wfor(0, _ < messages.size, _ + 1) { m =>
        wfor(0, _ < commElems.size, _ + 1) { ce =>
          messageVirtualChannels(m)(ce).instantiateTo(messageVirtualChannels(m)(ce).getLB(), this)
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
