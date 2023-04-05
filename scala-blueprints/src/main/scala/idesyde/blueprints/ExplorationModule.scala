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
    with CanParseExplorationModuleConfiguration {

  /** decoders used to reconstruct decision models from headers.
    *
    * Ideally, these functions are able to produce a decision model from the headers read during a
    * call of this module.
    *
    * @return
    *   the registered decoders
    */
  def decisionModelDecoders: Set[(DecisionModelHeader) => Option[DecisionModel]]

  /** the logger to be used during a module call.
    *
    * @return
    *   the registered logger
    * @see
    *   [[idesyde.utils.Logger]]
    */
  def logger: Logger

  def explorers: Set[Explorer]

  /** Unique string used to identify this module during orchetration. Ideally it matches the name of
    * the implementing class (or is the implemeting class name, ditto).
    */
  def uniqueIdentifier: String

  def canExplore(decisionModel: DecisionModel): Boolean =
    explorers.exists(_.combination(decisionModel).can_explore)

  def explore(
      decisionModel: DecisionModel,
      totalExplorationTimeOutInSecs: Long
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
      case Some(e) => e.explore(decisionModel, totalExplorationTimeOutInSecs)
      case None    => LazyList.empty
    }
  }

  inline def standaloneExplorationModule(args: Array[String]): Unit = {
    parse(args, uniqueIdentifier) match {
      case Some(value) =>
        val runPath             = value.runPath
        val dominantPathMsgPack = runPath / "dominant" / "msgpack"
        val comboMsgPack        = runPath / "combinations" / "msgpack"
        val comboJson           = runPath / "combinations" / "json"
        val exploredMsgPack     = runPath / "explored" / "msgpack"
        val exploredJson        = runPath / "explored" / "json"
        os.makeDir.all(comboMsgPack)
        os.makeDir.all(comboJson)
        os.makeDir.all(exploredMsgPack)
        os.makeDir.all(exploredJson)
        val decisionModelHeaders =
          os.list(dominantPathMsgPack)
            .filter(_.last.startsWith("header"))
            .map(f => f -> readBinary[DecisionModelHeader](os.read.bytes(f)))
            .toMap
        for (
          explorer    <- explorers; decoder <- decisionModelDecoders;
          (_, header) <- decisionModelHeaders;
          m           <- decoder(header)
        ) {
          println(explorer.combination(m).asText)
        }
        // val combos = explorers.flatMap(explorer => {
        //   decisionModelDecoders.flatMap(decoder => {
        //     decisionModelHeaders
        //       .flatMap((p, m) => decoder(m))
        //       .flatMap(m => {
        //         explorer.canExplore(m) match {
        //           case true  => Some(ExplorationCombination(explorer, m))
        //           case false => None
        //         }
        //       })
        //   })
        // })
        // save the combos back on disk
        // for (c <- combos) {
        //   os.write(
        //     comboMsgPack / s"combination_header_${c.explorer.uniqueIdentifier}_${c.decisionModel.uniqueIdentifier}_${uniqueIdentifier}.msgpack",
        //     c.header.asBinary
        //   )
        //   os.write(
        //     comboJson / s"combination_header_${c.explorer.uniqueIdentifier}_${c.decisionModel.uniqueIdentifier}_${uniqueIdentifier}.json",
        //     c.header.asText
        //   )
        // }
        // now explore if required
        for (
          headerPath <- value.chosenDecisionModel; header <- decisionModelHeaders.get(headerPath);
          decoder    <- decisionModelDecoders; m          <- decoder(header)
        ) {
          // this not part of the for loop to have higher certainty the for loop won t make a Set out of the LazyList
          explore(m, value.explorationTotalTimeOutInSecs).foreach(solved => {
            val header = solved match {
              case comp: CompleteDecisionModel =>
                os.write(
                  exploredMsgPack / s"body_${solved.uniqueIdentifier}_${uniqueIdentifier}.msgpack",
                  comp.bodyAsBinary
                )
                os.write(
                  exploredJson / s"body_${solved.uniqueIdentifier}_${uniqueIdentifier}.json",
                  comp.bodyAsText
                )
                comp.header.copy(body_path =
                  Some(
                    (exploredMsgPack / s"body_${solved.uniqueIdentifier}_${uniqueIdentifier}.msgpack").toString
                  )
                )
              case _ =>
                solved.header
            }
            os.write(
              exploredMsgPack / s"header_${solved.uniqueIdentifier}_${uniqueIdentifier}.msgpack",
              header.asBinary
            )
            os.write(
              exploredJson / s"header_${solved.uniqueIdentifier}_${uniqueIdentifier}.json",
              header.asText
            )
          })
        }
      case _ =>
    }
  }

  def decodeFromPath[T: ReadWriter](p: String): Option[T] = {
    if (p.endsWith(".msgpack")) Some(readBinary[T](os.read.bytes(os.pwd / p)))
    else if (p.endsWith(".json")) Some(read[T](os.read(os.pwd / p)))
    else None
  }
}
