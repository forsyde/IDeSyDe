package idesyde.identification

import scala.collection.mutable.HashSet

import collection.JavaConverters.*
import scala.collection.mutable.Buffer
import idesyde.utils.CoreUtils
import idesyde.utils.Logger
import scala.collection.mutable
import idesyde.identification.MarkedIdentificationRule.DesignModelOnlyIdentificationRule

class IdentificationHandler(
    var registeredModules: Set[IdentificationModule] = Set()
)(using logger: Logger) {

  def registerIdentificationRule(identModule: IdentificationModule): IdentificationHandler = {
    registeredModules += identModule
    this
  }

  def identifyDecisionModels(
      models: Set[DesignModel],
      previouslyIdentified: Set[DecisionModel] = Set()
  ): Set[DecisionModel] = {
    var identified: Set[DecisionModel] = previouslyIdentified
    var activeRules                    = registeredModules.flatMap(m => m.identificationRules)
    var iters                          = 0
    val maxIters                       = models.map(_.elements.size).sum
    logger.info(
      s"Performing identification with ${activeRules.size} rules on ${models.size} design models."
    )
    // var currentCovered      = mutable.Set[String]()
    // var allCovered          = false
    // use all the design models first and take them away from the set
    identified ++= activeRules.flatMap(r =>
      r match {
        case DesignModelOnlyIdentificationRule(iRule) => iRule(models, identified)
        case _                                        => Set.empty
      }
    )
    activeRules = activeRules.filter(r =>
      r match {
        case r: DesignModelOnlyIdentificationRule => false
        case _                                    => true
      }
    )
    // now proceed normally
    var noNewIdentification = false
    while (activeRules.size > 0 && iters <= maxIters && !noNewIdentification) {
      val ruleResults = activeRules.flatMap(irule => irule(models, identified))
      // val reIdentified = ruleResults
      //   .flatMap((irule, res) => res)
      //   .filter(m => identified.exists(prev => prev.uniqueIdentifier == m.uniqueIdentifier && prev.coveredElements == m.coveredElements))
      val newIdentified = ruleResults.diff(identified)
      // currentCovered ++= newIdentified.map(_.coveredElementIDs).foldLeft(Set[String]())(_ ++ _)
      // add to the current identified
      identified = identified ++ newIdentified
      noNewIdentification = newIdentified.isEmpty
      // keep only non fixed rules
      // activeRules = ruleResults
      //   .filter((irule, res) => res.map(r => !reIdentified.contains(r)).getOrElse(true))
      //   .map((irule, _) => irule)
      logger.debug(
        s"identification step $iters: ${identified.size} identified"
      )
      // allCovered = models.forall(m =>
      //   m.elementIDs.subsetOf(
      //     identified.foldLeft(Set[String]())((s, mm) => s ++ mm.coveredElementIDs)
      //   )
      // )
      iters += 1
    }
    logger.debug(s"identified: ${identified.map(m => m.uniqueIdentifier)}")
    val identifiedArray = identified.toArray
    // build reachability matrix
    val reachability = identifiedArray.map(m => identifiedArray.map(mm => m.dominates(mm)))
    // get the dominant decision models (including circular dominances)
    val dominantWithoutFilter =
      CoreUtils.computeDominant(reachability).map(idx => identifiedArray(idx)).toSet
    val dominant = dominantWithoutFilter.filter(m =>
      dominantWithoutFilter
        .filter(_ != m)
        .forall(mm =>
          m.uniqueIdentifier != mm.uniqueIdentifier || (m.uniqueIdentifier == mm.uniqueIdentifier && m
            .dominates(mm))
        )
    )
    logger.debug(s"dominant: ${dominant.map(m => m.uniqueIdentifier)}")
    logger.info(s"found ${dominant.size} dominant decision model(s).")
    dominant
  }

  def integrateDecisionModel(
      model: DesignModel,
      decisions: DecisionModel
  ): Set[DesignModel] = for (
    module     <- registeredModules; integrationRule <- module.integrationRules;
    integrated <- integrationRule(model, decisions)
  ) yield integrated

}
