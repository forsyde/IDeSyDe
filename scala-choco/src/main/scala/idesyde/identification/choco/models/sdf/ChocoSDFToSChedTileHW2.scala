package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.ChocoDecisionModel
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
import forsyde.io.java.core.EdgeInfo
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.identification.common.StandardDecisionModel

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
    val dse: SDFToTiledMultiCore
)(using Fractional[Rational])
    extends StandardDecisionModel
    with ChocoDecisionModel(shouldLearnSignedClauses = false) {

  val chocoModel: Model = Model()

  // chocoModel.getSolver().plugMonitor(ConMonitorObj2(this))

  // section for time multiplier calculation
  val timeValues =
    (dse.wcets.flatten ++ dse.platform.hardware.maxTraversalTimePerBit.flatten)
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
  val memoryValues = dse.platform.hardware.tileMemorySizes ++
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
        dse.sdfApplications.actorsIdentifiers.indexOf(src)
      ) * p + tok) * mSize / memoryDivider).toInt
    ),
    dse.platform.hardware.tileMemorySizes
      .map(_ / memoryDivider)
      .map(l => if (l > Int.MaxValue) then Int.MaxValue - 1 else l)
      .map(_.toInt)
  )

  val tileAnalysisModule = TileAsyncInterconnectCommsModule(
    chocoModel,
    dse.platform.hardware.processors,
    dse.platform.hardware.communicationElems,
    dse.sdfApplications.sdfMessages.zipWithIndex.map((m, i) => i),
    dse.sdfApplications.sdfMessages.map((_, _, _, mSize, _, _, _) =>
      dse.platform.hardware.communicationElementsBitPerSecPerChannel.map(bw =>
        (mSize / bw / timeMultiplier / memoryDivider).ceil.toInt
      )
    ),
    dse.platform.hardware.communicationElementsMaxChannels,
    (src: String) =>
      (dst: String) =>
        dse.platform.hardware
          .computedPaths(dse.platform.hardware.platformElements.indexOf(src))(
            dse.platform.hardware.platformElements.indexOf(dst)
          )
          .toArray
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
      if (dse.sdfApplications.actorsIdentifiers(a) == t) {
        chocoModel.arithm(aMap, "=", cMap).post()
      }
    })
  })
  // build the table that make this constraint
  for (
    (_, p)  <- dse.platform.runtimes.schedulers.zipWithIndex;
    (_, pp) <- dse.platform.runtimes.schedulers.zipWithIndex;
    if p != pp
  ) {
    val anyMapped = dse.sdfApplications.sdfMessages
      .map((s, t, cs, _, _, _, _) =>
        chocoModel.and(
          chocoModel.arithm(
            memoryMappingModule
              .processesMemoryMapping(dse.sdfApplications.actorsIdentifiers.indexOf(s)),
            "=",
            p
          ),
          chocoModel.arithm(
            memoryMappingModule
              .processesMemoryMapping(dse.sdfApplications.actorsIdentifiers.indexOf(t)),
            "=",
            pp
          )
        )
      )
    chocoModel.ifOnlyIf(
      chocoModel.or(anyMapped: _*),
      chocoModel.arithm(tileAnalysisModule.procElemSendsDataToAnother(p)(pp), "=", 1)
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
  //   dse.platform.hardware.processors.length,
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
  val mappedPerProcessingElement = dse.platform.runtimes.schedulers.zipWithIndex.map((p, i) =>
    chocoModel.count(
      s"mappedPerProcessingElement($p)",
      i,
      memoryMappingModule.processesMemoryMapping: _*
    )
  )
  val minusMappedPerProcessingElement =
    mappedPerProcessingElement.map(v => chocoModel.intMinusView(v))
  val indexOfPe = dse.platform.runtimes.schedulers.map(p =>
    chocoModel.intVar(
      s"indexOfPe($p)",
      0,
      Math
        .max(dse.sdfApplications.actorsIdentifiers.size, dse.platform.runtimes.schedulers.size + 1),
      false
    )
  )
  for (
    (p, j) <- dse.platform.runtimes.schedulers.zipWithIndex;
    (a, i) <- dse.sdfApplications.topologicalAndHeavyActorOrdering.zipWithIndex
  ) {
    chocoModel.ifThenElse(
      chocoModel.arithm(
        memoryMappingModule.processesMemoryMapping(
          dse.sdfApplications.actorsIdentifiers.indexOf(a)
        ),
        "=",
        j
      ),
      chocoModel.arithm(indexOfPe(j), "<=", i),
      chocoModel.arithm(indexOfPe(j), "!=", i)
    )
  }
  dse.platform.hardware.symmetricTileGroups
    .maxByOption(_.size)
    .foreach(group => {
      val pSorted = group.toArray.sorted.map(dse.platform.hardware.platformElements.indexOf(_))
      chocoModel
        .lexChainLessEq(
          pSorted.map(p => Array(minusMappedPerProcessingElement(p), indexOfPe(p))): _*
        )
        .post()
      // chocoModel
      //   .lexLessEq(pSorted.map(minusMappedPerProcessingElement(_)), pSorted.map(indexOfPe(_)))
      //   .post()
      // chocoModel
      //   .increasing(pSorted.map(idx => indexOfPe(idx)), 1)
      //   .post()
    })
  // enforcing a certain order whenever possible
  val dataFlows = dse.platform.runtimes.schedulers.map(i =>
    dse.platform.runtimes.schedulers.map(j => chocoModel.boolVar(s"dataFlows($i, $j)"))
  )
  for (
    (p, i)  <- dse.platform.runtimes.schedulers.zipWithIndex;
    (pp, j) <- dse.platform.runtimes.schedulers.zipWithIndex
  ) {
    val possiblePaths = tileAnalysisModule.procElemSendsDataToAnother(i)(
      j
    ) +: dse.platform.runtimes.schedulers.zipWithIndex.map((ppp, k) =>
      tileAnalysisModule.procElemSendsDataToAnother(i)(k).and(dataFlows(k)(j)).boolVar()
    )
    chocoModel.ifOnlyIf(
      chocoModel.arithm(dataFlows(i)(j), "=", 1),
      chocoModel.or(
        possiblePaths: _*
      )
    )
  }
  for ((p, i) <- dse.platform.runtimes.schedulers.zipWithIndex) {
    chocoModel.ifThen(
      chocoModel.arithm(dataFlows(i)(i), "=", 0),
      sdfAnalysisModule.makeCanonicalOrderingAtScheduleConstraint(i)
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
      dse.platform.hardware.minTraversalTimePerBit.map(arr =>
        arr.map(v => (v * timeMultiplier).ceil.toInt)
      ),
      dse.sdfApplications.topologicalAndHeavyActorOrdering.map(a =>
        dse.sdfApplications.sdfDisjointComponents
          .indexWhere(_.exists(_ == a))
      ),
      dse.sdfApplications.topologicalAndHeavyActorOrdering.map(a =>
        memoryMappingModule.processesMemoryMapping(dse.sdfApplications.actorsIdentifiers.indexOf(a))
      )
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

  def rebuildFromChocoOutput(output: Solution): DecisionModel = {
    scribe.debug(
      s"solution: nUsedPEs = ${output.getIntVal(nUsedPEs)}, globalInvThroughput = ${output
        .getIntVal(sdfAnalysisModule.globalInvThroughput)}, maxLatency = ${output.getIntVal(maxLatency)}"
    )
    dse.copy(
      processMappings = dse.sdfApplications.actorsIdentifiers.zipWithIndex.map((a, i) =>
        dse.platform.hardware
          .memories(output.getIntVal(memoryMappingModule.processesMemoryMapping(i)))
      ),
      messageMappings = dse.sdfApplications.channelsIdentifiers.zipWithIndex.map((c, i) => {
        val messageIdx =
          dse.sdfApplications.sdfMessages.indexWhere((_, _, ms, _, _, _, _) => ms.contains(c))
        dse.platform.hardware
          .memories(output.getIntVal(memoryMappingModule.messagesMemoryMapping(messageIdx)))
      }),
      schedulerSchedules = dse.platform.runtimes.zipWithIndex.map((s, si) => {
        // TODO: make here the lists
        Array.empty
      }),
      messageSlotAllocations = dse.sdfApplications.channelsIdentifiers.zipWithIndex.map((c, ci) => {
        // we have to look from the source perpective, since the sending processor is the one that allocates
        val (s, _, _, _, _, _, _) =
          dse.sdfApplications.sdfMessages.find((s, d, cs, l, _, _, _) => cs.contains(c)).get
        val p = output.getIntVal(
          memoryMappingModule.processesMemoryMapping(
            dse.sdfApplications.actorsIdentifiers.indexOf(s)
          )
        )
        // TODO: this must be fixed later, it might clash correct slots
        val iter =
          for (
            (ce, j) <- dse.platform.hardware.communicationElems.zipWithIndex;
            if output.getIntVal(tileAnalysisModule.numVirtualChannelsForProcElem(p)(j)) > 0
          )
            yield ce -> (0 until dse.platform.hardware.communicationElementsMaxChannels(j))
              .map(slot =>
                (slot + j % dse.platform.hardware.communicationElementsMaxChannels(j)) < output
                  .getIntVal(tileAnalysisModule.numVirtualChannelsForProcElem(p)(j))
              )
              .toArray
        iter.toMap
      })
    )
  }

  def uniqueIdentifier: String = "ChocoSDFToSChedTileHW2"

  val coveredElements = dse.coveredElements

  val coveredElementRelations = dse.coveredElementRelations

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
      .find(m => m.isInstanceOf[SDFToTiledMultiCore])
      .map(m => m.asInstanceOf[SDFToTiledMultiCore])
      .map(dse => identFromForSyDeWithDeps(model, dse))
      .getOrElse(IdentificationResult.unfixedEmpty())
  }

  def identFromForSyDeWithDeps(
      model: ForSyDeSystemGraph,
      dse: SDFToTiledMultiCore
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHW2] = {
    IdentificationResult.fixed(ChocoSDFToSChedTileHW2(dse))
  }

}
