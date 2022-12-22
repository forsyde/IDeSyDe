package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoCPForSyDeDecisionModel
import org.chocosolver.solver.Model
import forsyde.io.java.core.Vertex
import org.chocosolver.solver.Solution
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult
import idesyde.identification.choco.models.ManyProcessManyMessageMemoryConstraintsMixin
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import idesyde.identification.forsyde.models.mixed.SDFToSchedTiledHW
import idesyde.identification.forsyde.ForSyDeIdentificationRule
import idesyde.identification.choco.models.TileAsyncInterconnectCommsModule
import spire.math.Rational
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.variables.Largest
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMedian
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMax
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsModule
import org.chocosolver.solver.search.strategy.strategy.FindAndProve
import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import org.chocosolver.solver.exception.ContradictionException
import scala.collection.mutable.Buffer

class ConMonitorObj2(val model: ChocoSDFToSChedTileHW2) extends IMonitorContradiction {

  def onContradiction(cex: ContradictionException): Unit = {
    println(cex.toString())
    // println(
    //   model.tileAnalysisModule.procElemSendsDataToAnother
    //     .map(_.mkString(", "))
    //     .mkString("\n")
    // )
    // println(
    //   model.dataFlows
    //     .map(_.mkString(", "))
    //     .mkString("\n")
    // )
    // println(
    //   model.tileAnalysisModule.numVirtualChannelsForProcElem
    //     .map(_.filter(_.getValue() > 0).mkString(", "))
    //     .mkString("\n")
    // )
    println(model.memoryMappingModule.processesMemoryMapping.mkString(", "))
    println(model.sdfAnalysisModule.jobOrder.mkString(", "))
    println(model.sdfAnalysisModule.jobStartTime.mkString(", "))
    println(model.sdfAnalysisModule.invThroughputs.mkString(", "))
    println(model.sdfAnalysisModule.numMappedElements)
    println(model.maxLatency.toString())
    // println(
    //   model.tileAnalysisModule.messageTravelDuration
    //     .map(_.map(_.mkString(",")).mkString("; "))
    //     .mkString("\n")
    // )
  }
}

final case class ChocoSDFToSChedTileHW2(
    val dse: SDFToSchedTiledHW
)(using Fractional[Rational])
    extends ChocoCPForSyDeDecisionModel
    with ChocoModelMixin(shouldLearnSignedClauses = false) {

  val chocoModel: Model = Model()

  // chocoModel.getSolver().plugMonitor(ConMonitorObj2(this))

  // section for time multiplier calculation
  val timeValues =
    (dse.wcets.flatten ++ dse.platform.tiledDigitalHardware.maxTraversalTimePerBitPerRouter)
  var timeMultiplier = 1L
  while (
    timeValues
      .map(t => t * (timeMultiplier))
      .exists(d =>
        d.numerator <= d.denominator / 1000L
      ) // ensure that the numbers magnitudes still stay sane
    &&
    timeValues
      .map(t => t * (timeMultiplier))
      .sum < Int.MaxValue / 1000 - 1
  ) {
    timeMultiplier *= 10
  }

  // do the same for memory numbers
  val memoryValues = dse.platform.tiledDigitalHardware.memories.map(_.getSpaceInBits().toLong) ++
    dse.sdfApplications.messagesMaxSizes ++
    dse.sdfApplications.processSizes
  var memoryDivider = 1L
  while (memoryValues.forall(_ / memoryDivider >= 100) && memoryDivider < Int.MaxValue) {
    memoryDivider *= 10L
  }
  // scribe.debug(timeMultiplier.toString)

  val memoryMappingModule = SingleProcessSingleMessageMemoryConstraintsModule(
    chocoModel,
    dse.sdfApplications.processSizes.map(_ / memoryDivider).map(_.toInt),
    dse.sdfApplications.sdfMessages.map((src, _, _, mSize, p, c, tok) =>
      ((dse.sdfApplications.sdfRepetitionVectors(
        dse.sdfApplications.actorsSet.indexOf(src)
      ) * p + tok) * mSize / memoryDivider).toInt
    ),
    dse.platform.tiledDigitalHardware.maxMemoryPerTile
      .map(_ / memoryDivider)
      .map(l => if (l > Int.MaxValue) then Int.MaxValue - 1 else l)
      .map(_.toInt)
  )

  val tileAnalysisModule = TileAsyncInterconnectCommsModule(
    chocoModel,
    dse.platform.schedulerSet,
    dse.platform.tiledDigitalHardware.routerSet,
    dse.sdfApplications.sdfMessages.zipWithIndex.map((m, i) => i),
    dse.sdfApplications.sdfMessages.map((_, _, _, mSize, _, _, _) =>
      dse.platform.tiledDigitalHardware.bandWidthPerCEPerVirtualChannel.map(bw =>
        (mSize / bw / timeMultiplier / memoryDivider).ceil.toInt
      )
    ),
    dse.platform.tiledDigitalHardware.commElemsVirtualChannels,
    dse.platform.tiledDigitalHardware.computeRouterPaths
  )

  val sdfAnalysisModule = SDFSchedulingAnalysisModule2(
    chocoModel,
    dse,
    memoryMappingModule,
    tileAnalysisModule,
    timeMultiplier
  )

  // - mixed constraints
  memoryMappingModule.postSingleProcessSingleMessageMemoryConstraints()

  //---------

  //-----------------------------------------------------
  // COMMUNICATION

  // we make sure that the input messages are always mapped with the consumers so that
  // it would be synthetizeable later. Otherwise the model becomes irrealistic
  memoryMappingModule.processesMemoryMapping.zipWithIndex.foreach((aMap, a) => {
    memoryMappingModule.messagesMemoryMapping.zipWithIndex.foreach((cMap, c) => {
      val (s, t, cs, _, _, _, _) = dse.sdfApplications.sdfMessages(c)
      if (a == t) {
        chocoModel.arithm(aMap, "=", cMap).post()
      }
    })
  })
  // build the table that make this constraint
  for (
    (p, sendi)  <- dse.platform.schedulerSet.zipWithIndex;
    (pp, desti) <- dse.platform.schedulerSet.zipWithIndex;
    if sendi != desti
  ) {
    val anyMapped = dse.sdfApplications.sdfMessages
      .map((s, t, cs, _, _, _, _) =>
        chocoModel.and(
          chocoModel.arithm(
            memoryMappingModule
              .processesMemoryMapping(dse.sdfApplications.actorsSet.indexOf(s)),
            "=",
            p
          ),
          chocoModel.arithm(
            memoryMappingModule
              .processesMemoryMapping(dse.sdfApplications.actorsSet.indexOf(t)),
            "=",
            pp
          )
        )
      )
    chocoModel.ifOnlyIf(
      chocoModel.or(anyMapped: _*),
      chocoModel.arithm(tileAnalysisModule.procElemSendsDataToAnother(sendi)(desti), "=", 1)
      // tileAnalysisModule.procElemSendsDataToAnother(sendi)(desti).eq(0).decompose()
    )
  }
  // dse.platform.schedulerSet.zipWithIndex.foreach((_, sendi) => {
  //   dse.platform.schedulerSet.zipWithIndex.foreach((_, desti) => {
  //     if (sendi != desti) {
  //       chocoModel.ifThen(
  //         chocoModel.and(
  //           chocoModel.arithm(aMap, "=", sendi),
  //           chocoModel.arithm(cMap, "=", desti)
  //         ),
  //         chocoModel.arithm(
  //           tileAnalysisModule.procElemSendsDataToAnother(sendi)(desti),
  //           ">",
  //           0
  //         )
  //       )
  //     }
  //   })
  // })

  tileAnalysisModule.postTileAsyncInterconnectComms()
  //---------

  //-----------------------------------------------------
  // SCHEDULING AND TIMING

  sdfAnalysisModule.postSDFTimingAnalysis()
  //---------

  //-----------------------------------------------------
  // Objectives

  // remember that this is tiled based, so a memory being mapped entails the processor
  // is used
  val nUsedPEs = sdfAnalysisModule.numMappedElements
  //   chocoModel.intVar(
  //   "nUsedPEs",
  //   1,
  //   dse.platform.tiledDigitalHardware.processors.length,
  //   true
  // )
  // make sure the variable counts the number of used
  // chocoModel.atMostNValues(memoryMappingModule.processesMemoryMapping, nUsedPEs, true).post()
  // chocoModel.atLeastNValues(memoryMappingModule.processesMemoryMapping, nUsedPEs, true).post()
  // chocoModel.nValues(memoryMappingModule.processesMemoryMapping, nUsedPEs).post()
  // val bufferSum =
  //   chocoModel.intVar(
  //     "bufferSum",
  //     0,
  //     sdfAnalysisModule.maxBufferTokens.zipWithIndex
  //       .map((v, i) => v.getUB() * dse.sdfApplications.sdfMessages(i)._4.toInt)
  //       .sum,
  //     true
  //   )
  // chocoModel
  //   .scalar(
  //     sdfAnalysisModule.maxBufferTokens,
  //     dse.sdfApplications.sdfMessages.map((_, _, _, size, _, _, _) => size.toInt),
  //     "=",
  //     bufferSum
  //   )
  //   .post()
  val maxLatency = chocoModel.max("maxLatency", sdfAnalysisModule.jobTasks.map(_.getEnd()))

  override val modelMinimizationObjectives: Array[IntVar] =
    Array(
      nUsedPEs,
      sdfAnalysisModule.globalInvThroughput
      // bufferSum
      // maxLatency
      // chocoModel.max("maximumBuffer", sdfAnalysisModule.slotRange.map(s => chocoModel.sum(s"tokensAt($s)", sdfAnalysisModule.tokens.map(bc => bc(s)):_*)))
    )
  //---------

  //-----------------------------------------------------
  // BRANCHING AND SEARCH

  // breaking platform symmetries
  val mappedPerProcessingElement = dse.platform.schedulerSet.zipWithIndex.map((p, i) =>
    chocoModel.count(
      s"mappedPerProcessingElement($p)",
      p,
      memoryMappingModule.processesMemoryMapping: _*
    )
  )
  val minusMappedPerProcessingElement =
    mappedPerProcessingElement.map(v => chocoModel.intMinusView(v))
  val indexOfPe = dse.platform.schedulerSet.map(p =>
    chocoModel.intVar(
      s"indexOfPe($p)",
      0,
      Math.max(dse.sdfApplications.actors.size, dse.platform.schedulerSet.size + 1),
      false
    )
  )
  for (
    (p, j) <- dse.platform.schedulerSet.zipWithIndex;
    (a, i) <- dse.sdfApplications.topologicalAndHeavyActorOrdering.zipWithIndex
  ) {
    chocoModel.ifThenElse(
      chocoModel.arithm(memoryMappingModule.processesMemoryMapping(a), "=", p),
      chocoModel.arithm(indexOfPe(j), "<=", i),
      chocoModel.arithm(indexOfPe(j), "!=", i)
    )
  }
  dse.platform.tiledDigitalHardware.symmetricTileGroups
    .maxByOption(_.size)
    .foreach(group => {
      val pSorted = group.toArray.sorted
      // chocoModel
      //   .lexChainLessEq(
      //     pSorted.map(p => Array(minusMappedPerProcessingElement(p), indexOfPe(p))): _*
      //   )
      //   .post()
      // chocoModel
      //   .lexLessEq(pSorted.map(minusMappedPerProcessingElement(_)), pSorted.map(indexOfPe(_)))
      //   .post()
      chocoModel
        .increasing(pSorted.map(idx => indexOfPe(idx)), 1)
        .post()
    })
  // enforcing a certain order whenever possible
  val dataFlows = dse.platform.schedulerSet.map(i =>
    dse.platform.schedulerSet.map(j => chocoModel.boolVar(s"dataFlows($i, $j)"))
  )
  for (
    (p, i)  <- dse.platform.schedulerSet.zipWithIndex;
    (pp, j) <- dse.platform.schedulerSet.zipWithIndex
  ) {
    val possiblePaths = tileAnalysisModule.procElemSendsDataToAnother(i)(
      j
    ) +: dse.platform.schedulerSet.zipWithIndex.map((ppp, k) =>
      tileAnalysisModule.procElemSendsDataToAnother(i)(k).and(dataFlows(k)(j)).boolVar()
    )
    chocoModel.ifOnlyIf(
      chocoModel.arithm(dataFlows(i)(j), "=", 1),
      chocoModel.or(
        possiblePaths: _*
      )
    )
  }
  for ((p, i) <- dse.platform.schedulerSet.zipWithIndex) {
    chocoModel.ifThen(
      chocoModel.arithm(dataFlows(i)(i), "=", 0),
      sdfAnalysisModule.makeCanonicalOrderingAtScheduleConstraint(p)
    )
  }
  // also enforce a waterfall pattern for the mappings
  // for (
  //   (dst, j) <- dse.sdfApplications.topologicalAndHeavyActorOrdering.zipWithIndex.drop(1);
  //   prev = dse.sdfApplications.topologicalAndHeavyActorOrdering(j - 1)
  // ) {
  //   val differentThanAll = dse.sdfApplications.topologicalAndHeavyActorOrdering
  //     .take(j)
  //     .map(src =>
  //       chocoModel.arithm(
  //         memoryMappingModule.processesMemoryMapping(dst),
  //         "!=",
  //         memoryMappingModule.processesMemoryMapping(src)
  //       )
  //     )
  //   // equal to the last one OR different than all previous ones
  //   chocoModel
  //     .or(
  //       chocoModel.arithm(
  //         memoryMappingModule.processesMemoryMapping(dst),
  //         "=",
  //         memoryMappingModule.processesMemoryMapping(prev)
  //       ),
  //       chocoModel.and(
  //         differentThanAll: _*
  //       )
  //     )
  //     .post()
  // }
  // println(dse.sdfApplications.firingsPrecedenceGraph.toSortedString())
  override val strategies: Array[AbstractStrategy[? <: Variable]] = Array(
    CompactingMultiCoreMapping[Int](
      dse.platform.tiledDigitalHardware.minTraversalTimePerBit.map(arr =>
        arr.map(v => (v * timeMultiplier).ceil.toInt)
      ),
      dse.sdfApplications.topologicalAndHeavyActorOrdering.map(a =>
        dse.sdfApplications.sdfDisjointComponents.indexWhere(_.contains(a))
      ),
      dse.sdfApplications.topologicalAndHeavyActorOrdering.map(a =>
        memoryMappingModule.processesMemoryMapping(dse.sdfApplications.actorsSet.indexOf(a))
      ),
      (i: Int) => (j: Int) => dse.sdfApplications.firingsPrecedenceGraph.get(sdfAnalysisModule.jobsAndActors(i)).pathTo(dse.sdfApplications.firingsPrecedenceGraph.get(sdfAnalysisModule.jobsAndActors(j))).isDefined
    ),
    Search.minDomLBSearch(nUsedPEs),
    Search.minDomLBSearch(tileAnalysisModule.numVirtualChannelsForProcElem.flatten: _*),
    Search.inputOrderLBSearch(
      dse.sdfApplications.topologicalAndHeavyJobOrdering.map(j =>
        sdfAnalysisModule.jobOrder(sdfAnalysisModule.jobsAndActors.indexOf(j))
      ): _*
    ),
    // Search.inputOrderLBSearch(
    //   dse.sdfApplications.topologicalAndHeavyJobOrdering.map(j =>
    //     sdfAnalysisModule.jobTasks(sdfAnalysisModule.jobsAndActors.indexOf(j)).getStart()
    //   ): _*
    // ),
    Search.minDomLBSearch(sdfAnalysisModule.invThroughputs: _*)
    // Search.minDomLBSearch(maxLatency)
    // Search.minDomLBSearch(sdfAnalysisModule.maxBufferTokens: _*),
    // Search.minDomLBSearch(indexOfPe: _*),
    // these next two lines makre sure the choice for start times and throughput are made just like the ordering and mapping
    // Search.inputOrderLBSearch(
    //   dse.sdfApplications.topologicalAndHeavyJobOrdering.map(j =>
    //     sdfAnalysisModule.jobStartTime(sdfAnalysisModule.jobsAndActors.indexOf(j))
    //   ): _*
    // ),
    // Search.minDomLBSearch(nUsedPEs),
  )

  def chooseOrderingIfMapped(
      mapping: Array[IntVar],
      ordering: Array[IntVar]
  ) = Search.intVarSearch(
    (x) => {
      var chosen: IntVar = null
      for ((v, i) <- mapping.zipWithIndex) {
        if (chosen == null && v.isInstantiated() && !ordering(i).isInstantiated()) {
          chosen = ordering(i)
          // println(s"found at $i : ${v.getValue()}")
        }
      }
      chosen
    },
    (v) => {
      // println(s"choosing ${v.getLB()}")
      v.getLB()
    },
    (mapping ++ ordering): _*
  )

  def chooseLowestMappedTime(
      mapping: Array[IntVar],
      ordering: Array[IntVar],
      startTime: Array[IntVar]
  ) = Search.intVarSearch(
    (x) => {
      var chosen: IntVar = null
      for ((v, i) <- mapping.zipWithIndex) {
        if (chosen == null && v.isInstantiated() && ordering(i).isInstantiated()) {
          chosen = startTime(i)
          // println(s"found at $i : ${v.getValue()}")
        }
      }
      chosen
    },
    (v) => {
      // println(s"choosing ${v.getLB()}")
      v.getLB()
    },
    (mapping ++ ordering ++ startTime): _*
  )

  //---------

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    scribe.debug(
      s"solution: nUsedPEs = ${output.getIntVal(nUsedPEs)}, globalInvThroughput = ${output
        .getIntVal(sdfAnalysisModule.globalInvThroughput)}, maxLatency = ${output.getIntVal(maxLatency)}"
    )
    val paths = tileAnalysisModule.commElemsPaths
    val channelToRouters = dse.sdfApplications.channelsSet.map(c =>
      val i = dse.sdfApplications.sdfMessages.indexWhere((s, d, cs, l, _, _, _) => cs.contains(c))
      dse.platform.tiledDigitalHardware.routerSet.zipWithIndex.map((s, j) =>
        val p = output.getIntVal(memoryMappingModule.messagesMemoryMapping(i))
        output.getIntVal(tileAnalysisModule.numVirtualChannelsForProcElem(p)(j)) > 0
      )
    )
    // println(dse.platform.tiledDigitalHardware.routerPaths.map(_.map(_.mkString("[", ", ", "]")).mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    // println(channelToRouters.map(_.mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    // println(tileAnalysisModule.numVirtualChannelsForProcElem.map(_.mkString(", ")).mkString("\n"))
    val channelToTiles = dse.sdfApplications.channelsSet.map(c =>
      val i = dse.sdfApplications.sdfMessages.indexWhere((s, d, cs, l, _, _, _) => cs.contains(c))
      dse.platform.tiledDigitalHardware.tileSet.zipWithIndex.map((s, j) =>
        output.getIntVal(memoryMappingModule.messagesMemoryMapping(i)) == j
      )
    )
    val mappings = dse.sdfApplications.channels.zipWithIndex
      .map((c, i) => channelToTiles(i) ++ channelToRouters(i))
    val schedulings =
      memoryMappingModule.processesMemoryMapping.map(vs =>
        dse.platform.tiledDigitalHardware.tileSet.map(j => output.getIntVal(vs) == j)
      )
    dse.addMappingsAndRebuild(
      mappings,
      schedulings,
      dse.platform.schedulerSet.zipWithIndex.map((s, j) => {
        sdfAnalysisModule.jobsAndActors.zipWithIndex
          .filter((job, i) =>
            output.getIntVal(
              memoryMappingModule.processesMemoryMapping(
                dse.sdfApplications.actorsSet.indexOf(job._1)
              )
            ) == s
          )
          .sortBy((job, i) => output.getIntVal(sdfAnalysisModule.jobStartTime(i)))
          .map((job, i) => dse.sdfApplications.actorsSet.indexOf(job._1))
      }),
      // TODO: fix this slot allocaiton strategy for later. It is Okay, but lacks some direct synthetizable details, like which exact VC the channel goes
      dse.sdfApplications.channelsSet.zipWithIndex.map((c, ci) => {
        // we have to look from the source perpective, since the sending processor is the one that allocates
        val (s, _, _, _, _, _, _) =
          dse.sdfApplications.sdfMessages.find((s, d, cs, l, _, _, _) => cs.contains(c)).get
        val p = output.getIntVal(
          memoryMappingModule.processesMemoryMapping(dse.sdfApplications.actorsSet.indexOf(s))
        )
        dse.platform.tiledDigitalHardware.allCommElems.zipWithIndex.map((ce, j) => {
          output.getIntVal(tileAnalysisModule.numVirtualChannelsForProcElem(p)(j))
        })
      }),
      dse.sdfApplications.actors.zipWithIndex.map((a, i) =>
        val th = output.getIntVal(
          sdfAnalysisModule.invThroughputs(i)
        )
        // val th = sdfAnalysisModule.jobsAndActors.zipWithIndex
        //   .filter((job, ji) => job._1 == dse.sdfApplications.actorsSet.indexOf(i))
        //   .map((job, ji) => output.getIntVal(sdfAnalysisModule.jobsInvThroughputs(ji)))
        //   .max
        if (th > 0) {
          Rational(
            timeMultiplier,
            th
          )
        } else {
          Rational(-1)
        }
      )
    )
  }

  def uniqueIdentifier: String = "ChocoSDFToSChedTileHW2"

  def coveredVertexes: Iterable[Vertex] = dse.coveredVertexes

}

object ChocoSDFToSChedTileHW2 {

  def identifyFromAny(
      model: Any,
      identified: scala.collection.Iterable[DecisionModel]
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHW2] =
    ForSyDeIdentificationRule.identifyWrapper(model, identified, identifyFromForSyDe)
  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHW2] = {
    identified
      .find(m => m.isInstanceOf[SDFToSchedTiledHW])
      .map(m => m.asInstanceOf[SDFToSchedTiledHW])
      .map(dse => identFromForSyDeWithDeps(model, dse))
      .getOrElse(IdentificationResult.unfixedEmpty())
  }

  def identFromForSyDeWithDeps(
      model: ForSyDeSystemGraph,
      dse: SDFToSchedTiledHW
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHW2] = {
    IdentificationResult.fixed(ChocoSDFToSChedTileHW2(dse))
  }

}
