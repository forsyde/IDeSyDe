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
    with IdentificationLibrary
    with ModuleUtils {

  def inputsToDesignModel(p: os.Path): Option[DesignModelHeader | DesignModel] = None

  def designModelToOutput(m: DesignModel, p: os.Path): Boolean = false

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
  def designHeaderToModel(m: DesignModelHeader): Set[DesignModel]

  /** decoders used to reconstruct decision models from headers.
    *
    * Ideally, these functions are able to produce a decision model from the headers read during a
    * call of this module.
    *
    * @return
    *   the registered decoders
    */
  def decisionHeaderToModel(m: DecisionModelHeader): Seq[DecisionModel]

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

  def integration(
      designModel: DesignModel,
      solvedDecisionModel: DecisionModel
  ): Set[DesignModel] = {
    for (
      irule      <- integrationRules;
      integrated <- irule(designModel, solvedDecisionModel)
    ) yield integrated
  }

  def identificationStep(
      stepNumber: Long,
      designModels: Set[DesignModel] = Set(),
      decisionModels: Set[DecisionModel] = Set()
  ): Set[DecisionModel] = {
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
    for (m <- identified; if !decisionModels.contains(m)) yield {
      m
    }
  }

  inline def standaloneIdentificationModule(
      args: Array[String]
  ): Unit = {
    parse(args, uniqueIdentifier) match {
      case Right(conf) =>
        conf match {
          case IdentificationModuleConfiguration(
                Some(designPath),
                _,
                Some(solvedPath),
                Some(integrationPath),
                outP,
                _
              ) =>
            os.makeDir.all(designPath)
            os.makeDir.all(solvedPath)
            os.makeDir.all(integrationPath)
            val fromDesignModelHeaders =
              os.list(designPath)
                .filter(_.last.startsWith("header"))
                .filter(_.ext == "msgpack")
                .map(f => readBinary[DesignModelHeader](os.read.bytes(f)))
                .flatMap(designHeaderToModel)
                .toSet
            val designModels =
              os.list(designPath)
                .flatMap(h => inputsToDesignModel(h))
                .flatMap(mm =>
                  mm match {
                    case dem: DesignModel          => Some(dem)
                    case header: DesignModelHeader => designHeaderToModel(header)
                  }
                )
                .toSet ++ fromDesignModelHeaders
            val solvedDecisionModels =
              os.list(solvedPath)
                .filter(_.last.startsWith("header"))
                .filter(_.ext == "msgpack")
                .map(f => readBinary[DecisionModelHeader](os.read.bytes(f)))
                .flatMap(h => decisionHeaderToModel(h))
                .toSet
            for (
              (d, i) <- designModels.zipWithIndex;
              (s, j) <- solvedDecisionModels.zipWithIndex;
              (m, k) <- integration(
                d,
                s
              ).zipWithIndex
            ) {
              val (hPath, bPath) = m.writeToPath(
                integrationPath,
                s"$i$j$k",
                uniqueIdentifier
              )
              println(
                hPath.getOrElse(
                  integrationPath / s"header_${m.uniqueIdentifier}_${uniqueIdentifier}.msgpack"
                )
              )
              outP match {
                case Some(fout) =>
                  if (os.isFile(fout)) {
                    designModelToOutput(m, fout)
                  }
                case _ =>
              }
            }
          case IdentificationModuleConfiguration(
                Some(designPath),
                Some(identifiedPath),
                _,
                _,
                _,
                iteration
              ) =>
            os.makeDir.all(designPath)
            os.makeDir.all(identifiedPath)
            val fromDesignModelHeaders =
              os.list(designPath)
                .filter(_.last.startsWith("header"))
                .filter(_.ext == "msgpack")
                .map(f => readBinary[DesignModelHeader](os.read.bytes(f)))
                .flatMap(designHeaderToModel)
                .toSet
            val designModels =
              os.list(designPath)
                .flatMap(h => inputsToDesignModel(h))
                .flatMap(mm =>
                  mm match {
                    case dem: DesignModel          => Some(dem)
                    case header: DesignModelHeader => designHeaderToModel(header)
                  }
                )
                .toSet
                ++ fromDesignModelHeaders
            for (d <- designModels) {
              d.writeToPath(designPath, "", uniqueIdentifier)
            }
            val decisionModels =
              os.list(identifiedPath)
                .filter(_.last.startsWith("header"))
                .filter(_.ext == "msgpack")
                .map(f => readBinary[DecisionModelHeader](os.read.bytes(f)))
                .flatMap(h => decisionHeaderToModel(h))
                .toSet
            val identified = identificationStep(
              iteration,
              designModels,
              decisionModels
            )
            for (m <- identified) {
              val (hPath, bPath) = m.writeToPath(
                identifiedPath,
                f"${iteration}%016d",
                uniqueIdentifier
              )
              println(
                hPath.getOrElse(
                  identifiedPath / s"header_${iteration}_${m.uniqueIdentifier}_${uniqueIdentifier}.msgpack"
                )
              )
            }
          case _ =>

        }
      case Left(usage) => println(usage)
    }
  }
}
