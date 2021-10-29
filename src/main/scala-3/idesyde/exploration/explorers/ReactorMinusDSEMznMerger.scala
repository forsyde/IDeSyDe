package idesyde.exploration.explorers

import idesyde.identification.models.reactor.ReactorMinusAppMapAndSchedMzn
import idesyde.identification.interfaces.MiniZincData
import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.core.EdgeTrait
import forsyde.io.java.typed.viewers.GenericProcessingModule

trait ReactorMinusDSEMznMerger:
  
  def mergeResults(decisionModel: ReactorMinusAppMapAndSchedMzn, results: Map[String, MiniZincData]): ForSyDeModel =
    val outModel = ForSyDeModel()
    results("reactorMapping") match
      case MiniZincData.MznArray(values) =>
        val valuesConverted = values.map(i => 
          i match 
            case MiniZincData.MznLiteral(j) => j.asInstanceOf[Int]
            case _ => 0
        )
        for (i <- 0 until values.length)
          val reactor = decisionModel.reactorsOrdered(i)
          val mem = decisionModel.platformOrdered(valuesConverted(i) - 1) // -1 due to minizinc starting from 1
          if (!outModel.containsVertex(reactor.getViewedVertex)) outModel.addVertex(reactor.getViewedVertex)
          if (!outModel.containsVertex(mem.getViewedVertex)) outModel.addVertex(mem.getViewedVertex)
          outModel.connect(reactor, mem, EdgeTrait.AbstractMapping)
      case _ => 
    results("channelMapping") match
      case MiniZincData.MznArray(values) =>
        val valuesConverted = values.map(i => 
          i match 
            case MiniZincData.MznLiteral(j) => j.asInstanceOf[Int]
            case _ => 0
        )
        for (i <- 0 until values.length)
          val (_, channel) = decisionModel.channelsOrdered(i)
          val mem = decisionModel.platformOrdered(valuesConverted(i) - 1) // -1 due to minizinc starting from 1
          if (!outModel.containsVertex(channel.getViewedVertex)) outModel.addVertex(channel.getViewedVertex)
          if (!outModel.containsVertex(mem.getViewedVertex)) outModel.addVertex(mem.getViewedVertex)
          outModel.connect(channel, mem, EdgeTrait.AbstractMapping)
      case _ =>
    results("reactionExecution") match
      case MiniZincData.MznArray(values) =>
        val valuesConverted = values.map(i => 
          i match 
            case MiniZincData.MznLiteral(j) => j.asInstanceOf[Int]
            case _ => 0
        )
        for (i <- 0 until values.length)
          val reaction = decisionModel.reactionsOrdered(i)
          val pe = decisionModel.platformOrdered(valuesConverted(i) - 1) // -1 due to minizinc starting from 1
          pe match 
            case p: GenericProcessingModule => 
              val sched = decisionModel.sourceModel.platform.schedulersFromPEs(p)
              if (!outModel.containsVertex(reaction.getViewedVertex)) outModel.addVertex(reaction.getViewedVertex)
              if (!outModel.containsVertex(sched.getViewedVertex)) outModel.addVertex(sched.getViewedVertex)
              outModel.connect(reaction, sched, EdgeTrait.AbstractScheduling)
            case _ =>
      case _ => 
    outModel

end ReactorMinusDSEMznMerger
