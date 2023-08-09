package idesyde.blueprints

import upickle.default._

import idesyde.core.Explorer
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.DecisionModel
import idesyde.core.ExplorationModule
import idesyde.utils.Logger
import idesyde.core.CompleteDecisionModel
import idesyde.core.ExplorationCombinationDescription
import scala.collection.mutable

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
  def decisionHeaderToModel(m: DecisionModelHeader): Option[DecisionModel]

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
          case ExplorationModuleConfiguration(_, _, _, _, _, _, _, _, _, _, Some("stdio")) =>
            serverStandaloneExplorationModule(conf, scala.io.StdIn.readLine, println)
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
                explorationTotalTimeOutInSecs,
                _
              ) =>
            os.makeDir.all(dominantPath)
            os.makeDir.all(solutionPath)
            val header = readBinary[DecisionModelHeader](os.read.bytes(decisionModelToExplore))
            decisionHeaderToModel(header) match {
              case Some(m) =>
                explorerIdxOpt match {
                  case Some(idx) =>
                    explore(
                      m,
                      idx,
                      Set(),
                      explorationTotalTimeOutInSecs,
                      maximumSolutions,
                      timeResolution.getOrElse(-1L),
                      memoryResolution.getOrElse(-1L)
                    ).zipWithIndex.foreach((solvedWithObj, idx) => {
                      val (solved, _) = solvedWithObj
                      val (hPath, bPath, header) =
                        solved.writeToPath(solutionPath, f"$idx%016d", uniqueIdentifier)
                      println(hPath.get)
                    })
                  case None =>
                    exploreBest(
                      m,
                      Set(),
                      explorationTotalTimeOutInSecs,
                      maximumSolutions,
                      timeResolution.getOrElse(-1L),
                      memoryResolution.getOrElse(-1L)
                    ).zipWithIndex.foreach((solvedWithObj, idx) => {
                      val (solved, _) = solvedWithObj
                      val (hPath, bPath, header) =
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
                _,
                _
              ) =>
            val header = readBinary[DecisionModelHeader](os.read.bytes(decisionModelToGetCriterias))
            decisionHeaderToModel(header) match {
              case Some(m) => for (comb <- combination(m)) println(comb.asText)
              case None    =>

            }
          case _ =>
        }
      case _ =>
    }
  }

  inline def serverStandaloneExplorationModule(
      conf: ExplorationModuleConfiguration,
      inline fetchInputLine: () => String,
      inline sendOutputLine: (String) => Unit
  ): Unit = {
    var command                       = ""
    var urlsConsumed                  = Set[String]()
    var decisionModels                = Set[DecisionModel]()
    var solvedDecisionModels          = Set[DecisionModel]()
    var solvedDecisionObjs            = mutable.Map[DecisionModel, Map[String, Double]]()
    var identifiedPath                = conf.dominantPath.getOrElse(os.pwd / "run" / "identified")
    var solvedPath                    = conf.solutionPath.getOrElse(os.pwd / "run" / "explored")
    var maximumSolutions              = conf.maximumSolutions
    var explorationTotalTimeOutInSecs = conf.explorationTotalTimeOutInSecs
    var timeResolution                = conf.timeResolution.getOrElse(0L)
    var memoryResolution              = conf.memoryResolution.getOrElse(0L)
    os.makeDir.all(identifiedPath)
    os.makeDir.all(solvedPath)
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
      } else if (command.startsWith("SET")) {
        val payload = command.substring(3).strip().split(" ")
        val option  = payload(0)
        val value   = payload(1)
        option.toLowerCase() match {
          case "maximum-solutions" | "max-sols" => maximumSolutions = value.toLong
          case "total-timeout"                  => explorationTotalTimeOutInSecs = value.toLong
          case "time-resolution" | "time-res"   => timeResolution = value.toLong
          case "memory-resolution" | "memory-res" | "mem-res" => memoryResolution = value.toLong
          case "identified-path" | "ident-path"             => identifiedPath = stringToPath(value)
          case "explored-path" | "solved-path" | "sol-path" => solvedPath = stringToPath(value)
          case _                                            =>
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
      } else if (command.startsWith("BID")) {
        val payload       = command.substring(3).strip()
        val modelHeader   = DecisionModelHeader.fromString(payload)
        val modelElements = modelHeader.covered_elements
        for (bpath <- modelHeader.body_path) {
          if (!urlsConsumed.contains(bpath)) {
            for (m <- decisionHeaderToModel(modelHeader)) { decisionModels += m }
          }
        }
        for (
          explorer <- explorers;
          modelWithAllElements = decisionModels.filter(_.category == modelHeader.category);
          model <- modelWithAllElements.filter(_.coveredElementIDs == modelElements);
          bid = explorer.combination(model)
        ) {
          sendOutputLine("RESULT " + bid.asText)
        }
        sendOutputLine("FINISHED")
        // val explorerOpt = explorers.find()
      } else if (command.startsWith("EXPLORE BEST")) {
        val payload       = command.substring(12).strip()
        val modelHeader   = DecisionModelHeader.fromString(payload)
        val modelElements = modelHeader.covered_elements
        for (bpath <- modelHeader.body_path) {
          if (!urlsConsumed.contains(bpath)) {
            for (m <- decisionHeaderToModel(modelHeader)) { decisionModels += m }
          }
        }
        for (
          model <- decisionModels
            .filter(_.category == modelHeader.category)
            .filter(_.coveredElementIDs == modelElements);
          ((solved, objs), idx) <- exploreBest(
            model,
            Set(),
            explorationTotalTimeOutInSecs,
            maximumSolutions,
            timeResolution,
            memoryResolution
          ).zipWithIndex
          // if !solvedDecisionModels.contains(solved)
        ) {
          val (hPath, bPath, header) =
            solved.writeToPath(solvedPath, f"$idx%016d", uniqueIdentifier)
          solvedDecisionModels += solved
          solvedDecisionObjs(solved) = objs
          sendOutputLine(f"RESULT ${write(objs)} ${header.asText}")
        }
        sendOutputLine("FINISHED")
      } else if (command.startsWith("EXPLORE")) {
        val payloadArray  = command.substring(7).strip().split(" ")
        val explorerName  = payloadArray(0)
        val modelHeader   = DecisionModelHeader.fromString(payloadArray(1))
        val modelElements = modelHeader.covered_elements
        for (bpath <- modelHeader.body_path) {
          if (!urlsConsumed.contains(bpath)) {
            for (m <- decisionHeaderToModel(modelHeader)) { decisionModels += m }
          }
        }
        for (
          explorer <- explorers.find(_.uniqueIdentifier == explorerName);
          modelWithAllElements = decisionModels.filter(_.category == modelHeader.category);
          model <- modelWithAllElements.filter(_.coveredElementIDs == modelElements);
          ((solved, objs), idx) <- explorer
            .explore(
              model,
              Set(),
              explorationTotalTimeOutInSecs,
              maximumSolutions,
              timeResolution,
              memoryResolution
            )
            .zipWithIndex
          // if !solvedDecisionModels.contains(solved)
        ) {
          val (hPath, bPath, header) =
            solved.writeToPath(solvedPath, f"$idx%016d", uniqueIdentifier)
          solvedDecisionModels += solved
          solvedDecisionObjs(solved) = objs
          sendOutputLine(f"RESULT ${write(objs)} ${header.asText}")
        }
        sendOutputLine("FINISHED")
      } else if (command.startsWith("STAT")) {
        sendOutputLine("DECISION " + decisionModels.toVector.map(_.category).mkString(", "))
        sendOutputLine("SOLVED " + solvedDecisionModels.toVector.map(_.category).mkString(", "))
      } else if (!command.startsWith("EXIT")) {
        logger.error("Passed invalid command: " + command)
      }
    }
  }

}
