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
import idesyde.core.IdentificationModule
import scala.collection.mutable
import scala.collection.mutable.Buffer
import scala.quoted._

/** The trait/interface for an identification module that provides the identification and
  * integration rules required to power the design space identification process [1].
  *
  * This trait extends [[idesyde.core.IdentificationModule]] to push further the modularization of
  * the DSI methodology. In essence, this trait transforms an [[idesyde.core.IdentificationModule]]
  * into an independent callable library, which can be orchestrated externally. This enables modules
  * in different languages to cooperate seamlessly.
  *
  * @see
  *   [[idesyde.core.IdentificationModule]]
  */
trait StandaloneIdentificationModule
    extends CanParseIdentificationModuleConfiguration
    with IdentificationModule
    with ModuleUtils {

  def inputsToDesignModel(p: os.Path): Option[DesignModelHeader | DesignModel] = None

  def designModelToOutput(m: DesignModel, p: os.Path): Option[os.Path] = None

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
  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel]

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

  def decisionModelSchemas: Vector[String] = Vector()

  inline def standaloneIdentificationModule(
      args: Array[String]
  ): Unit = {
    parse(args, uniqueIdentifier) match {
      case Right(conf) =>
        conf match {
          case IdentificationModuleConfiguration(_, _, _, _, _, _, true) =>
            for (schema <- decisionModelSchemas) println(schema)
          case IdentificationModuleConfiguration(
                Some(designPath),
                _,
                Some(solvedPath),
                Some(reversePath),
                outP,
                _,
                false
              ) =>
            os.makeDir.all(designPath)
            os.makeDir.all(solvedPath)
            os.makeDir.all(reversePath)
            val fromDesignModelHeaders =
              os.list(designPath)
                .filter(_.last.startsWith("header"))
                .filter(_.ext == "msgpack")
                .map(f => readBinary[DesignModelHeader](os.read.bytes(f)))
                .flatMap(designHeaderToModel)
                .toSet
            val designModelsBeforeMerge =
              os.list(designPath)
                .flatMap(h => inputsToDesignModel(h))
                .flatMap(mm =>
                  mm match {
                    case dem: DesignModel          => Some(dem)
                    case header: DesignModelHeader => designHeaderToModel(header)
                  }
                )
                .toSet ++ fromDesignModelHeaders
            val designModels = designModelsBeforeMerge.foldLeft(Set[DesignModel]())((s, m) => {
              if (s.isEmpty || s.forall(_.merge(m).isEmpty)) { s + m }
              else {
                s.map(prev =>
                  prev.merge(m) match {
                    case Some(ok) => ok
                    case _        => prev
                  }
                )
              }
            })
            val solvedDecisionModels =
              os.list(solvedPath)
                .filter(_.last.startsWith("header"))
                .filter(_.ext == "msgpack")
                .map(f => readBinary[DecisionModelHeader](os.read.bytes(f)))
                .flatMap(h => decisionHeaderToModel(h))
                .toSet
            for (
              (m, k) <- reverseIdentification(
                solvedDecisionModels,
                designModels
              ).zipWithIndex;
              written <- designModelToOutput(m, reversePath)
            ) {
              val header = m.header.copy(model_paths = Set(written.toString))
              val (hPath, bPath) = header.writeToPath(
                reversePath,
                s"${k}",
                uniqueIdentifier
              )
              println(
                hPath
                  .getOrElse(
                    reversePath / s"header_${m.category}_${uniqueIdentifier}.msgpack"
                  )
                  .toString
              )
              outP match {
                case Some(fout) =>
                  designModelToOutput(m, fout)
                case _ =>
              }
            }
          case IdentificationModuleConfiguration(
                Some(designPath),
                Some(identifiedPath),
                _,
                _,
                _,
                iteration,
                false
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
                .map(p => (p, inputsToDesignModel(p)))
                .flatMap((p, mm) =>
                  mm match {
                    case Some(m) =>
                      m match {
                        case dem: DesignModel => {
                          val h = dem.header.copy(model_paths = Set(p.toString()))
                          h.writeToPath(designPath, p.baseName, uniqueIdentifier)
                          Some(dem)
                        }
                        case deh: DesignModelHeader => designHeaderToModel(deh)
                      }
                    case None => None
                  }
                )
                .toSet
                ++ fromDesignModelHeaders
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
            for (m <- identified; if !decisionModels.contains(m)) {
              val (hPath, bPath) = m.writeToPath(
                identifiedPath,
                f"${iteration}%016d",
                uniqueIdentifier
              )
              println(
                hPath
                  .getOrElse(
                    identifiedPath / s"header_${iteration}_${m.category}_${uniqueIdentifier}.msgpack"
                  )
                  .toString
              )
            }
          case _ =>

        }
      case Left(usage) => println(usage)
    }
  }
}
