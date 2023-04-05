package idesyde.blueprints

import upickle.default._

import idesyde.core.Explorer
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.DecisionModel
import idesyde.core.ExplorationLibrary
import idesyde.utils.Logger
import idesyde.core.CompleteDecisionModel

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

  def canExplore(decisionModel: DecisionModel): Boolean =
    explorers.exists(_.combination(decisionModel).can_explore)

  def explore(
      decisionModel: DecisionModel,
      totalExplorationTimeOutInSecs: Long = 0L,
      maximumSolutions: Long = 0L
  ): LazyList[DecisionModel] = {
    val nonDominated =
      explorers
        .filter(_.combination(decisionModel).can_explore)
        .filterNot(e =>
          explorers
            .filter(_ != e)
            .filter(_.combination(decisionModel).can_explore)
            .forall(ee => ee.dominates(e, decisionModel, e.availableCriterias(decisionModel)))
        )
        .headOption
    nonDominated match {
      case Some(e) => e.explore(decisionModel, totalExplorationTimeOutInSecs, maximumSolutions)
      case None    => LazyList.empty
    }
  }

  inline def standaloneExplorationModule(args: Array[String]): Unit = {
    parse(args, uniqueIdentifier) match {
      case Some(value) =>
        val runPath      = value.runPath
        val dominantPath = runPath / "dominant"
        val exploredPath = runPath / "explored"
        os.makeDir.all(dominantPath)
        os.makeDir.all(exploredPath)
        (
          value.decisionModelToGetCriterias,
          value.decisionModelToGetCombination,
          value.decisionModelToExplore
        ) match {
          case (Some(f), _, _) =>
          case (_, Some(f), _) =>
            val header = readBinary[DecisionModelHeader](os.read.bytes(f))
            val combos = for (m <- decodeDecisionModels(header)) {
              println(combination(m).asText)
            }
          case (_, _, Some(f)) =>
            val header = readBinary[DecisionModelHeader](os.read.bytes(f))
            for (m <- decodeDecisionModels(header)) {
              // this not part of the for loop to have higher certainty the for loop won t make a Set out of the LazyList
              explore(m, value.explorationTotalTimeOutInSecs).zipWithIndex.foreach(
                (solved, idx) => {
                  solved.writeToPath(exploredPath, f"$idx%32d", uniqueIdentifier)
                }
              )
            }
          case _ =>
        }
        val decisionModelHeaders =
          os.list(dominantPath)
            .filter(_.last.startsWith("header"))
            .filter(_.ext == "msgpack")
            .map(f => f -> readBinary[DecisionModelHeader](os.read.bytes(f)))
            .toMap

      case _ =>
    }
  }

}
