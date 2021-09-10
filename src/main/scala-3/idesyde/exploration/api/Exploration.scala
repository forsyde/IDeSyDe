package idesyde.exploration.api

import idesyde.exploration.Explorer
import idesyde.identification.DecisionModel
import scala.concurrent.Future

object Exploration {

    def defaultExplorers: Set[Explorer[? <: DecisionModel]] = Set(

    )

    // def exploreDecisionModel(decisionModel: DecisionModel, extraExplorers: Set[Explorer[? <: DecisionModel]] = Set()): Future[DecisionModel] = {
    //     val explorers = defaultExplorers ++ extraExplorers
        
    // }
  
}
