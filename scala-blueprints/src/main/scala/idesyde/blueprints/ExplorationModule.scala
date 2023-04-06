package idesyde.blueprints

import upickle.default._

import idesyde.core.Explorer
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.DecisionModel
import idesyde.core.ExplorationLibrary
import idesyde.utils.Logger
import idesyde.core.CompleteDecisionModel
import idesyde.core.ExplorationCombinationDescription

/** The trait/interface for an exploration module that provides the explorers rules required to
  * explored identified design spaces [1].
  *
  * Like [[idesyde.blueprints.IdentificationModule]], this trait extends
  * [[idesyde.core.ExplorationLibrary]] to an independent callable, which can be used externally in
  * a multi-language exploration process.
  *
  * @see
  *   [[idesyde.core.ExplorationLibrary]]
  */
trait ExplorationModule
    extends Explorer
    with ExplorationLibrary
    with CanParseExplorationModuleConfiguration
    with ModuleUtils {

  /** decoders used to reconstruct decision models from headers.
    *
    * Ideally, these functions are able to produce a decision model from the headers read during a
    * call of this module.
    *
    * @return
    *   decoded [[idesyde.core.DecisionModel]]
    */
  def decodeDecisionModels(m: DecisionModelHeader): Seq[DecisionModel]

  /** the logger to be used during a module call.
    *
    * @return
    *   the registered logger
    * @see
    *   [[idesyde.utils.Logger]]
    */
  def logger: Logger

  /** Unique string used to identify this module during orchetration. Ideally it matches the name of
    * the implementing class (or is the implemeting class name, ditto).
    */
  def uniqueIdentifier: String

  def canExplore(decisionModel: DecisionModel): Boolean =
    explorers.exists(_.combination(decisionModel).can_explore)

  def explore(
      decisionModel: DecisionModel,
      totalExplorationTimeOutInSecs: Long = 0L,
      maximumSolutions: Long = 0L
  ): LazyList[DecisionModel] = {
    val valid = explorers
      .filter(_.combination(decisionModel).can_explore)
    val nonDominated =
      valid
        .filter(e =>
          !valid
            .filter(_ != e)
            .exists(ee => ee.dominates(e, decisionModel))
        )
        .headOption
    nonDominated match {
      case Some(e) => e.explore(decisionModel, totalExplorationTimeOutInSecs, maximumSolutions)
      case None    => LazyList.empty
    }
  }

  def standaloneExplorationModule(args: Array[String]): Unit = {
    parse(args, uniqueIdentifier) match {
      case Some(value) =>
        os.makeDir.all(value.dominantPath)
        os.makeDir.all(value.solutionPath)
        (
          value.decisionModelToGetCriterias,
          value.decisionModelToGetCombination,
          value.decisionModelToExplore
        ) match {
          case (Some(f), _, _) =>
          case (_, Some(f), _) =>
            val header = readBinary[DecisionModelHeader](os.read.bytes(f))
            decodeDecisionModels(header) match {
              case head :: next => println(combination(head).asText)
              case Nil          => println(ExplorationCombinationDescription.impossible.asText)
            }
          case (_, _, Some(f)) =>
            val header = readBinary[DecisionModelHeader](os.read.bytes(f))
            decodeDecisionModels(header) match {
              case head :: next =>
                explore(head, value.explorationTotalTimeOutInSecs).zipWithIndex.foreach(
                  (solved, idx) => {
                    val (hPath, bPath) =
                      solved.writeToPath(value.solutionPath, f"$idx%016d", uniqueIdentifier)
                    println(hPath.get)
                  }
                )
              case Nil =>
            }
          case _ =>
        }
      case _ =>
    }
  }

}
