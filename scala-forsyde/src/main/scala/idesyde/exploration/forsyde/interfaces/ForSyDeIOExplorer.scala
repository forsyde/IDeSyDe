package idesyde.exploration.forsyde.interfaces

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.exploration.Explorer
import idesyde.identification.DecisionModel
import scala.concurrent.ExecutionContext
import idesyde.identification.forsyde.ForSyDeDecisionModel

trait ForSyDeIOExplorer extends Explorer {

  def explore[DesignModel](decisionModel: DecisionModel)(using ExecutionContext): LazyList[DesignModel] = 
    decisionModel match {
      case fioDecisionModel: ForSyDeDecisionModel => exploreForSyDe(fioDecisionModel)
            .flatMap(systemGraph => systemGraph match {
              // the fact this is unchecked and correct relies completely on 
              // the DesignModel always being a class that can casted! Otherwise,
              // this might give class casting exception
              case designModel: DesignModel @unchecked => Some(designModel)
              case _ => Option.empty
            })
      case _ => LazyList.empty
    }

  def canExplore(decisionModel: DecisionModel): Boolean =
    decisionModel match {
      case fioDecisionModel: ForSyDeDecisionModel => canExploreForSyDe(fioDecisionModel)
      case _ => false
    }

  def exploreForSyDe(decisionModel: ForSyDeDecisionModel)(using ExecutionContext): LazyList[ForSyDeSystemGraph]

  def canExploreForSyDe(decisionModel: ForSyDeDecisionModel): Boolean

}
