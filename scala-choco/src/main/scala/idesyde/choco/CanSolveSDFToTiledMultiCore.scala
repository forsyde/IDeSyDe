package idesyde.choco

import idesyde.identification.choco.ChocoDecisionModel
import org.chocosolver.solver.Model
import forsyde.io.java.core.Vertex
import org.chocosolver.solver.Solution
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.core.DecisionModel
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import idesyde.choco.HasTileAsyncInterconnectCommunicationConstraints
import spire.math.Rational
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.variables.Largest
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMedian
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMax
import org.chocosolver.solver.search.strategy.strategy.FindAndProve
import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import org.chocosolver.solver.exception.ContradictionException
import scala.collection.mutable.Buffer
import forsyde.io.java.core.EdgeInfo
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.identification.common.StandardDecisionModel
import org.chocosolver.solver.objective.OptimizationPolicy
import idesyde.utils.Logger
import idesyde.identification.choco.models.sdf.CompactingMultiCoreMapping
import scalax.collection.GraphEdge.DiEdge
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.headers.LabelledArcWithPorts
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.cycle.JohnsonSimpleCycles
import org.jgrapht.graph.DefaultDirectedGraph
import scala.collection.mutable.Stack
import idesyde.utils.HasUtils
import idesyde.identification.choco.models.HasSingleProcessSingleMessageMemoryConstraints
import idesyde.choco.HasDiscretizationToIntegers

trait CanSolveSDFToTiledMultiCore(using logger: Logger)
    extends HasUtils
    with HasSingleProcessSingleMessageMemoryConstraints
    with HasDiscretizationToIntegers
    with CanSolveMultiObjective
    with HasTileAsyncInterconnectCommunicationConstraints
    with HasSDFSchedulingAnalysisAndConstraints {

  def buildSDFToTiledMultiCore(m: SDFToTiledMultiCore): Model = {
    val chocoModel   = Model()
    val execMax      = m.wcets.flatten.max
    val commMax      = m.platform.hardware.maxTraversalTimePerBit.flatten.max
    val timeValues   = m.wcets.flatten ++ m.platform.hardware.maxTraversalTimePerBit.flatten
    val memoryValues = m.platform.hardware.tileMemorySizes
    val (timeMultiplier, memoryDivider) =
      computeTimeMultiplierAndMemoryDivider[Double, Long](timeValues, memoryValues)
    val (processMappings, messageMappings, _) = postSingleProcessSingleMessageMemoryConstraints(
      chocoModel,
      m.sdfApplications.processSizes.map(_ / memoryDivider).map(_.toInt).toArray,
      m.sdfApplications.sdfMessages
        .map((src, _, _, mSize, p, c, tok) =>
          ((m.sdfApplications.sdfRepetitionVectors(
            m.sdfApplications.actorsIdentifiers.indexOf(src)
          ) * p + tok) * mSize / memoryDivider).toInt
        )
        .toArray,
      m.platform.hardware.tileMemorySizes
        .map(_ / memoryDivider)
        .map(l => if (l > Int.MaxValue) then Int.MaxValue - 1 else l)
        .map(_.toInt)
        .toArray
    )
    val (numVirtualChannelsForProcElem, procElemSendsDataToAnother, messageTravelDuration) =
      postTileAsyncInterconnectComms(
        chocoModel,
        m.platform.hardware.processors.toArray,
        m.platform.hardware.communicationElems.toArray,
        m.sdfApplications.sdfMessages.zipWithIndex.map((m, i) => i).toArray,
        m.sdfApplications.sdfMessages
          .map((_, _, _, mSize, _, _, _) =>
            m.platform.hardware.communicationElementsBitPerSecPerChannel
              .map(bw => (mSize / bw / timeMultiplier / memoryDivider).ceil.toInt)
              .toArray
          )
          .toArray,
        m.platform.hardware.communicationElementsMaxChannels.toArray,
        (src: String) =>
          (dst: String) =>
            m.platform.hardware
              .computedPaths(m.platform.hardware.platformElements.indexOf(src))(
                m.platform.hardware.platformElements.indexOf(dst)
              )
              .toArray
      )

    val (
      jobOrder,
      mappedJobsPerElement,
      invThroughputs,
      numMappedElements,
      globalInvThroughput
    ) = postSDFTimingAnalysis(m, chocoModel, processMappings, messageTravelDuration)

    postMapChannelsWithConsumers(
      m,
      chocoModel,
      processMappings,
      messageMappings,
      procElemSendsDataToAnother
    )
    createAndApplyPropagator(chocoModel, Array(numMappedElements, globalInvThroughput))
    chocoModel
  }

  def postMapChannelsWithConsumers(
      m: SDFToTiledMultiCore,
      chocoModel: Model,
      processesMemoryMapping: Array[IntVar],
      messagesMemoryMapping: Array[IntVar],
      procElemSendsDataToAnother: Array[Array[BoolVar]]
  ): Array[Array[BoolVar]] = {
    // we make sure that the input messages are always mapped with the consumers so that
    // it would be synthetizeable later. Otherwise the model becomes irrealistic
    processesMemoryMapping.zipWithIndex.foreach((aMap, a) => {
      messagesMemoryMapping.zipWithIndex.foreach((cMap, c) => {
        val (s, t, cs, _, _, _, _) = m.sdfApplications.sdfMessages(c)
        if (m.sdfApplications.actorsIdentifiers(a) == t) {
          chocoModel.arithm(aMap, "=", cMap).post()
        }
      })
    })
    // val procElemSendsDataToAnother =
    //   m.platform.hardware.processors.zipWithIndex.map((src, i) => {
    //     m.platform.hardware.processors.zipWithIndex.map((dst, j) => {
    //       if (!m.platform.hardware.computedPaths(i)(j).isEmpty)
    //         chocoModel
    //           .getVars()
    //           .find(_.getName() == s"sendsData(${i},${j})")
    //           .getOrElse(
    //             chocoModel.boolVar(s"sendsData(${i},${j})")
    //           )
    //           .asBoolVar()
    //       else
    //         chocoModel
    //           .getVars()
    //           .find(_.getName() == s"sendsData(${i},${j})")
    //           .getOrElse(
    //             chocoModel.boolVar(s"sendsData(${i},${j})", false)
    //           )
    //           .asBoolVar()
    //     })
    //   })
    // build the table that make this constraint
    for (
      (_, p)  <- m.platform.runtimes.schedulers.zipWithIndex;
      (_, pp) <- m.platform.runtimes.schedulers.zipWithIndex;
      if p != pp
    ) {
      val anyMapped = m.sdfApplications.sdfMessages
        .map((s, t, cs, _, _, _, _) =>
          chocoModel.and(
            chocoModel.arithm(
              processesMemoryMapping(m.sdfApplications.actorsIdentifiers.indexOf(s)),
              "=",
              p
            ),
            chocoModel.arithm(
              processesMemoryMapping(m.sdfApplications.actorsIdentifiers.indexOf(t)),
              "=",
              pp
            )
          )
        )
      chocoModel.ifOnlyIf(
        chocoModel.or(anyMapped: _*),
        chocoModel.arithm(procElemSendsDataToAnother(p)(pp), "=", 1)
        // tileAnalysisModule.procElemSendsDataToAnother(sendi)(desti).eq(0).decompose()
      )
    }
    procElemSendsDataToAnother
  }

  // breaking platform symmetries
  def postSymmetryBreakingConstraints(
      m: SDFToTiledMultiCore,
      chocoModel: Model,
      processesMemoryMapping: Array[IntVar],
      procElemSendsDataToAnother: Array[Array[BoolVar]],
      jobOrder: Array[IntVar]
  ): (Vector[IntVar], Vector[Vector[BoolVar]]) = {
    val computedOnlineIndexOfPe = m.platform.runtimes.schedulers.map(p =>
      chocoModel.intVar(
        s"indexOfPe($p)",
        0,
        m.sdfApplications.actorsIdentifiers.size + m.platform.runtimes.schedulers.size - 1,
        false
      )
    )
    for (
      (p, j) <- m.platform.runtimes.schedulers.zipWithIndex;
      (a, i) <- m.sdfApplications.topologicalAndHeavyActorOrdering.zipWithIndex
    ) {
      chocoModel.ifThenElse(
        chocoModel.arithm(
          processesMemoryMapping(
            m.sdfApplications.actorsIdentifiers.indexOf(a)
          ),
          "=",
          j
        ),
        chocoModel.arithm(computedOnlineIndexOfPe(j), "<=", i),
        chocoModel.arithm(computedOnlineIndexOfPe(j), "!=", i)
      )
    }
    m.platform.hardware.symmetricTileGroups
      .filter(_.size > 1)
      .maxByOption(_.size)
      .foreach(group => {
        val pSorted = group.map(id => m.platform.hardware.processors.indexOf(id)).toArray.sorted
        chocoModel
          .increasing(pSorted.map(idx => computedOnlineIndexOfPe(idx)), 1)
          .post()
      })
    val dataFlows = m.platform.runtimes.schedulers.map(i =>
      m.platform.runtimes.schedulers.map(j => chocoModel.boolVar(s"dataFlows($i, $j)"))
    )
    for (
      (p, i)  <- m.platform.runtimes.schedulers.zipWithIndex;
      (pp, j) <- m.platform.runtimes.schedulers.zipWithIndex
    ) {
      val possiblePaths = procElemSendsDataToAnother(i)(
        j
      ) +: m.platform.runtimes.schedulers.zipWithIndex.map((ppp, k) =>
        procElemSendsDataToAnother(i)(k).and(dataFlows(k)(j)).boolVar()
      )
      chocoModel.ifOnlyIf(
        chocoModel.arithm(dataFlows(i)(j), "=", 1),
        chocoModel.or(
          possiblePaths: _*
        )
      )
    }
    for ((p, i) <- m.platform.runtimes.schedulers.zipWithIndex) {
      chocoModel.ifThen(
        chocoModel.arithm(dataFlows(i)(i), "=", 0),
        makeCanonicalOrderingAtScheduleConstraint(m)(
          chocoModel,
          processesMemoryMapping,
          jobOrder,
          i
        )
      )
    }
    (computedOnlineIndexOfPe, dataFlows)
  }

  def createAndApplySearchStrategies(
      m: SDFToTiledMultiCore,
      chocoModel: Model,
      numVirtualChannelsForProcElem: Array[Array[IntVar]],
      processesMemoryMapping: Array[IntVar],
      jobOrder: Array[IntVar],
      invThroughputs: Array[IntVar],
      nUsedPEs: IntVar
  ): Array[AbstractStrategy[? <: Variable]] = {
    val (timeMultiplier, _) = computeTimeMultiplierAndMemoryDivider(
      m.platform.hardware.minTraversalTimePerBit.flatten ++ m.wcets.flatten,
      m.platform.hardware.tileMemorySizes
    )
    val jobsAndActors =
      m.sdfApplications.firingsPrecedenceGraph.nodes
        .map(v => v.value)
        .toVector
    val compactStrategy = CompactingMultiCoreMapping[Int](
      m.platform.hardware.minTraversalTimePerBit
        .map(arr => arr.map(v => (v * timeMultiplier).ceil.toInt).toArray)
        .toArray,
      m.wcets.map(_.map(v => (v * timeMultiplier).ceil.toInt).toArray).toArray,
      m.sdfApplications.topologicalAndHeavyActorOrdering
        .map(a =>
          m.sdfApplications.sdfDisjointComponents
            .indexWhere(_.exists(_ == a))
        )
        .toArray,
      m.sdfApplications.topologicalAndHeavyActorOrdering
        .map(a =>
          processesMemoryMapping(
            m.sdfApplications.actorsIdentifiers.indexOf(a)
          )
        )
        .toArray,
      (i: Int) =>
        (j: Int) =>
          m.sdfApplications.sdfGraph
            .get(m.sdfApplications.topologicalAndHeavyActorOrdering(i))
            .pathTo(
              m.sdfApplications.sdfGraph
                .get(m.sdfApplications.topologicalAndHeavyActorOrdering(j))
            )
            .isDefined
    )
    val strategies: Array[AbstractStrategy[? <: Variable]] = Array(
      FindAndProve(
        nUsedPEs +: processesMemoryMapping,
        compactStrategy,
        Search
          .sequencer(Search.minDomLBSearch(nUsedPEs), compactStrategy)
          .asInstanceOf[AbstractStrategy[IntVar]]
      ),
      Search.minDomLBSearch(numVirtualChannelsForProcElem.flatten: _*),
      Search.inputOrderLBSearch(
        m.sdfApplications.topologicalAndHeavyJobOrdering
          .map(jobsAndActors.indexOf)
          .map(jobOrder(_)): _*
      ),
      Search.minDomLBSearch(invThroughputs: _*)
    )
    chocoModel.getSolver().setSearch(strategies: _*)
    strategies
  }

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

  //---------

  def rebuildFromChocoOutput(
      m: SDFToTiledMultiCore,
      processesMemoryMapping: Array[IntVar],
      messagesMemoryMapping: Array[IntVar],
      numVirtualChannelsForProcElem: Array[Array[IntVar]],
      jobOrder: Array[IntVar],
      invThroughputs: Array[IntVar],
      output: Solution
  ): Set[DecisionModel] = {
    // logger.debug(
    //   s"solution: nUsedPEs = ${output.getIntVal(nUsedPEs)}, globalInvThroughput = ${output
    //     .getIntVal(sdfAnalysisModule.globalInvThroughput)} / $timeMultiplier"
    // )
    // logger.debug(sdfAnalysisModule.duration.mkString(", "))
    // logger.debug(memoryMappingModule.processesMemoryMapping.mkString(", "))
    // logger.debug(sdfAnalysisModule.jobOrder.mkString(", "))
    // logger.debug(sdfAnalysisModule.invThroughputs.mkString(", "))
    val (timeMultiplier, _) = computeTimeMultiplierAndMemoryDivider(
      m.platform.hardware.minTraversalTimePerBit.flatten ++ m.wcets.flatten,
      m.platform.hardware.tileMemorySizes
    )
    val jobsAndActors =
      m.sdfApplications.firingsPrecedenceGraph.nodes
        .map(v => v.value)
        .toVector
    val full = m.copy(
      sdfApplications = m.sdfApplications.copy(minimumActorThrouhgputs =
        invThroughputs.zipWithIndex
          .map((th, i) =>
            timeMultiplier.toDouble / (m.sdfApplications
              .sdfRepetitionVectors(i) * th.getValue().toDouble)
          )
          .toVector
      ),
      processMappings = m.sdfApplications.actorsIdentifiers.zipWithIndex.map((a, i) =>
        m.platform.hardware
          .memories(output.getIntVal(processesMemoryMapping(i)))
      ),
      messageMappings = m.sdfApplications.channelsIdentifiers.zipWithIndex.map((c, i) => {
        val messageIdx =
          m.sdfApplications.sdfMessages.indexWhere((_, _, ms, _, _, _, _) => ms.contains(c))
        m.platform.hardware
          .memories(output.getIntVal(messagesMemoryMapping(messageIdx)))
      }),
      schedulerSchedules = m.platform.runtimes.schedulers.zipWithIndex.map((s, si) => {
        val unordered = for (
          ((aId, q), i) <- jobsAndActors.zipWithIndex;
          a = m.sdfApplications.actorsIdentifiers.indexOf(aId);
          if processesMemoryMapping(a).isInstantiatedTo(si)
        ) yield (aId, jobOrder(i).getValue())
        unordered.sortBy((a, o) => o).map((a, _) => a)
      }),
      messageSlotAllocations = m.sdfApplications.channelsIdentifiers.zipWithIndex.map((c, ci) => {
        // we have to look from the source perpective, since the sending processor is the one that allocates
        val (s, _, _, _, _, _, _) =
          m.sdfApplications.sdfMessages.find((s, d, cs, l, _, _, _) => cs.contains(c)).get
        val p = output.getIntVal(
          processesMemoryMapping(
            m.sdfApplications.actorsIdentifiers.indexOf(s)
          )
        )
        // TODO: this must be fixed later, it might clash correct slots
        val iter =
          for (
            (ce, j) <- m.platform.hardware.communicationElems.zipWithIndex;
            if output.getIntVal(numVirtualChannelsForProcElem(p)(j)) > 0
          )
            yield ce -> (0 until m.platform.hardware.communicationElementsMaxChannels(j))
              .map(slot =>
                (slot + j % m.platform.hardware.communicationElementsMaxChannels(j)) < output
                  .getIntVal(numVirtualChannelsForProcElem(p)(j))
              )
              .toVector
        iter.toMap
      }),
      actorThroughputs = recomputeTh(
        m,
        jobsAndActors.zipWithIndex
          .map((j, x) => (m.sdfApplications.actorsIdentifiers.indexOf(j._1), x))
          .map((ax, i) => m.wcets(ax)(processesMemoryMapping(ax).getValue())),
        jobsAndActors
          .map((srca, srcq) =>
            jobsAndActors
              .map((dsta, dstq) => {
                val srcax = m.sdfApplications.actorsIdentifiers.indexOf(srca)
                val dstax = m.sdfApplications.actorsIdentifiers.indexOf(dsta)
                val mSize = m.sdfApplications.sdfMessages
                  .find((s, t, _, _, _, _, _) => s == srca && t == dsta)
                  .map((_, _, _, m, _, _, _) => m)
                  .getOrElse(0L)
                val srcM = processesMemoryMapping(srcax).getValue()
                val dstM = processesMemoryMapping(dstax).getValue()
                if (srcM != dstM) {
                  mSize * m.platform.hardware
                    .minTraversalTimePerBit(srcM)(dstM) * m.platform.hardware
                    .computedPaths(srcM)(dstM)
                    .map(ce => m.platform.hardware.communicationElems.indexOf(ce))
                    .map(cex => numVirtualChannelsForProcElem(srcM)(cex).getValue())
                    .min
                } else 0.0
              })
          ),
        jobsAndActors
          .map((a, _) => processesMemoryMapping(m.sdfApplications.actorsIdentifiers.indexOf(a)))
          .toArray,
        jobOrder
      )
    )
    // return both
    Set(full)
  }

  private def recomputeTh(
      m: SDFToTiledMultiCore,
      jobWeights: Vector[Double],
      edgeWeigths: Vector[Vector[Double]],
      jobMapping: Array[IntVar],
      jobOrder: Array[IntVar]
  ): Vector[Double] = {
    val jobsAndActors =
      m.sdfApplications.firingsPrecedenceGraph.nodes
        .map(v => v.value)
        .toVector
    def mustSuceed(i: Int)(j: Int): Boolean = if (
      jobMapping(i).isInstantiated() && jobMapping(j)
        .isInstantiated() && jobMapping(i)
        .getValue() == jobMapping(j).getValue()
    ) {
      jobOrder(i).stream().anyMatch(oi => jobOrder(j).contains(oi + 1))
    } else {
      isSuccessor(m)(jobsAndActors)(i)(j)
    }
    def mustCycle(i: Int)(j: Int): Boolean =
      hasDataCycle(m)(jobsAndActors)(i)(j) ||
        (jobMapping(i).isInstantiated() && jobMapping(j).isInstantiated() && jobMapping(i)
          .getValue() == jobMapping(j).getValue() && jobOrder(j)
          .getUB() == 0 && jobOrder(i).getLB() > 0)
    var ths   = Buffer.fill(m.sdfApplications.actorsIdentifiers.size)(Double.PositiveInfinity)
    val nJobs = jobsAndActors.size
    val minimumDistanceMatrix = Buffer.fill(nJobs)(0.0)
    var dfsStack              = new Stack[Int](initialSize = nJobs)
    val visited               = Buffer.fill(nJobs)(false)
    val previous              = Buffer.fill(nJobs)(-1)
    wfor(0, _ < nJobs, _ + 1) { src =>
      // this is used instead of popAll in the hopes that no list is allocated
      while (!dfsStack.isEmpty) dfsStack.pop()
      wfor(0, _ < nJobs, _ + 1) { j =>
        visited(j) = false
        previous(j) = -1
        minimumDistanceMatrix(j) = Double.NegativeInfinity
      }
      dfsStack.push(src)
      while (!dfsStack.isEmpty) {
        val i = dfsStack.pop()
        if (!visited(i)) {
          visited(i) = true
          wfor(0, _ < nJobs, _ + 1) { j =>
            if (mustSuceed(i)(j) || mustCycle(i)(j)) { // adjacents
              if (j == src) {                          // found a cycle
                minimumDistanceMatrix(i) = jobWeights(i) + edgeWeigths(i)(j)
                var k = i
                // go backwards until the src
                while (k != src) {
                  val kprev = previous(k)
                  minimumDistanceMatrix(kprev) = Math.max(
                    minimumDistanceMatrix(kprev),
                    jobWeights(kprev) + edgeWeigths(kprev)(k) + minimumDistanceMatrix(k)
                  )
                  k = kprev
                }
              } else if (visited(j) && minimumDistanceMatrix(j) > Int.MinValue) { // found a previous cycle
                var k = j
                // go backwards until the src
                while (k != src) {
                  val kprev = previous(k)
                  minimumDistanceMatrix(kprev) = Math.max(
                    minimumDistanceMatrix(kprev),
                    jobWeights(kprev) + edgeWeigths(kprev)(k) + minimumDistanceMatrix(k)
                  )
                  k = kprev
                }
              } else if (!visited(j)) {
                dfsStack.push(j)
                previous(j) = i
              }
            }
          }
        }
      }
      val (a, _) = jobsAndActors(src)
      val adx    = m.sdfApplications.actorsIdentifiers.indexOf(a)
      val th     = 1.0 / (m.sdfApplications.sdfRepetitionVectors(adx) * minimumDistanceMatrix(src))
      if (ths(adx) > th) ths(adx) = th
    }
    ths.toVector
  }

}
