package idesyde.choco

import scala.jdk.CollectionConverters._

import idesyde.identification.choco.ChocoDecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
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
import idesyde.common.legacy.SDFToTiledMultiCore
import org.chocosolver.solver.objective.OptimizationPolicy
import idesyde.identification.choco.models.sdf.CompactingMultiCoreMapping
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.cycle.JohnsonSimpleCycles
import org.jgrapht.graph.DefaultDirectedGraph
import scala.collection.mutable.Stack
import idesyde.choco.HasDiscretizationToIntegers
import org.chocosolver.solver.objective.ParetoMaximizer
import org.chocosolver.solver.constraints.Constraint
import idesyde.core.Explorer
import idesyde.core.ExplorationSolution
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.graph.AsGraphUnion
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector

final class CanSolveSDFToTiledMultiCore
    extends ChocoExplorable[SDFToTiledMultiCore]
    with HasUtils
    with HasSingleProcessSingleMessageMemoryConstraints
    with HasDiscretizationToIntegers
    with CanSolveMultiObjective
    with HasTileAsyncInterconnectCommunicationConstraints
    with HasSDFSchedulingAnalysisAndConstraints {

  def buildChocoModel(
      m: SDFToTiledMultiCore,
      previousSolutions: Set[ExplorationSolution],
      configuration: Explorer.Configuration
  ): (Model, Map[String, IntVar]) = {
    val chocoModel = Model()
    val execMax    = m.wcets.flatten.max
    val commMax    = m.platform.hardware.maxTraversalTimePerBit.flatten.max
    val timeValues = m.wcets.flatten ++ m.platform.hardware.maxTraversalTimePerBit.flatten
    val memoryValues = m.platform.hardware.tileMemorySizes ++ m.sdfApplications.sdfMessages
      .map((src, _, _, mSize, p, c, tok) => mSize)
    // val (discreteTimeValues, discreteMemoryValues) =
    //   computeTimeMultiplierAndMemoryDividerWithResolution(
    //     timeValues,
    //     memoryValues,
    //     if (configuration.timeDiscretizationFactor > Int.MaxValue) Int.MaxValue else configuration.timeDiscretizationFactor.toInt,
    //     if (memoryResolution > Int.MaxValue) Int.MaxValue else memoryResolution.toInt
    //   )
    def log2(x: Double) = scala.math.log10(x) / scala.math.log10(2)
    def double2int(s: Double) = discretized(
      if (configuration.timeDiscretizationFactor > Int.MaxValue) Int.MaxValue
      else if (configuration.timeDiscretizationFactor <= 0L)
        scala.math.ceil(log2(m.platform.runtimes.schedulers.length) + 5 * log2(10) - 1.0).toInt
      else configuration.timeDiscretizationFactor.toInt,
      timeValues.sum
    )(s)
    given Fractional[Long] = HasDiscretizationToIntegers.ceilingLongFractional
    def long2int(l: Long) = discretized(
      if (configuration.memoryDiscretizationFactor > Int.MaxValue) Int.MaxValue
      else if (configuration.memoryDiscretizationFactor <= 0L) memoryValues.size * 100
      else configuration.memoryDiscretizationFactor.toInt,
      memoryValues.max
    )(l)
    val messagesSizes = m.sdfApplications.sdfMessages
      .map((src, _, _, mSize, p, c, tok) =>
        val s = (m.sdfApplications.sdfRepetitionVectors(
          m.sdfApplications.actorsIdentifiers.indexOf(src)
        ) * p + tok) * mSize
        long2int(s)
      )
      .toArray
    val execTimes = m.wcets
      .map(ws => ws.map(double2int))
      .map(_.toArray)
      .toArray
    val (processMappings, messageMappings, _) = postSingleProcessSingleMessageMemoryConstraints(
      chocoModel,
      m.sdfApplications.processSizes.map(long2int).toArray,
      messagesSizes,
      m.platform.hardware.tileMemorySizes
        .map(long2int)
        .toArray
    )
    val (numVirtualChannelsForProcElem, procElemSendsDataToAnother, messageTravelDuration) =
      postTileAsyncInterconnectComms(
        chocoModel,
        m.platform.hardware.processors.toArray,
        m.platform.hardware.communicationElems.toArray,
        messagesSizes
          .map(mSize =>
            m.platform.hardware.communicationElementsBitPerSecPerChannel
              .map(bw => mSize / bw)
              .map(double2int)
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

    // disable mappings to places that cannnot run stuff
    for ((pm, a) <- processMappings.zipWithIndex; (w, pe) <- m.wcets(a).zipWithIndex; if w <= 0.0) {
      chocoModel.arithm(pm, "!=", pe).post()
    }

    val durations = postTiledOrPartitionedDurations(
      chocoModel,
      processMappings,
      execTimes
    )

    val (
      jobOrder,
      mappedJobsPerElement,
      invThroughputs,
      numMappedElements
    ) = postSDFTimingAnalysis(
      m,
      chocoModel,
      processMappings,
      durations,
      messageTravelDuration
    )

    postMapChannelsWithConsumers(
      m,
      chocoModel,
      processMappings,
      messageMappings,
      procElemSendsDataToAnother
    )
    // createAndApplyMOOPropagator(chocoModel, Array(numMappedElements, globalInvThroughput))
    // val (computedOnlineIndexOfPe, _) = postSymmetryBreakingConstraints(
    //   m,
    //   chocoModel,
    //   processMappings,
    //   procElemSendsDataToAnother,
    //   jobOrder
    // )
    createAndApplySearchStrategies(
      m,
      chocoModel,
      execTimes,
      m.platform.hardware.maxTraversalTimePerBit.map(_.map(double2int).toArray).toArray,
      numVirtualChannelsForProcElem,
      processMappings,
      jobOrder,
      invThroughputs,
      numMappedElements
    )
    // val commSlotsMax = chocoModel.max("commSlotsMax", numVirtualChannelsForProcElem.flatten)
    // val paretoPropagator = ParetoMaximizer(
    //   Array(
    //     chocoModel.intMinusView(numMappedElements),
    //     chocoModel.intMinusView(globalInvThroughput)
    //     // chocoModel.intMinusView(commSlotsSum)
    //   )
    // )
    // chocoModel.post(Constraint("paretoOptimality", paretoPropagator))
    // chocoModel.getSolver().plugMonitor(paretoPropagator)
    // val globalInvTh = chocoModel.max("globalInvTh", invThroughputs)
    val goalInvThs = invThroughputs.zipWithIndex.filter((v, i) => {
      m.sdfApplications.minimumActorThroughputs(i) <= 0.0
    })
    for ((v, i) <- invThroughputs.zipWithIndex; if !goalInvThs.contains((v, i))) {
      chocoModel
        .arithm(
          v,
          "<=",
          double2int(
            m.sdfApplications.sdfRepetitionVectors(i).toDouble / m.sdfApplications
              .minimumActorThroughputs(i)
          )
        )
        .post()
    }
    val uniqueGoalPerSubGraphInvThs = goalInvThs
      .groupBy((v, i) =>
        m.sdfApplications.sdfDisjointComponents
          .map(_.toVector)
          .indexWhere(component => component.contains(m.sdfApplications.actorsIdentifiers(i)))
      )
      .map((k, v) => v.head._1)
    val objs = Array(
      numMappedElements
    ) ++ uniqueGoalPerSubGraphInvThs
    val desiredGoals =
      if (!configuration.targetObjectives.isEmpty) then
        objs.filter(v => configuration.targetObjectives.contains(v.getName()))
      else objs
    createAndApplyMOOPropagator(
      chocoModel,
      desiredGoals,
      previousSolutions
        .map(sol => (sol.solved(), sol.objectives().asScala))
        .map((_, o) =>
          o.map((k, v) =>
            if (uniqueGoalPerSubGraphInvThs.exists(_.getName().equals(k))) k -> double2int(v)
            else k                                                           -> v.toInt
          ).toMap
        )
    )
    // chocoModel.getSolver().setLearningSignedClauses()
    chocoModel.getSolver().setRestartOnSolutions()
    chocoModel.getSolver().setNoGoodRecordingFromRestarts()
    // chocoModel
    //   .getSolver()
    //   .plugMonitor(new IMonitorContradiction {
    //     def onContradiction(cex: ContradictionException): Unit = {
    //       println(cex.toString())
    //       println(chocoModel.getSolver().getDecisionPath().toString())
    //     }
    //   })
    (chocoModel, desiredGoals.map(o => o.getName() -> o).toMap)
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
      if (procElemSendsDataToAnother(p)(pp).getUB() == 1) {
        chocoModel.ifOnlyIf(
          chocoModel.or(anyMapped: _*),
          chocoModel.arithm(procElemSendsDataToAnother(p)(pp), "=", 1)
          // tileAnalysisModule.procElemSendsDataToAnother(sendi)(desti).eq(0).decompose()
        )
      } else {
        chocoModel.and(anyMapped.map(_.getOpposite()): _*).post()
      }
      // if (procElemSendsDataToAnother(p)(pp).getUB() == 1) {
      // } else {
      //   chocoModel.and(anyMapped.map(c => c.getOpposite()): _*)
      // }
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
    val dataFlows = m.platform.runtimes.schedulers.zipWithIndex.map((src, i) =>
      m.platform.runtimes.schedulers.zipWithIndex.map((dst, j) =>
        chocoModel.boolVar(s"dataFlows($i, $j)")
      )
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
      // chocoModel.arithm(dataFlows(i)(i), "=", 0).post()
    }
    (computedOnlineIndexOfPe, dataFlows)
  }

  def createAndApplySearchStrategies(
      m: SDFToTiledMultiCore,
      chocoModel: Model,
      execTimes: Array[Array[Int]],
      tarversalTimesPerBit: Array[Array[Int]],
      numVirtualChannelsForProcElem: Array[Array[IntVar]],
      processesMemoryMapping: Array[IntVar],
      jobOrder: Array[IntVar],
      invThroughputs: Array[IntVar],
      nUsedPEs: IntVar
  ): Array[AbstractStrategy[? <: Variable]] = {
    val jobsAndActors =
      m.sdfApplications.jobsAndActors
    val sdfGraphInspector = ConnectivityInspector(m.sdfApplications.sdfGraph)
    val compactStrategy = CompactingMultiCoreMapping[Int](
      tarversalTimesPerBit,
      execTimes,
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
          sdfGraphInspector.pathExists(
            m.sdfApplications.topologicalAndHeavyActorOrdering(i),
            m.sdfApplications.topologicalAndHeavyActorOrdering(j)
          )
      // m.sdfApplications.sdfGraph
      //   .get(m.sdfApplications.topologicalAndHeavyActorOrdering(i))
      //   .pathTo(
      //     m.sdfApplications.sdfGraph
      //       .get(m.sdfApplications.topologicalAndHeavyActorOrdering(j))
      //   )
      //   .isDefined
    )
    val strategies: Array[AbstractStrategy[? <: Variable]] = Array(
      // compactStrategy,
      Search.activityBasedSearch(processesMemoryMapping: _*),
      Search.inputOrderLBSearch(
        m.sdfApplications.topologicalAndHeavyJobOrdering
          .map(jobsAndActors.indexOf)
          .map(jobOrder(_)): _*
      ),
      Search.activityBasedSearch(
        numVirtualChannelsForProcElem.flatten: _*
      ),
      Search.activityBasedSearch(nUsedPEs),
      Search.minDomLBSearch(invThroughputs: _*)
      // Search.activityBasedSearch(numVirtualChannelsForProcElem.flatten: _*)
      // Search.minDomLBSearch(indexOfPes: _*)
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

  def rebuildDecisionModel(
      m: SDFToTiledMultiCore,
      solution: Solution,
      configuration: Explorer.Configuration
  ): ExplorationSolution = {
    val timeValues = m.wcets.flatten ++ m.platform.hardware.maxTraversalTimePerBit.flatten
    val memoryValues = m.platform.hardware.tileMemorySizes ++ m.sdfApplications.sdfMessages
      .map((src, _, _, mSize, p, c, tok) => mSize)
    def log2(x: Double) = scala.math.log10(x) / scala.math.log10(2)
    def int2double(d: Int) = undiscretized(
      if (configuration.timeDiscretizationFactor > Int.MaxValue) Int.MaxValue
      else if (configuration.timeDiscretizationFactor <= 0L)
        scala.math.ceil(log2(m.platform.runtimes.schedulers.length) + 5 * log2(10) - 1.0).toInt
      else configuration.timeDiscretizationFactor.toInt,
      timeValues.sum
    )(d)
    // val (discreteTimeValues, discreteMemoryValues) =
    //   computeTimeMultiplierAndMemoryDividerWithResolution(
    //     timeValues,
    //     memoryValues,
    //     if (configuration.timeDiscretizationFactor > Int.MaxValue) Int.MaxValue else configuration.timeDiscretizationFactor.toInt,
    //     if (memoryResolution > Int.MaxValue) Int.MaxValue else memoryResolution.toInt
    //   )
    val intVars = solution.retrieveIntVars(true).asScala
    // the mappings default to zero because choco Molde might not store the mapping variables
    // in the solution where there is only one possible mapping, meaning that the chosen mapping
    // was 0, by cosntruction.
    val processesMemoryMapping: Vector[Int] =
      m.sdfApplications.actorsIdentifiers.zipWithIndex.map((_, a) =>
        intVars
          .find(_.getName() == s"mapProcess($a)")
          .map(solution.getIntVal(_))
          .get
      )
    val messagesMemoryMapping: Vector[Int] =
      m.sdfApplications.sdfMessages.zipWithIndex.map((messsage, c) =>
        intVars
          .find(_.getName() == s"mapMessage($c)")
          .map(solution.getIntVal(_))
          .get
      )
    val numVirtualChannelsForProcElem: Vector[Vector[IntVar]] =
      m.platform.hardware.processors.map(src =>
        m.platform.hardware.communicationElems.map(ce =>
          intVars.find(_.getName() == s"vc($src,$ce)").get
        )
      )
    val jobOrder: Vector[IntVar] = m.sdfApplications.jobsAndActors
      .map((a, q) => intVars.find(_.getName() == s"jobOrder($a, $q)").get)
      .toVector
    val invThroughputs: Vector[IntVar] =
      m.sdfApplications.actorsIdentifiers.map(a =>
        intVars.find(_.getName() == s"invThroughput($a)").get
      )
    val numMappedElements = intVars.find(_.getName() == "nUsedPEs").get
    val jobsAndActors =
      m.sdfApplications.jobsAndActors
    val throughputs = recomputeTh(
      m,
      jobsAndActors.zipWithIndex
        .map((j, x) => (m.sdfApplications.actorsIdentifiers.indexOf(j._1), x))
        .map((ax, i) => m.wcets(ax)(processesMemoryMapping(ax))),
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
              val srcM = processesMemoryMapping(srcax)
              val dstM = processesMemoryMapping(dstax)
              if (srcM != dstM) {
                mSize * m.platform.hardware
                  .minTraversalTimePerBit(srcM)(dstM) * m.platform.hardware
                  .computedPaths(srcM)(dstM)
                  .map(ce => m.platform.hardware.communicationElems.indexOf(ce))
                  .map(cex => numVirtualChannelsForProcElem(srcM)(cex).getLB())
                  .minOption
                  .getOrElse(0)
              } else 0.0
            })
        ),
      jobsAndActors
        .map((a, _) => processesMemoryMapping(m.sdfApplications.actorsIdentifiers.indexOf(a)))
        .toArray,
      jobOrder.map(v => v.getValue()).toArray
    )
    val full = m.copy(
      sdfApplications = m.sdfApplications.copy(minimumActorThroughputs =
        invThroughputs.zipWithIndex
          .map((invTh, i) =>
            m.sdfApplications
              .sdfRepetitionVectors(i)
              .toDouble / int2double(invTh.getValue())
          )
          .toVector
      ),
      processMappings = m.sdfApplications.actorsIdentifiers.zipWithIndex.map((a, i) =>
        m.platform.hardware
          .memories(processesMemoryMapping(i))
      ),
      messageMappings = m.sdfApplications.channelsIdentifiers.zipWithIndex.map((c, i) => {
        val messageIdx =
          m.sdfApplications.sdfMessages.indexWhere((_, _, ms, _, _, _, _) => ms.contains(c))
        m.platform.hardware
          .memories(messagesMemoryMapping(messageIdx))
      }),
      schedulerSchedules = m.platform.runtimes.schedulers.zipWithIndex.map((s, si) => {
        val unordered = for (
          ((aId, q), i) <- jobsAndActors.zipWithIndex;
          a = m.sdfApplications.actorsIdentifiers.indexOf(aId);
          if processesMemoryMapping(a) == si
        ) yield (aId, jobOrder(i).getValue())
        unordered.sortBy((a, o) => o).map((a, _) => a)
      }),
      messageSlotAllocations = m.sdfApplications.channelsIdentifiers.zipWithIndex.map((c, ci) => {
        // we have to look from the source perpective, since the sending processor is the one that allocates
        val (s, _, _, _, _, _, _) =
          m.sdfApplications.sdfMessages.find((s, d, cs, l, _, _, _) => cs.contains(c)).get
        val p = processesMemoryMapping(
          m.sdfApplications.actorsIdentifiers.indexOf(s)
        )
        // TODO: this must be fixed later, it might clash correct slots
        val iter =
          for (
            (ce, j) <- m.platform.hardware.communicationElems.zipWithIndex;
            // if solution.getIntVal(numVirtualChannelsForProcElem(p)(j)) > 0
            if numVirtualChannelsForProcElem(p)(j).getLB() > 0
          )
            yield ce -> (0 until m.platform.hardware.communicationElementsMaxChannels(j))
              .map(slot =>
                // (slot + j % m.platform.hardware.communicationElementsMaxChannels(j)) < solution
                //   .getIntVal(numVirtualChannelsForProcElem(p)(j))
                (slot + j % m.platform.hardware.communicationElementsMaxChannels(
                  j
                )) < numVirtualChannelsForProcElem(p)(j).getLB()
              )
              .toVector
        iter.toMap
      }),
      actorThroughputs = throughputs
    )
    // return both
    val objs = Map(
      "nUsedPEs" -> numMappedElements.getValue().toDouble
    ) ++ m.sdfApplications.actorsIdentifiers.zipWithIndex
      .map((a, i) => s"invThroughput($a)" -> 1.0 / throughputs(i))
      .toMap
    val filteredObjs =
      if (configuration.targetObjectives.isEmpty) then objs
      else objs.filter((k, v) => configuration.targetObjectives.contains(k))
    ExplorationSolution(
      filteredObjs.map((k, v) => k -> v.asInstanceOf[java.lang.Double]).asJava,
      full
    )
  }

  private def recomputeTh(
      m: SDFToTiledMultiCore,
      jobWeight: Vector[Double],
      edgeWeight: Vector[Vector[Double]],
      jobMapping: Array[Int],
      jobOrder: Array[Int]
  ): Vector[Double] = {

    val maxCycles = Buffer.fill(jobWeight.size)(0.0)

    val jobs =
      m.sdfApplications.jobsAndActors
    val nJobs = jobs.size

    val mappingGraph = DefaultDirectedGraph[(String, Int), DefaultEdge](classOf[DefaultEdge])
    jobs.foreach(job => mappingGraph.addVertex(job))

    def mustSuceed(i: Int)(j: Int): Boolean = if (
    jobMapping(i) == jobMapping(j)
    ) {
      jobOrder(i) + 1 == jobOrder(j)
    } else {
      m.sdfApplications.firingsPrecedenceGraph.containsEdge(jobs(i), jobs(j))
    }

    def mustCycle(i: Int)(j: Int): Boolean =
      (!m.sdfApplications.firingsPrecedenceGraph.containsEdge(jobs(i), jobs(j)) && m.sdfApplications.firingsPrecedenceGraphWithCycles.containsEdge(jobs(i), jobs(j))) ||
        (jobMapping(i) == jobMapping(j) && jobOrder(j) == 0 && jobOrder(i) > 0)

    wfor(0, _ < nJobs, _ + 1) { i =>
      wfor(0, _ < nJobs, _ + 1) { j =>
        if (mustSuceed(i)(j) || mustCycle(i)(j)) {
          mappingGraph.addEdge(jobs(i), jobs(j))
        } else if (mappingGraph.containsEdge(jobs(i), jobs(j))) {
          mappingGraph.removeEdge(jobs(i), jobs(j))
        }
      }
    }

    // merge the mapping graph with the original graph
    val mergedGraph = AsGraphUnion(
      m.sdfApplications.firingsPrecedenceGraphWithCycles,
      mappingGraph
    )

    // find all strongly connected components
    var sccAlgorithm = KosarajuStrongConnectivityInspector(mergedGraph);
    sccAlgorithm.stronglyConnectedSets().forEach(sccJava => {
        var cycleValue = 0.0
        val scc = sccJava.asScala
        // println(scc.mkString(", "))
        // add the value in the cycle
        for (jobI <- scc) {
          val i = jobs.indexOf(jobI)
            cycleValue = cycleValue + jobWeight(i)
            for (jobJ <- scc; if m.sdfApplications.firingsPrecedenceGraphWithCycles.containsEdge(jobI, jobJ)) {
              val j = jobs.indexOf(jobJ)
              cycleValue = cycleValue + edgeWeight(i)(j)
            }
          maxCycles(i) = Math.max(maxCycles(i), cycleValue);
        }
    });
    
    
    var mappedInspector = ConnectivityInspector(mergedGraph);
    mappedInspector.connectedSets().forEach(wccJava => {
      val wcc = wccJava.asScala
      val maxCycleValue = wcc.map(jobI => maxCycles(jobs.indexOf(jobI))).max
      wcc.map(jobs.indexOf).foreach(i => maxCycles(i) = Math.max(maxCycles(i), maxCycleValue))
    })

    // println(maxCycles.mkString(", "))
    m.sdfApplications.actorsIdentifiers
      .map(a => maxCycles.zipWithIndex.filter((l, i) => jobs(i)._1 == a).map((l, i) => jobs(i)._2.toDouble / l).max)
      .toVector

  //   var ths = m.wcets.zipWithIndex
  //     .map((w, ai) => m.sdfApplications.sdfRepetitionVectors(ai).toDouble / w.filter(_ > 0.0).min)
  //     .toBuffer
  //   val nJobs                 = jobsAndActors.size
  //   val minimumDistanceMatrix = jobWeights.toBuffer
  //   var dfsStack              = new Stack[Int](initialSize = nJobs)
  //   val visited               = Buffer.fill(nJobs)(false)
  //   val previous              = Buffer.fill(nJobs)(-1)
  //   wfor(0, _ < nJobs, _ + 1) { src =>
  //     // this is used instead of popAll in the hopes that no list is allocated
  //     while (!dfsStack.isEmpty) dfsStack.pop()
  //     wfor(0, _ < nJobs, _ + 1) { j =>
  //       visited(j) = false
  //       previous(j) = -1
  //       minimumDistanceMatrix(j) = Double.NegativeInfinity
  //     }
  //     dfsStack.push(src)
  //     while (!dfsStack.isEmpty) {
  //       val i = dfsStack.pop()
  //       if (!visited(i)) {
  //         visited(i) = true
  //         wfor(0, _ < nJobs, _ + 1) { j =>
  //           if (mustSuceed(i)(j) || mustCycle(i)(j)) { // adjacents
  //             if (j == src) {                          // found a cycle
  //               minimumDistanceMatrix(i) = jobWeights(i) + edgeWeigths(i)(j)
  //               var k = i
  //               // go backwards until the src
  //               while (k != src) {
  //                 val kprev = previous(k)
  //                 minimumDistanceMatrix(kprev) = Math.max(
  //                   minimumDistanceMatrix(kprev),
  //                   jobWeights(kprev) + edgeWeigths(kprev)(k) + minimumDistanceMatrix(k)
  //                 )
  //                 k = kprev
  //               }
  //             } else if (visited(j) && minimumDistanceMatrix(j) > Int.MinValue) { // found a previous cycle
  //               var k = j
  //               // go backwards until the src
  //               while (k != src) {
  //                 val kprev = previous(k)
  //                 minimumDistanceMatrix(kprev) = Math.max(
  //                   minimumDistanceMatrix(kprev),
  //                   jobWeights(kprev) + edgeWeigths(kprev)(k) + minimumDistanceMatrix(k)
  //                 )
  //                 k = kprev
  //               }
  //             } else if (!visited(j)) {
  //               dfsStack.push(j)
  //               previous(j) = i
  //             }
  //           }
  //         }
  //       }
  //     }
  //     val (a, _) = jobsAndActors(src)
  //     val adx    = m.sdfApplications.actorsIdentifiers.indexOf(a)
  //     val th =
  //       m.sdfApplications.sdfRepetitionVectors(adx).toDouble / minimumDistanceMatrix(src)
  //     if (minimumDistanceMatrix(src) > Double.NegativeInfinity && ths(adx) > th) ths(adx) = th
  //   }
  //   for (
  //     group <- m.sdfApplications.sdfDisjointComponents; a1 <- group; a2 <- group; if a1 != a2;
  //     a1i = m.sdfApplications.actorsIdentifiers.indexOf(a1);
  //     a2i = m.sdfApplications.actorsIdentifiers.indexOf(a2);
  //     qa1 = m.sdfApplications.sdfRepetitionVectors(a1i);
  //     qa2 = m.sdfApplications.sdfRepetitionVectors(a2i)
  //   ) {
  //     ths(a1i) = Math.min(ths(a1i), ths(a2i) * qa1 / qa2)
  //     ths(a2i) = Math.min(ths(a1i) * qa2 / qa1, ths(a2i))
  //   }
  //   ths.toVector
  }

}
