package idesyde.identification

import scala.collection.mutable.HashSet

import collection.JavaConverters.*
import scala.collection.mutable.Buffer
import idesyde.utils.Logger
import scala.collection.mutable
import idesyde.utils.HasUtils
import scala.annotation.targetName
import idesyde.core.MarkedIdentificationRule
import idesyde.core.DecisionModel
import idesyde.core.DesignModel

trait CanIdentify(using logger: Logger) extends HasUtils {

  @targetName("identifyDecisionModelsWithModules")
  def identifyDecisionModels(
      models: Set[DesignModel],
      identificationModules: Set[IdentificationModule],
      startingDecisionModels: Set[DecisionModel] = Set()
  ): Set[DecisionModel] = identifyDecisionModels(
    models,
    identificationModules.flatMap(_.identificationRules),
    startingDecisionModels
  )

  @targetName("identifyDecisionModelsWithRules")
  def identifyDecisionModels(
      models: Set[DesignModel],
      identificationRules: Set[
        (Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]
      ],
      startingDecisionModels: Set[DecisionModel]
  ): Set[DecisionModel] = {
    var identified: Set[DecisionModel] = startingDecisionModels
    var activeRules                    = identificationRules
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
        case MarkedIdentificationRule.DesignModelOnlyIdentificationRule(iRule) =>
          iRule(models, identified)
        case _ => Set.empty
      }
    )
    activeRules = activeRules.filter(r =>
      r match {
        case r: MarkedIdentificationRule.DesignModelOnlyIdentificationRule => false
        case _                                                             => true
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
    val identifiedArray = identified.toVector
    // build reachability matrix
    val reachability = identifiedArray.map(m => identifiedArray.map(mm => m.dominates(mm)))
    // get the dominant decision models (including circular dominances)
    val dominantWithoutFilter =
      computeSSCFromReachibility(reachibilityClosure(reachability))
        .map(idx => identifiedArray(idx))
        .toSet
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

  @targetName("integrateDecisionModelWithModules")
  def integrateDecisionModel(
      model: DesignModel,
      decisions: DecisionModel,
      integrationModules: Set[IdentificationModule] = Set()
  ): Set[DesignModel] =
    integrateDecisionModel(model, decisions, integrationModules.flatMap(_.integrationRules))

  @targetName("integrateDecisionModelWithRules")
  def integrateDecisionModel(
      model: DesignModel,
      decisions: DecisionModel,
      integrationRules: Set[
        (DesignModel, DecisionModel) => Option[? <: DesignModel]
      ]
  ): Set[DesignModel] = for (
    integrationRule <- integrationRules;
    integrated      <- integrationRule(model, decisions)
  ) yield integrated

  // private def reachibilityClosure(matrix: Vector[Vector[Boolean]]): Vector[Vector[Boolean]] = {
  //   // necessary step to clone it
  //   val closure  = matrix.map(row => row.toBuffer).toBuffer
  //   val numElems = matrix.length
  //   for (
  //     k <- 0 until numElems;
  //     i <- 0 until numElems;
  //     j <- 0 until numElems;
  //     if i != k
  //   ) {
  //     closure(i)(j) = closure(i)(j) || (closure(i)(k) && closure(k)(j))
  //   }
  //   closure.map(_.toVector).toVector
  // }

  // def computeSSCFromReachibility(reachability: Vector[Vector[Boolean]]): Set[Int] = {
  //   var lowMask     = Buffer.fill(reachability.size)(0)
  //   val numElements = reachability.size
  //   for (
  //     k <- 0 until numElements;
  //     i <- 0 until numElements - 1;
  //     j <- i + 1 until numElements
  //     // if there exists any dominance path forward and back
  //   ) {
  //     if (reachability(i)(j) && !reachability(j)(i)) {
  //       lowMask(j) = Math.max(lowMask(j), lowMask(i) + 1)
  //     } else if (!reachability(i)(j) && reachability(j)(i)) {
  //       lowMask(i) = Math.max(lowMask(i), lowMask(j) + 1)
  //     } else if (reachability(i)(j) && reachability(j)(i)) {
  //       lowMask(i) = Math.max(lowMask(i), lowMask(j))
  //       lowMask(j) = Math.max(lowMask(i), lowMask(j))
  //     }
  //   }
  //   lowMask.zipWithIndex.filter((v, i) => v == 0).map((v, i) => i).toSet
  // }

}
