package idesyde.choco

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import scala.collection.mutable.HashMap
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.exception.ContradictionException
import org.chocosolver.solver.constraints.`extension`.Tuples
import org.chocosolver.solver.Model
import idesyde.choco.HasTileAsyncInterconnectCommunicationConstraints
import idesyde.choco.HasSingleProcessSingleMessageMemoryConstraints
import idesyde.choco.HasDiscretizationToIntegers
import scala.collection.mutable.Buffer
import idesyde.common.SDFToTiledMultiCore
import idesyde.identification.choco.models.sdf.StreamingJobsThroughputPropagator
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.traverse.TopologicalOrderIterator
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

trait HasSDFSchedulingAnalysisAndConstraints
    extends HasUtils
    with HasSingleProcessSingleMessageMemoryConstraints
    with HasDiscretizationToIntegers {

  def postTiledOrPartitionedDurations(
      chocoModel: Model,
      processMappings: Array[IntVar],
      actorDuration: Array[Array[Int]]
  ): Array[IntVar] = {
    val actors = 0 until actorDuration.length
    val schedulers =
      processMappings.map(_.getLB().toInt).min until processMappings.map(_.getUB().toInt).max
    val duration = actors.zipWithIndex
      .map((a, i) =>
        chocoModel.intVar(
          s"dur($a)",
          actorDuration(i).filter(_ > 0).toArray
        )
      )
      .toArray
    for ((a, i) <- actors.zipWithIndex; (p, j) <- schedulers.zipWithIndex) {
      if (actorDuration(i)(j) < 0) {
        chocoModel.arithm(processMappings(i), "!=", j).post()
      } else {
        chocoModel.ifThen(
          chocoModel.arithm(processMappings(i), "=", j),
          chocoModel.arithm(duration(i), "=", actorDuration(i)(j))
        )
      }
    }
    duration
  }

  def postSDFTimingAnalysis(
      chocoModel: Model,
      actors: Vector[String],
      actorSubGraphs: Vector[Vector[String]],
      messages: Vector[(String, String, Long)],
      jobsAndActors: Vector[(String, Int)],
      jobGraphWithoutCycles: Graph[(String, Int), DefaultEdge],
      jobGraphWithCycles: Graph[(String, Int), DefaultEdge],
      // partialOrder: Vector[((String, Int), (String, Int))],
      // partialOrderWithCycles: Vector[((String, Int), (String, Int))],
      schedulers: Vector[String],
      maxRepetitionsPerActors: (Int) => Int,
      jobMapping: (Int) => IntVar,
      processMappings: Array[IntVar],
      durations: Array[IntVar],
      messageTravelDuration: Array[Array[Array[IntVar]]]
  ): (Array[IntVar], Array[IntVar], Array[IntVar], IntVar) = {
    val maxLength = schedulers.zipWithIndex
      .map((_, p) => {
        actors.zipWithIndex
          .map((a, ai) => durations(ai).getUB() * maxRepetitionsPerActors(ai))
          .sum
      })
      .max + messageTravelDuration.flatten.flatten.map(_.getUB()).sum

    val invThroughputs = actors.zipWithIndex
      .map((a, i) =>
        chocoModel.intVar(
          s"invThroughput($a)",
          durations(i).getLB(),
          maxLength,
          true
        )
      )
      .toArray

    val jobOrder =
      jobsAndActors
        .map((a, q) =>
          chocoModel.intVar(
            s"jobOrder($a, $q)",
            0,
            jobsAndActors.size - 1,
            false
          )
        )
        .toArray

    // val globalInvThroughput =
    //   chocoModel.intVar(
    //     "globalInvThroughput",
    //     invThroughputs.map(_.getLB()).max,
    //     maxLength,
    //     true
    //   )

    val transmissionDelay =
      actors.zipWithIndex.map((a, i) =>
        actors.zipWithIndex
          .map((aa, j) => {
            val messageTimesIdx =
              messages
                .indexWhere((src, dst, _) => src == a && dst == aa)
            val maxMessageTime =
              if (messageTimesIdx > -1) then
                chocoModel.max(
                  s"maxMessageTime($a, $aa)",
                  messageTravelDuration(messageTimesIdx).flatten
                )
              else chocoModel.intVar(0)
            maxMessageTime
          })
      )

    val mappedJobsPerElement = schedulers.zipWithIndex.map((p, i) =>
      chocoModel.count(
        s"mappedJobsPerElement($p)",
        i,
        jobsAndActors.toArray.map((a, _) => processMappings(actors.indexOf(a))): _*
      )
    )

    val numMappedElements = chocoModel.intVar("nUsedPEs", 1, schedulers.size, false)

    chocoModel.nValues(processMappings, numMappedElements).post()
    chocoModel.count(0, jobOrder, numMappedElements).post()
    // --- general duration constraints
    for ((a, i) <- actors.zipWithIndex; (p, j) <- schedulers.zipWithIndex) {
      for ((job, k) <- jobsAndActors.zipWithIndex; if job._1 == a) {
        chocoModel.ifThen(
          chocoModel.arithm(processMappings(i), "=", j),
          chocoModel.arithm(jobOrder(k), "<", mappedJobsPerElement(j))
        )
      }
    }
    // -------------- next and ordering parts
    for ((p, j) <- schedulers.zipWithIndex) {
      chocoModel
        .allDifferentUnderCondition(
          jobOrder,
          (x) => jobMapping(jobOrder.indexOf(x)).isInstantiatedTo(j),
          false
        )
        .post()
    }
    // make sure that actors in the same element follow the precedences
    for (
      ((ai, qi), i) <- jobsAndActors.zipWithIndex;
      ((aj, qj), j) <- jobsAndActors.zipWithIndex;
      if jobGraphWithoutCycles.containsEdge((ai, qi), (aj, qj));
      aix = actors.indexOf(ai);
      ajx = actors.indexOf(aj)
    ) {
      chocoModel.ifThen(
        chocoModel.arithm(
          processMappings(aix),
          "=",
          processMappings(ajx)
        ),
        chocoModel.arithm(jobOrder(i), "<", jobOrder(j))
      )
    }
    // -----/
    // buffers
    // for (
    //   (tuple, i) <- m.sdfApplications.sdfMessages.zipWithIndex;
    //   (src, dst, _, size, p, c, initTokens) = tuple;
    //   (srcJob, j) <- jobsAndActors.zipWithIndex.filter((job, _) => job._1 == src);
    //   (srcA, srcQ) = srcJob;
    //   (dstJob, k) <- jobsAndActors.zipWithIndex.filter((job, _) => job._1 == dst);
    //   (dstA, dstQ) = dstJob
    // ) {
    //   chocoModel.ifThen(
    //     chocoModel.arithm(
    //       jobTasks(j).getEnd(),
    //       "<=",
    //       jobTasks(k).getStart()
    //     ),
    //     chocoModel.arithm(
    //       maxBufferTokens(i),
    //       ">=",
    //       initTokens + p * srcQ - c * (dstQ - 1)
    //     )
    //   )
    // }
    // -----/
    // throughput
    val thPropagator = StreamingJobsThroughputPropagator(
      jobsAndActors,
      jobGraphWithoutCycles,
      jobGraphWithCycles,
      // isSuccessor(partialOrder)(jobsAndActors),
      // hasDataCycle(partialOrderWithCycles)(jobsAndActors),
      jobOrder,
      (0 until jobsAndActors.size).map(jobMapping(_)).toArray,
      jobsAndActors.map((a, _) => durations(actors.indexOf(a))).toArray,
      jobsAndActors
        .map((src, _) =>
          jobsAndActors
            .map((dst, _) => transmissionDelay(actors.indexOf(src))(actors.indexOf(dst)))
            .toArray
        )
        .toArray,
      jobsAndActors.map((a, _) => invThroughputs(actors.indexOf(a))).toArray
    )
    chocoModel.post(new Constraint("StreamingJobsThroughputPropagator", thPropagator))
    for ((a, i) <- actors.zipWithIndex; (aa, j) <- actors.zipWithIndex; if a != aa) {
      if (actorSubGraphs.indexWhere(_.contains(a)) == actorSubGraphs.indexWhere(_.contains(aa))) {
        chocoModel
          .arithm(
            invThroughputs(i),
            "=",
            invThroughputs(j)
          )
          .post()
      } else {
        chocoModel.ifThen(
          chocoModel.arithm(
            processMappings(i),
            "=",
            processMappings(j)
          ),
          chocoModel.arithm(
            invThroughputs(i),
            "=",
            invThroughputs(j)
          )
        )
      }
    }
    // chocoModel.max(globalInvThroughput, invThroughputs).post()
    (jobOrder, mappedJobsPerElement.toArray, invThroughputs, numMappedElements)
  }

  def postSDFTimingAnalysis(
      m: SDFToTiledMultiCore,
      chocoModel: Model,
      processMappings: Array[IntVar],
      durations: Array[IntVar],
      messageTravelDuration: Array[Array[Array[IntVar]]]
  ): (Array[IntVar], Array[IntVar], Array[IntVar], IntVar) = {
    val actors             = m.sdfApplications.actorsIdentifiers
    val jobsAndActors      = m.sdfApplications.jobsAndActors
    def jobMapping(i: Int) = processMappings(actors.indexOf(jobsAndActors(i)._1))

    val schedulers = m.platform.runtimes.schedulers

    val maxRepetitionsPerActors = m.sdfApplications.sdfRepetitionVectors
    // val wccs = ConnectivityInspector(m.sdfApplications.firingsPrecedenceGraph)

    postSDFTimingAnalysis(
      chocoModel,
      actors,
      m.sdfApplications.sdfDisjointComponents.map(_.toVector).toVector,
      m.sdfApplications.sdfMessages.map((s, t, _, l, p, _, _) => (s, t, l * p)),
      jobsAndActors,
      m.sdfApplications.firingsPrecedenceGraph,
      m.sdfApplications.firingsPrecedenceGraphWithCycles,
      // jobsAndActors.flatMap(s =>
      //   jobsAndActors
      //     .filter(t =>
      //       // inspector.pathExists(s, t)
      //       m.sdfApplications.firingsPrecedenceGraph
      //         .containsEdge(s, t)
      //     // .get(s)
      //     // .isDirectPredecessorOf(m.sdfApplications.firingsPrecedenceGraph.get(t))
      //     )
      //     .map(t => (s, t))
      // ),
      // jobsAndActors.flatMap(s =>
      //   jobsAndActors
      //     .filter(t =>
      //       !m.sdfApplications.firingsPrecedenceGraph
      //         .containsEdge(s, t) &&
      //       m.sdfApplications.firingsPrecedenceGraphWithCycles
      //         .containsEdge(s, t)
      //     // .isDirectPredecessorOf(m.sdfApplications.firingsPrecedenceGraphWithCycles.get(t))
      //     )
      //     .map(t => (s, t))
      // ),
      schedulers,
      maxRepetitionsPerActors,
      jobMapping,
      processMappings,
      durations,
      messageTravelDuration
    )
  }

  def isSuccessor(partialOrder: Vector[((String, Int), (String, Int))])(
      jobsAndActors: Vector[(String, Int)]
  )(i: Int)(j: Int) =
    partialOrder.contains((jobsAndActors(i), jobsAndActors(j)))

  def isSuccessor(m: SDFToTiledMultiCore)(jobsAndActors: Vector[(String, Int)])(i: Int)(j: Int) =
    m.sdfApplications.firingsPrecedenceGraph
      .containsEdge(jobsAndActors(i), jobsAndActors(j))
  // .get(jobsAndActors(i))

  // .isDirectPredecessorOf(
  //   m.sdfApplications.firingsPrecedenceGraph.get(jobsAndActors(j))
  // )

  def hasDataCycle(
      partialOrderWithCycles: Vector[((String, Int), (String, Int))]
  )(jobsAndActors: Vector[(String, Int)])(i: Int)(j: Int) =
    partialOrderWithCycles.contains((jobsAndActors(i), jobsAndActors(j))) && partialOrderWithCycles
      .contains((jobsAndActors(j), jobsAndActors(i)))

  def hasDataCycle(m: SDFToTiledMultiCore)(jobsAndActors: Vector[(String, Int)])(i: Int)(j: Int) = {
    // val inspector = ConnectivityInspector(m.sdfApplications.firingsPrecedenceGraph)
    val inspectorWithCyles = ConnectivityInspector(
      m.sdfApplications.firingsPrecedenceGraphWithCycles
    )
    // inspector.pathExists(jobsAndActors(i), jobsAndActors(j)) &&
    inspectorWithCyles.pathExists(jobsAndActors(j), jobsAndActors(i))
    // m.sdfApplications.firingsPrecedenceGraph
    //   .containsEdge(jobsAndActors(i), jobsAndActors(j))
    // .isPredecessorOf(
    //   m.sdfApplications.firingsPrecedenceGraph.get(jobsAndActors(j))
    // )
    // m.sdfApplications.firingsPrecedenceGraphWithCycles
    //   .containsEdge(jobsAndActors(j), jobsAndActors(i))
  }
  // .get(jobsAndActors(j))
  // .isPredecessorOf(
  //   m.sdfApplications.firingsPrecedenceGraphWithCycles.get(jobsAndActors(i))
  // )

  def makeCanonicalOrderingAtScheduleConstraint(
      m: SDFToTiledMultiCore
  )(
      chocoModel: Model,
      processesMemoryMapping: Array[IntVar],
      jobOrder: Array[IntVar],
      scheduler: Int
  ): Constraint = {
    val jobsAndActors =
      m.sdfApplications.jobsAndActors
    val constraints = for (
      (v, i)  <- jobsAndActors.zipWithIndex;
      (vv, j) <- jobsAndActors.zipWithIndex;
      if i != j;
      vIdx  = m.sdfApplications.topologicalAndHeavyJobOrdering.indexOf(v);
      vvIdx = m.sdfApplications.topologicalAndHeavyJobOrdering.indexOf(vv);
      if vIdx < vvIdx;
      (a, q)   = v;
      (aa, qq) = vv;
      aIdx     = m.sdfApplications.actorsIdentifiers.indexOf(a);
      aaIdx    = m.sdfApplications.actorsIdentifiers.indexOf(aa)
    )
      yield chocoModel.or(
        chocoModel.arithm(processesMemoryMapping(aIdx), "!=", scheduler),
        chocoModel.arithm(processesMemoryMapping(aaIdx), "!=", scheduler),
        chocoModel.arithm(jobOrder(i), "<", jobOrder(j))
      )
    chocoModel.and(constraints: _*)
  }

}
