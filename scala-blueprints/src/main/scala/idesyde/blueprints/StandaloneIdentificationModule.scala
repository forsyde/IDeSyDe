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
import os.ReadablePath
import os.FilePath
import os.Path
import os.RelPath
import os.SubPath
import java.net.ServerSocket
import scala.io.BufferedSource
import java.io.BufferedWriter
import java.io.BufferedOutputStream
import java.io.PrintStream

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

  def inputsToDesignModel(p: String): Option[DesignModelHeader | DesignModel] = None

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
          case IdentificationModuleConfiguration(_, _, _, _, _, _, _, Some("stdio")) =>
            serverStandalineIdentificationModule(
              conf,
              scala.io.StdIn.readLine,
              println
            )
          // case IdentificationModuleConfiguration(_, _, _, _, _, _, _, Some("tcp")) =>
          //   tcpServerStandalineIdentificationModule(conf)
          case IdentificationModuleConfiguration(_, _, _, _, _, _, true, None) =>
            for (schema <- decisionModelSchemas) println(schema)
          case IdentificationModuleConfiguration(
                Some(designPath),
                _,
                Some(solvedPath),
                Some(reversePath),
                outP,
                _,
                false,
                None
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
                false,
                None
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
              val (hPath, bPath, header) = m.writeToPath(
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
      case Left(usage) =>
        println("Incorrect combination of parameters/options. Check correct usage with -h/--help.")
        sys.exit(64)
    }
  }

  inline def serverStandalineIdentificationModule(
      conf: IdentificationModuleConfiguration,
      inline fetchInputLine: () => String,
      inline sendOutputLine: (String) => Unit
  ): Unit = {
    var command              = ""
    var urlsConsumed         = Set[String]()
    var designModels         = Set[DesignModel]()
    var decisionModels       = Set[DecisionModel]()
    var solvedDecisionModels = Set[DecisionModel]()
    var identifiedPath       = conf.identifiedPath.getOrElse(os.pwd / "run" / "identified")
    var designPath           = conf.designPath.getOrElse(os.pwd / "run" / "inputs")
    var solvedPath           = conf.solvedPath.getOrElse(os.pwd / "run" / "explored")
    var integrationPath      = conf.integrationPath.getOrElse(os.pwd / "run" / "integrated")
    os.makeDir.all(identifiedPath)
    os.makeDir.all(designPath)
    os.makeDir.all(solvedPath)
    os.makeDir.all(integrationPath)
    // populate the initial set just for the sake of completion
    for (path <- os.list(designPath)) {
      // headers
      if (path.baseName.startsWith("header")) {
        if (path.ext == "msgpack") {
          for (m <- designHeaderToModel(readBinary[DesignModelHeader](os.read.bytes(path))))
            designModels += m
        } else if (path.ext == "json") {
          for (m <- designHeaderToModel(read[DesignModelHeader](os.read(path))))
            designModels += m
        }
      } else {
        for (mread <- inputsToDesignModel(path)) {
          mread match {
            case d: DesignModel => {
              val h = d.header.copy(model_paths = Set(path.toString()))
              h.writeToPath(designPath, path.baseName, uniqueIdentifier)
              designModels += d
            }
            case h: DesignModelHeader => for (m <- designHeaderToModel(h)) designModels += m
          }
        }
      }
      // write down the found design models
      urlsConsumed += path.toString
    }
    // now the decision models
    for (path <- os.list(identifiedPath); if path.baseName.startsWith("header")) {
      if (path.ext == "msgpack") {
        for (m <- decisionHeaderToModel(readBinary[DecisionModelHeader](os.read.bytes(path))))
          decisionModels += m
      } else if (path.ext == "json") {
        for (m <- decisionHeaderToModel(read[DecisionModelHeader](os.read(path))))
          decisionModels += m
      }
      urlsConsumed += path.toString
    }
    // finally, for the solved ones
    for (path <- os.list(solvedPath); if path.baseName.startsWith("header")) {
      if (path.ext == "msgpack") {
        for (m <- decisionHeaderToModel(readBinary[DecisionModelHeader](os.read.bytes(path))))
          solvedDecisionModels += m
      } else if (path.ext == "json") {
        for (m <- decisionHeaderToModel(read[DecisionModelHeader](os.read(path))))
          solvedDecisionModels += m
      }
      urlsConsumed += path.toString
    }
    sendOutputLine("INITIALIZED")
    while (command != "EXIT") {
      command = fetchInputLine()
      // now branch, depending on the command passed
      if (command == null) { // likely recieved a SIGNIT, so we just shutdown
      } else if (command.startsWith("DESIGN")) {
        val url  = command.substring(6).strip()
        val path = stringToPath(url)
        if (!urlsConsumed.contains(url)) {
          if (path.baseName.startsWith("header")) {
            for (
              decoded <- decodeFromPath[DesignModelHeader](url); m <- designHeaderToModel(decoded)
            )
              designModels += m
          } else {
            for (mread <- inputsToDesignModel(path)) {
              mread match {
                case d: DesignModel => {
                  val h = d.header.copy(model_paths = Set(path.toString()))
                  h.writeToPath(designPath, path.baseName, uniqueIdentifier)
                  designModels += d
                }
                case h: DesignModelHeader => for (m <- designHeaderToModel(h)) designModels += m
              }
            }
          }
          urlsConsumed += url
        }
      } else if (command.startsWith("DECISION INLINE")) {
        val payload = command.substring(15).strip()
        for (m <- decisionHeaderToModel(read[DecisionModelHeader](payload))) decisionModels += m
      } else if (command.startsWith("DECISION PATH")) {
        val url = command.substring(8).strip()
        if (!urlsConsumed.contains(url)) {
          for (
            decoded <- decodeFromPath[DecisionModelHeader](url);
            m       <- decisionHeaderToModel(decoded)
          ) {

            decisionModels += m
          }
          urlsConsumed += url
        }
      } else if (command.startsWith("SOLVED INLINE")) {
        val payload = command.substring(13).strip()
        for (m <- decisionHeaderToModel(read[DecisionModelHeader](payload)))
          solvedDecisionModels += m
      } else if (command.startsWith("SOLVED")) {
        val url  = command.substring(6).strip()
        val path = os.Path(url)
        if (!urlsConsumed.contains(url)) {
          if (path.ext == "msgpack") {
            for (
              decoded <- decodeFromPath[DecisionModelHeader](url);
              m       <- decisionHeaderToModel(decoded)
            )
              solvedDecisionModels += m
          } else if (path.ext == "json") {
            for (
              decoded <- decodeFromPath[DecisionModelHeader](url);
              m       <- decisionHeaderToModel(decoded)
            )
              solvedDecisionModels += m
          }
          urlsConsumed += url
        }
      } else if (command.startsWith("IDENTIFY")) {
        var iteration = command.substring(8).strip().toInt
        val identified = identificationStep(
          iteration,
          designModels,
          decisionModels
        )
        val newIdentified = identified -- decisionModels
        sendOutputLine("NEW " + newIdentified.size)
        for (m <- newIdentified) {
          val (hPath, bPath, header) = m.writeToPath(
            identifiedPath,
            f"${iteration}%016d",
            uniqueIdentifier
          )
          sendOutputLine(
            "DECISION INLINE" + header.asText
            // hPath
            //   .getOrElse(
            //     identifiedPath / s"header_${iteration}_${m.category}_${uniqueIdentifier}.msgpack"
            //   )
            //   .toString
          )
        }
        decisionModels ++= identified
      } else if (command.startsWith("INTEGRATE")) {
        val url = command.substring(9).strip()
        val integrated = reverseIdentification(
          solvedDecisionModels,
          designModels
        )
        sendOutputLine("INTEGRATED " + integrated.size)
        for (
          (m, k)  <- integrated.zipWithIndex;
          written <- designModelToOutput(m, integrationPath)
        ) {
          val header = m.header.copy(model_paths = Set(written.toString))
          val (hPath, bPath) = header.writeToPath(
            integrationPath,
            s"${k}",
            uniqueIdentifier
          )
          sendOutputLine(
            "DESIGN " + hPath
              .getOrElse(
                integrationPath / s"header_${m.category}_${uniqueIdentifier}.msgpack"
              )
              .toString
          )
          if (url.length() > 0) {
            designModelToOutput(m, os.Path(url))
          }
        }
      } else if (command.startsWith("STAT")) {
        sendOutputLine("DECISION " + decisionModels.toVector.map(_.category).mkString(", "))
        sendOutputLine("DESIGN " + designModels.toVector.map(_.category).mkString(", "))
        sendOutputLine("SOLVED " + solvedDecisionModels.toVector.map(_.category).mkString(", "))
      } else if (!command.startsWith("EXIT")) {
        logger.error("Passed invalid command: " + command)
      }
    }
  }

}
