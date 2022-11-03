package idesyde.identification

import java.util.stream.Collectors

import scala.collection.mutable.HashSet

import collection.JavaConverters.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import scala.collection.mutable.Buffer
import idesyde.utils.CoreUtils
import idesyde.utils.Logger

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
    var prevIdentified                 = -1
    logger.info(
      s"Performing identification with ${activeRules.size} rules on ${models.size} design models."
    )
    var allCovered = false
    while (activeRules.size > 0 && iters <= maxIters && !allCovered) {
      prevIdentified = identified.size
      val ruleResults = activeRules.map(irule => (irule, irule(models, identified)))
      val reIdentified = ruleResults
        .flatMap((irule, res) => res)
        .filter(m => identified.exists(prev => prev.coveredElements == m.coveredElements))
      val newIdentified =
        ruleResults.flatMap((irule, res) => res).filter(res => !reIdentified.contains(res))
      // add to the current identified
      identified = identified ++ newIdentified
      // keep only non fixed rules
      activeRules = ruleResults
        .filter((irule, res) => res.map(r => !reIdentified.contains(r)).getOrElse(true))
        .map((irule, _) => irule)
      logger.debug(
        s"identification step $iters: ${identified.size} identified and ${activeRules.size} rules"
      )
      allCovered =
        models.forall(m => identified.exists(mm => m.elementIDs.subsetOf(mm.coveredElementIDs)))
      iters += 1
    }
    // build reachability matrix
    logger.debug(s"identified: ${identified.map(m => m.uniqueIdentifier)}")
    val identifiedArray = identified.toArray
    val reachability    = identifiedArray.map(m => identifiedArray.map(mm => m.dominates(mm)))
    // get the dominant decision models (including circular dominances)
    val dominant = CoreUtils.computeDominant(reachability).map(idx => identifiedArray(idx)).toSet
    logger.debug(s"dominant: ${dominant.map(m => m.uniqueIdentifier)}")
    logger.info(s"found ${dominant.size} dominant decision model(s).")
    dominant
  }

}
