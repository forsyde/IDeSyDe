package idesyde.blueprints

import upickle.default._

import idesyde.core.Explorer
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.DecisionModel
import idesyde.core.ExplorationLibrary
import idesyde.utils.Logger
import idesyde.core.ExplorationCombination

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
trait ExplorationModule extends ExplorationLibrary with CanParseExplorationModuleConfiguration {

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

  inline def standaloneExplorationModule(args: Array[String]): Unit = {
    parse(args, uniqueIdentifier) match {
      case Some(value) =>
        val runPath             = value.runPath
        val dominantPathMsgPack = runPath / "dominant" / "msgpack"
        val comboMsgPack        = runPath / "combinations" / "msgpack"
        val comboJson           = runPath / "combinations" / "json"
        os.makeDir.all(comboMsgPack)
        os.makeDir.all(comboJson)
        val decisionModelHeaders =
          os.list(dominantPathMsgPack)
            .filter(_.last.startsWith("header"))
            .map(f => readBinary[DecisionModelHeader](os.read.bytes(f)))
        val combos = explorers.flatMap(explorer => {
          decisionModelDecoders.flatMap(decoder => {
            decisionModelHeaders
              .flatMap(decoder)
              .flatMap(m => {
                explorer.canExplore(m) match {
                  case true  => Some(ExplorationCombination(explorer, m))
                  case false => None
                }
              })
          })
        })
        // save the combos back on disk
        for (c <- combos) {
          os.write(
            comboMsgPack / s"combination_header_${c.explorer.uniqueIdentifier}_${c.decisionModel.uniqueIdentifier}_${uniqueIdentifier}.msgpack",
            c.header.asBinary
          )
          os.write(
            comboJson / s"combination_header_${c.explorer.uniqueIdentifier}_${c.decisionModel.uniqueIdentifier}_${uniqueIdentifier}.json",
            c.header.asText
          )
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
