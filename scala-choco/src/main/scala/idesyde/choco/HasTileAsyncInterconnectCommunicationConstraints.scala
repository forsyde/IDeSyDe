package idesyde.choco

import idesyde.identification.choco.interfaces.ChocoModelMixin
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
import org.jgrapht.graph.SimpleGraph
import idesyde.identification.choco.models.platform.ContentionFreeTiledCommunicationPropagator
import org.chocosolver.solver.Model
import idesyde.utils.HasUtils

trait HasTileAsyncInterconnectCommunicationConstraints(
    // val commElemsMustShareChannel: Array[Array[Boolean]],
) extends HasUtils {

  def postTileAsyncInterconnectComms(
      chocoModel: Model,
      procElems: Array[String],
      commElems: Array[String],
      messages: Array[Int],
      messageTravelTimePerVirtualChannel: Array[Array[Int]],
      numVirtualChannels: Array[Int],
      commElemsPaths: (String) => (String) => Array[String]
  ): (Array[Array[IntVar]], Array[Array[BoolVar]], Array[Array[Array[IntVar]]]) = {
    val numProcElems = procElems.size
    val numCommElems = commElems.size
    val numMessages  = messages.size

    val numVirtualChannelsForProcElem: Array[Array[IntVar]] =
      procElems.zipWithIndex.map((pe, i) => {
        commElems.zipWithIndex.map((ce, j) => {
          chocoModel.intVar(
            s"vc($pe,$ce)",
            0,
            numVirtualChannels(j),
            true
          )
        })
      })

    val procElemSendsDataToAnother: Array[Array[BoolVar]] =
      procElems.zipWithIndex.map((src, i) => {
        procElems.zipWithIndex.map((dst, j) => {
          if (!commElemsPaths(src)(dst).isEmpty)
            chocoModel.boolVar(s"sendsData(${i},${j})")
          else
            chocoModel.boolVar(s"sendsData(${i},${j})", false)
        })
      })

    val messageTravelDuration: Array[Array[Array[IntVar]]] = messages.zipWithIndex.map((c, ci) => {
      procElems.zipWithIndex.map((src, i) => {
        procElems.zipWithIndex.map((dst, j) => {
          if (i != j) {
            chocoModel.intVar(
              s"commTime(${c},${src},${dst})",
              0,
              commElemsPaths(src)(dst)
                .map(ce => messageTravelTimePerVirtualChannel(ci)(commElems.indexOf(ce)))
                .sum,
              true
            )
          } else
            chocoModel.intVar(0)
        })
      })
    })

    // first, make sure that data from different sources do not collide in any comm. elem
    for (ce <- 0 until numCommElems) {
      chocoModel
        .sum(numVirtualChannelsForProcElem.map(cVec => cVec(ce)), "<=", numVirtualChannels(ce))
        .post()
    }
    // no make sure that the virtual channels are allocated when required
    for (
      (p, src)  <- procElems.zipWithIndex;
      (pp, dst) <- procElems.zipWithIndex
      if src != dst;
      // c  <- 0 until numMessages;
      ce <- commElemsPaths(p)(pp)
    ) {
      chocoModel.ifThen(
        procElemSendsDataToAnother(src)(dst),
        chocoModel.arithm(numVirtualChannelsForProcElem(src)(commElems.indexOf(ce)), ">", 0)
      )
    }
    for (
      (p, src)  <- procElems.zipWithIndex;
      (pp, dst) <- procElems.zipWithIndex
      if src != dst;
      c <- 0 until numMessages
    ) {
      val singleChannelSum = commElemsPaths(p)(pp)
        .map(ce => messageTravelTimePerVirtualChannel(c)(commElems.indexOf(ce)))
        .sum
      if (
        commElemsPaths(p)(pp)
          .map(ce => numVirtualChannelsForProcElem(src)(commElems.indexOf(ce)))
          .size == 0
      ) then println(s"$p to $pp")
      val minVCInPath = chocoModel.min(
        s"minVCInPath($src, $dst)",
        commElemsPaths(p)(pp)
          .map(ce => numVirtualChannelsForProcElem(src)(commElems.indexOf(ce))): _*
      )
      chocoModel.ifThenElse(
        procElemSendsDataToAnother(src)(dst),
        chocoModel.arithm(
          messageTravelDuration(c)(src)(dst),
          "*",
          minVCInPath,
          "=",
          singleChannelSum
        ),
        chocoModel.arithm(
          messageTravelDuration(c)(src)(dst),
          "=",
          0
        )
      )
    }
    (numVirtualChannelsForProcElem, procElemSendsDataToAnother, messageTravelDuration)
  }

}
