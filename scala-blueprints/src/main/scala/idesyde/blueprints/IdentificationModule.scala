package idesyde.blueprints

import upickle.default._

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger
import idesyde.utils.SimpleStandardIOLogger
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.MarkedIdentificationRule
import idesyde.core.CompleteDecisionModel
import idesyde.core.IdentificationLibrary
import scala.collection.mutable

/** The trait/interface for an identification module that provides the identification and
  * integration rules required to power the design space identification process [1].
  *
  * This trait extends [[idesyde.core.IdentificationLibrary]] to push further the modularization of
  * the DSI methodology. In essence, this trait transforms an [[idesyde.core.IdentificationLibrary]]
  * into an independent callable library, which can be orchestrated externally. This enables modules
  * in different languages to cooperate seamlessly.
  *
  * @see
  *   [[idesyde.core.IdentificationLibrary]]
  */
trait IdentificationModule
    extends CanParseIdentificationModuleConfiguration
    with IdentificationLibrary {

  def inputsToDesign: Set[(os.Path) => Option[DesignModelHeader | DesignModel]] = Set()

  /** decoders used to reconstruct design models from headers.
    *
    * Ideally, these functions are able to produce a design model from the headers read during a
    * call of this module.
    *
    * The decoders return [[Set]] instead of [[Option]] because a header might refer to multiple
    * design model bodies.
    *
    * @return
    *   the registered decoders
    */
  def designModelDecoders: Set[(DesignModelHeader) => Set[DesignModel]]

  /** decoders used to reconstruct decision models from headers.
    *
    * Ideally, these functions are able to produce a decision model from the headers read during a
    * call of this module.
    *
    * @return
    *   the registered decoders
    */
  def decisionModelDecoders: Set[(DecisionModelHeader) => Option[DecisionModel]]

  /** Unique string used to identify this module during orchetration. Ideally it matches the name of
    * the implementing class (or is the implemeting class name, ditto).
    */
  def uniqueIdentifier: String

  /** the logger to be used during a module call.
    *
    * @return
    *   the registered logger
    * @see
    *   [[idesyde.utils.Logger]]
    */
  def logger: Logger = SimpleStandardIOLogger("WARN")

  inline def identificationStep(
      runPath: os.Path,
      stepNumber: Long,
      loadedDesignModels: Set[DesignModel] = Set()
  ): Set[DecisionModel] = {
    val designModelsPath          = runPath / "inputs" / "msgpack"
    val decisionModelsPathMsgPack = runPath / "identified" / "msgpack"
    val decisionModelsPathJson    = runPath / "identified" / "json"
    val designModelHeaders =
      os.list(designModelsPath)
        .filter(_.last.startsWith("header"))
        .map(f => readBinary[DesignModelHeader](os.read.bytes(f)))
    val loadedDecisionModelHeaders = loadedDesignModels.map(_.header)
    val decisionModelHeaders =
      os.list(decisionModelsPathMsgPack)
        .filter(_.last.startsWith("header"))
        .map(f => readBinary[DecisionModelHeader](os.read.bytes(f)))
    val designModels =
      designModelDecoders.flatMap(dec =>
        designModelHeaders.filter(!loadedDecisionModelHeaders.contains(_)).flatMap(h => dec(h))
      ) ++ loadedDesignModels
    val decisionModels =
      decisionModelDecoders.flatMap(dec => decisionModelHeaders.flatMap(h => dec(h)))
    val iterRules = if (stepNumber == 0L) {
      identificationRules.flatMap(_ match {
        case r: MarkedIdentificationRule.DecisionModelOnlyIdentificationRule         => None
        case r: MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule => None
        case r                                                                       => Some(r)
      })
    } else if (stepNumber > 0L) {
      identificationRules.flatMap(_ match {
        case r: MarkedIdentificationRule.DesignModelOnlyIdentificationRule => None
        case r                                                             => Some(r)
      })
    } else identificationRules
    val identified = iterRules.flatMap(irule => irule(designModels, decisionModels))
    for (
      (m, i) <- identified.zipWithIndex; h = m.header; if !decisionModelHeaders.contains(m.header)
    ) yield {
      val h = m match {
        case cm: CompleteDecisionModel =>
          os.write.over(
            decisionModelsPathJson / s"body_${stepNumber}_${uniqueIdentifier}_${m.uniqueIdentifier}.json",
            cm.bodyAsText
          )
          os.write.over(
            decisionModelsPathMsgPack / s"body_${stepNumber}_${uniqueIdentifier}_${m.uniqueIdentifier}.msgpack",
            cm.bodyAsBinary
          )
          cm.header.copy(body_path =
            Some(
              (decisionModelsPathMsgPack / s"body_${stepNumber}_${uniqueIdentifier}_${m.uniqueIdentifier}.msgpack").toString
            )
          )
        case _ =>
          m.header
      }
      os.write.over(
        decisionModelsPathJson / s"header_${stepNumber}_${uniqueIdentifier}_${m.uniqueIdentifier}.json",
        h.asText
      )
      os.write.over(
        decisionModelsPathMsgPack / s"header_${stepNumber}_${uniqueIdentifier}_${m.uniqueIdentifier}.msgpack",
        h.asBinary
      )
      m
    }
  }

  inline def standaloneIdentificationModule(
      args: Array[String]
  ): Unit = {
    parse(args, uniqueIdentifier) match {
      case Some(value) =>
        val runPath                   = value.runPath
        val inputsPath                = runPath / "inputs"
        val inputsPathJson            = runPath / "inputs" / "json"
        val inputsPathMspack          = runPath / "inputs" / "msgpack"
        val decisionModelsPathMsgPack = runPath / "identified" / "msgpack"
        val identStep                 = value.identificationStep
        var loadedDesignModels        = mutable.Set[DesignModel]()
        for (f <- os.walk(inputsPath); itoh <- inputsToDesign) {
          for (m <- itoh(f)) {
            m match {
              case header: DesignModelHeader =>
                os.write.over(
                  inputsPathMspack / s"header_${uniqueIdentifier}_${header.category}.msgpack",
                  header.asBinary
                )
                os.write.over(
                  inputsPathJson / s"header_${uniqueIdentifier}_${header.category}.json",
                  header.asText
                )
              case model: DesignModel =>
                loadedDesignModels.add(model)
                os.write.over(
                  inputsPathMspack / s"header_${uniqueIdentifier}_${model.uniqueIdentifier}.msgpack",
                  model.header.copy(model_paths = Set(f.toString)).asBinary
                )
                os.write.over(
                  inputsPathJson / s"header_${uniqueIdentifier}_${model.uniqueIdentifier}.json",
                  model.header.copy(model_paths = Set(f.toString)).asText
                )
            }
          }
        }
        if (value.shouldIdentify) {
          val identified = identificationStep(runPath, identStep, loadedDesignModels.toSet)
          for (m <- identified) {
            println(
              decisionModelsPathMsgPack / s"header_${identStep}_${uniqueIdentifier}_${m.uniqueIdentifier}.msgpack"
            )
          }
        }
      case None =>
    }
  }

  def decodeFromPath[T: ReadWriter](p: String): Option[T] = {
    if (p.endsWith(".msgpack")) Some(readBinary[T](os.read.bytes(os.pwd / p)))
    else if (p.endsWith(".json")) Some(read[T](os.read(os.pwd / p)))
    else None
  }
}