package idesyde.exploration.explorers

import idesyde.identification.interfaces.MiniZincDecisionModel

import scala.sys.process._
import idesyde.identification.models.reactor.ReactorMinusJobsMapAndSchedMzn
import java.time.Duration
import idesyde.exploration.interfaces.SimpleMiniZincCPExplorer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeModel
import java.nio.file.Files
import idesyde.identification.DecisionModel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

final case class ReactorMinusGecodeMiniZincExplorer() extends SimpleMiniZincCPExplorer[ReactorMinusJobsMapAndSchedMzn]:

  override def canExplore(decisionModel: DecisionModel): Boolean =
    super.canExplore(decisionModel) &&
    "minizinc --solvers".!!.contains("org.gecode.gecode") &&
    decisionModel.isInstanceOf[ReactorMinusJobsMapAndSchedMzn]

  def estimateTimeUntilFeasibility(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusJobsMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        Duration.ofSeconds(nonMznDecisionModel.reactorMinusJobs.jobs.size * nonMznDecisionModel.reactorMinusJobs.channels.size * 3)
      case _ => Duration.ZERO

  def estimateTimeUntilOptimality(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusJobsMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        Duration.ofMinutes(nonMznDecisionModel.reactorMinusJobs.jobs.size * nonMznDecisionModel.reactorMinusJobs.channels.size * nonMznDecisionModel.platform.coveredVertexes.size * 3)
      case _ => Duration.ZERO

  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel): Long = 
    decisionModel match
      case m: ReactorMinusJobsMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        128 * nonMznDecisionModel.reactorMinusJobs.jobs.size * nonMznDecisionModel.reactorMinusJobs.channels.size
      case _ => 0

  def estimateMemoryUntilOptimality(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusJobsMapAndSchedMzn =>
        val nonMznDecisionModel = m
        50 * estimateMemoryUntilFeasibility(decisionModel)
      case _ => 0

  def explore(decisionModel: DecisionModel)(using ExecutionContext) =
    decisionModel match
      case m: ReactorMinusJobsMapAndSchedMzn =>
        // val modelFile = Files.createTempFile("idesyde-minizinc-model", ".mzn")
        // val dataFile = Files.createTempFile("idesyde-minizinc-data", ".json")
        val modelPath = Paths.get("idesyde-minizinc-model.mzn")
        val dataPath = Paths.get("idesyde-minizinc-data.json")
        val dataJson = ujson.Obj.from(m.mznInputs.map((k, v) => k -> v.toJson(true)))
        val dataOutStream = Files.newOutputStream(dataPath, StandardOpenOption.CREATE,StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        Files.write(modelPath, m.mznModel.getBytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        dataJson.writeBytesTo(dataOutStream, 2, false)
        dataOutStream.close
        Future(Option(ForSyDeModel()))
      case _ => Future(Option.empty)