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
import idesyde.utils.HasUtils
import org.chocosolver.solver.Model
import idesyde.choco.HasTileAsyncInterconnectCommunicationConstraints
import idesyde.choco.HasSingleProcessSingleMessageMemoryConstraints
import idesyde.choco.HasDiscretizationToIntegers
import scala.collection.mutable.Buffer
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore
import idesyde.identification.choco.models.sdf.StreamingJobsThroughputPropagator

trait HasSDFSchedulingAnalysisAndConstraints
    extends HasUtils
    with HasSingleProcessSingleMessageMemoryConstraints
    with HasDiscretizationToIntegers {

  def postSDFTimingAnalysis(
      m: SDFToTiledMultiCore,
      chocoModel: Model,
      processMappings: Array[IntVar],
      actorDuration: Array[Array[Int]],
      messageTravelDuration: Array[Array[Array[IntVar]]]
  ): (Array[IntVar], Array[IntVar], Array[IntVar], IntVar, IntVar) = {
    val timeValues =
      m.wcets.flatten ++ m.platform.hardware.maxTraversalTimePerBit.flatten
    val memoryValues = m.platform.hardware.tileMemorySizes

    val actors             = m.sdfApplications.actorsIdentifiers
    val jobsAndActors      = m.sdfApplications.jobsAndActors
    def jobMapping(i: Int) = processMappings(actors.indexOf(jobsAndActors(i)._1))

    val schedulers = m.platform.runtimes.schedulers

    val maxRepetitionsPerActors     = m.sdfApplications.sdfRepetitionVectors
    def isSelfConcurrent(a: String) = m.sdfApplications.isSelfConcurrent(a)

    val maximumTokensPerChannel = m.sdfApplications.pessimisticTokensPerChannel

    val maxLength = schedulers.zipWithIndex
      .map((_, p) => {
        actors.zipWithIndex
          .map((a, ai) => actorDuration(ai)(p) * maxRepetitionsPerActors(ai))
          .sum
      })
      .max + messageTravelDuration.flatten.flatten.map(_.getUB()).sum

    val invThroughputs = actors.zipWithIndex
      .map((a, i) =>
        chocoModel.intVar(
          s"invTh($a)",
          actorDuration(i).filter(_ >= 0).minOption.getOrElse(0),
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

    val globalInvThroughput =
      chocoModel.intVar(
        "globalInvThroughput",
        invThroughputs.map(_.getLB()).max,
        maxLength,
        true
      )

    val duration = actors.zipWithIndex
      .map((a, i) =>
        chocoModel.intVar(
          s"dur($a)",
          actorDuration(i).filter(_ > 0).toArray
        )
      )
      .toArray

    val transmissionDelay =
      m.sdfApplications.actorsIdentifiers.zipWithIndex.map((a, i) =>
        m.sdfApplications.actorsIdentifiers.zipWithIndex
          .map((aa, j) => {
            val messageTimesIdx =
              m.sdfApplications.sdfMessages
                .indexWhere((src, dst, _, _, _, _, _) => src == a && dst == aa)
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

    // val maxBufferTokens = m.sdfApplications.sdfMessages.zipWithIndex.map((tuple, i) =>
    //   val (src, dst, _, size, p, c, tok) = tuple
    //   chocoModel.intVar(
    //     s"maxBufferTokens(${tuple._1}, ${tuple._2})",
    //     0,
    //     tok + m.sdfApplications.sdfRepetitionVectors(actors.indexOf(src)) * p,
    //     true
    //   )
    // )

    val mappedJobsPerElement = schedulers.zipWithIndex.map((p, i) =>
      chocoModel.count(
        s"mappedJobsPerElement($p)",
        i,
        jobsAndActors.map((a, _) => processMappings(actors.indexOf(a))): _*
      )
    )

    val numMappedElements = chocoModel.intVar("numMappedElements", 1, schedulers.size, false)

    chocoModel.nValues(processMappings, numMappedElements).post()
    chocoModel.count(0, jobOrder, numMappedElements).post()
    // --- general duration constraints
    for ((a, i) <- actors.zipWithIndex; (p, j) <- schedulers.zipWithIndex) {
      if (actorDuration(i)(j) < 0) {
        chocoModel.arithm(processMappings(i), "!=", j).post()
      } else {
        chocoModel.ifThen(
          chocoModel.arithm(processMappings(i), "=", j),
          chocoModel.arithm(duration(i), "=", actorDuration(i)(j))
        )
        for ((job, k) <- jobsAndActors.zipWithIndex; if job._1 == a) {
          chocoModel.ifThen(
            chocoModel.arithm(processMappings(i), "=", j),
            chocoModel.arithm(jobOrder(k), "<", mappedJobsPerElement(j))
          )
        }
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
      (aj, qj) <- m.sdfApplications.firingsPrecedenceGraph
        .get((ai, qi))
        .outNeighbors
        .map(_.value);
      j   = jobsAndActors.indexOf((aj, qj));
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
      jobsAndActors.size,
      isSuccessor(m)(jobsAndActors),
      hasDataCycle(m)(jobsAndActors),
      jobOrder,
      (0 until jobsAndActors.size).map(jobMapping(_)).toArray,
      jobsAndActors.map((a, _) => duration(actors.indexOf(a))).toArray,
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
      if (
        m.sdfApplications.sdfGraph
          .get(a)
          .isPredecessorOf(m.sdfApplications.sdfGraph.get(aa))
        || m.sdfApplications.sdfGraph
          .get(aa)
          .isPredecessorOf(m.sdfApplications.sdfGraph.get(a))
      ) {
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
    chocoModel.max(globalInvThroughput, invThroughputs).post()
    (jobOrder, mappedJobsPerElement.toArray, invThroughputs, numMappedElements, globalInvThroughput)
  }

  def isSuccessor(m: SDFToTiledMultiCore)(jobsAndActors: Vector[(String, Int)])(i: Int)(j: Int) =
    m.sdfApplications.firingsPrecedenceGraph
      .get(jobsAndActors(i))
      .isDirectPredecessorOf(
        m.sdfApplications.firingsPrecedenceGraph.get(jobsAndActors(j))
      )

  def hasDataCycle(m: SDFToTiledMultiCore)(jobsAndActors: Vector[(String, Int)])(i: Int)(j: Int) =
    m.sdfApplications.firingsPrecedenceGraph
      .get(jobsAndActors(i))
      .isPredecessorOf(
        m.sdfApplications.firingsPrecedenceGraph.get(jobsAndActors(j))
      ) &&
      m.sdfApplications.firingsPrecedenceGraphWithCycles
        .get(jobsAndActors(j))
        .isPredecessorOf(
          m.sdfApplications.firingsPrecedenceGraphWithCycles.get(jobsAndActors(i))
        )

  def makeCanonicalOrderingAtScheduleConstraint(
      m: SDFToTiledMultiCore
  )(
      chocoModel: Model,
      processesMemoryMapping: Array[IntVar],
      jobOrder: Array[IntVar],
      scheduler: Int
  ): Constraint = {
    val jobsAndActors =
      m.sdfApplications.firingsPrecedenceGraph.nodes
        .map(v => v.value)
        .toVector
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
