package idesyde.blueprints

import upickle.default._

import idesyde.core.Explorer
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.DecisionModel
import idesyde.core.ExplorationModule
import idesyde.utils.Logger
import idesyde.core.CompleteDecisionModel
import idesyde.core.ExplorationCombinationDescription

/** The trait/interface for an exploration module that provides the explorers rules required to
  * explored identified design spaces [1].
  *
  * Like [[idesyde.blueprints.IdentificationModule]], this trait extends
  * [[idesyde.core.ExplorationModule]] to an independent callable, which can be used externally in a
  * multi-language exploration process.
  *
  * @see
  *   [[idesyde.core.ExplorationModule]]
  */
trait StandaloneExplorationModule
    extends ExplorationModule
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
  def decodeDecisionModels(m: DecisionModelHeader): Option[DecisionModel]

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

  def standaloneExplorationModule(args: Array[String]): Unit = {
    parse(args, uniqueIdentifier) match {
      case Some(conf) =>
        conf match {
          case ExplorationModuleConfiguration(
                Some(dominantPath),
                Some(solutionPath),
                _,
                _,
                Some(decisionModelToExplore),
                timeResolution,
                memoryResolution,
                explorerIdxOpt,
                maximumSolutions,
                explorationTotalTimeOutInSecs
              ) =>
            os.makeDir.all(dominantPath)
            os.makeDir.all(solutionPath)
            val header = readBinary[DecisionModelHeader](os.read.bytes(decisionModelToExplore))
            decodeDecisionModels(header) match {
              case Some(m) =>
                explorerIdxOpt match {
                  case Some(idx) =>
                    explore(
                      m,
                      idx,
                      explorationTotalTimeOutInSecs,
                      maximumSolutions,
                      timeResolution.getOrElse(-1L),
                      memoryResolution.getOrElse(-1L)
                    ).zipWithIndex.foreach((solved, idx) => {
                      val (hPath, bPath) =
                        solved.writeToPath(solutionPath, f"$idx%016d", uniqueIdentifier)
                      println(hPath.get)
                    })
                  case None =>
                    exploreBest(
                      m,
                      explorationTotalTimeOutInSecs,
                      maximumSolutions,
                      timeResolution.getOrElse(-1L),
                      memoryResolution.getOrElse(-1L)
                    ).zipWithIndex.foreach((solved, idx) => {
                      val (hPath, bPath) =
                        solved.writeToPath(solutionPath, f"$idx%016d", uniqueIdentifier)
                      println(hPath.get)
                    })
                }
              case None =>
            }
          case ExplorationModuleConfiguration(
                Some(dominantPath),
                _,
                _,
                Some(decisionModelToGetCriterias),
                _,
                _,
                _,
                _,
                _,
                _
              ) =>
            val header = readBinary[DecisionModelHeader](os.read.bytes(decisionModelToGetCriterias))
            decodeDecisionModels(header) match {
              case Some(m) => for (comb <- combination(m)) println(comb.asText)
              case None    =>

            }
          case _ =>
        }
      case _ =>
    }
  }

}
