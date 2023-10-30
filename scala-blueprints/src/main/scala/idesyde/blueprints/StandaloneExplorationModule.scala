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
import io.javalin.Javalin
import java.util.concurrent.TimeUnit
import idesyde.core.ExplorerConfiguration
import ujson.Value.InvalidData
import upickle.core.AbortException
import java.nio.channels.ClosedChannelException
import java.{util => ju}
import org.eclipse.jetty.io.EofException
import io.javalin.config.SizeUnit

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

  def decisionMessageToModel(m: DecisionModelMessage): Option[DecisionModel]

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
          // case ExplorationModuleConfiguration(_, _, _, _, _, _, _, _, _, _, Some("stdio")) =>
          //   serverStandaloneExplorationModule(conf, scala.io.StdIn.readLine, println)
          case ExplorationModuleConfiguration(_, _, _, _, _, _, _, _, _, _, Some("http")) =>
            standaloneHttpExplorationModule(0)
          //     case ExplorationModuleConfiguration(
          //           Some(dominantPath),
          //           Some(solutionPath),
          //           _,
          //           _,
          //           Some(decisionModelToExplore),
          //           timeResolution,
          //           memoryResolution,
          //           explorerIdxOpt,
          //           maximumSolutions,
          //           explorationTotalTimeOutInSecs,
          //           _
          //         ) =>
          //       os.makeDir.all(dominantPath)
          //       os.makeDir.all(solutionPath)
          //       val header = readBinary[DecisionModelHeader](os.read.bytes(decisionModelToExplore))
          //       decisionHeaderToModel(header) match {
          //         case Some(m) =>
          //           explorerIdxOpt match {
          //             case Some(idx) =>
          //               explore(
          //                 m,
          //                 idx,
          //                 Set(),
          //                 explorationTotalTimeOutInSecs,
          //                 maximumSolutions,
          //                 timeResolution.getOrElse(-1L),
          //                 memoryResolution.getOrElse(-1L)
          //               ).zipWithIndex.foreach((solvedWithObj, idx) => {
          //                 val (solved, _) = solvedWithObj
          //                 val (hPath, bPath, header) =
          //                   solved.writeToPath(solutionPath, f"$idx%016d", uniqueIdentifier)
          //                 println(hPath.get)
          //               })
          //             case None =>
          //               exploreBest(
          //                 m,
          //                 Set(),
          //                 explorationTotalTimeOutInSecs,
          //                 maximumSolutions,
          //                 timeResolution.getOrElse(-1L),
          //                 memoryResolution.getOrElse(-1L)
          //               ).zipWithIndex.foreach((solvedWithObj, idx) => {
          //                 val (solved, _) = solvedWithObj
          //                 val (hPath, bPath, header) =
          //                   solved.writeToPath(solutionPath, f"$idx%016d", uniqueIdentifier)
          //                 println(hPath.get)
          //               })
          //           }
          //         case None =>
          //       }
          //     case ExplorationModuleConfiguration(
          //           Some(dominantPath),
          //           _,
          //           _,
          //           Some(decisionModelToGetCriterias),
          //           _,
          //           _,
          //           _,
          //           _,
          //           _,
          //           _,
          //           _
          //         ) =>
          //       val header = readBinary[DecisionModelHeader](os.read.bytes(decisionModelToGetCriterias))
          //       decisionHeaderToModel(header) match {
          //         case Some(m) => for (comb <- combination(m)) println(comb.asText)
          //         case None    =>

          //       }
          case _ =>
        }
      case _ =>
    }
  }

  // inline def serverStandaloneExplorationModule(
  //     conf: ExplorationModuleConfiguration,
  //     inline fetchInputLine: () => String,
  //     inline sendOutputLine: (String) => Unit
  // ): Unit = {
  //   var command                       = ""
  //   var urlsConsumed                  = Set[String]()
  //   var decisionModels                = Set[DecisionModel]()
  //   var solvedDecisionModels          = Set[DecisionModel]()
  //   var solvedDecisionObjs            = mutable.Map[DecisionModel, Map[String, Double]]()
  //   var identifiedPath                = conf.dominantPath.getOrElse(os.pwd / "run" / "identified")
  //   var solvedPath                    = conf.solutionPath.getOrElse(os.pwd / "run" / "explored")
  //   var maximumSolutions              = conf.maximumSolutions
  //   var explorationTotalTimeOutInSecs = conf.explorationTotalTimeOutInSecs
  //   var timeResolution                = conf.timeResolution.getOrElse(0L)
  //   var memoryResolution              = conf.memoryResolution.getOrElse(0L)
  //   os.makeDir.all(identifiedPath)
  //   os.makeDir.all(solvedPath)
  //   // now the decision models
  //   for (path <- os.list(identifiedPath); if path.baseName.startsWith("header")) {
  //     if (path.ext == "msgpack") {
  //       for (m <- decisionHeaderToModel(readBinary[DecisionModelHeader](os.read.bytes(path))))
  //         decisionModels += m
  //     } else if (path.ext == "json") {
  //       for (m <- decisionHeaderToModel(read[DecisionModelHeader](os.read(path))))
  //         decisionModels += m
  //     }
  //     urlsConsumed += path.toString
  //   }
  //   // finally, for the solved ones
  //   for (path <- os.list(solvedPath); if path.baseName.startsWith("header")) {
  //     if (path.ext == "msgpack") {
  //       for (m <- decisionHeaderToModel(readBinary[DecisionModelHeader](os.read.bytes(path))))
  //         solvedDecisionModels += m
  //     } else if (path.ext == "json") {
  //       for (m <- decisionHeaderToModel(read[DecisionModelHeader](os.read(path))))
  //         solvedDecisionModels += m
  //     }
  //     urlsConsumed += path.toString
  //   }
  //   sendOutputLine("INITIALIZED")
  //   while (command != "EXIT") {
  //     command = fetchInputLine()
  //     // now branch, depending on the command passed
  //     if (command == null) { // likely recieved a SIGNIT, so we just shutdown
  //     } else if (command.startsWith("SET")) {
  //       val payload = command.substring(3).strip().split(" ")
  //       val option  = payload(0)
  //       val value   = payload(1)
  //       option.toLowerCase() match {
  //         case "maximum-solutions" | "max-sols" => maximumSolutions = value.toLong
  //         case "total-timeout"                  => explorationTotalTimeOutInSecs = value.toLong
  //         case "time-resolution" | "time-res"   => timeResolution = value.toLong
  //         case "memory-resolution" | "memory-res" | "mem-res" => memoryResolution = value.toLong
  //         case "identified-path" | "ident-path"             => identifiedPath = stringToPath(value)
  //         case "explored-path" | "solved-path" | "sol-path" => solvedPath = stringToPath(value)
  //         case _                                            =>
  //       }
  //     } else if (command.startsWith("DECISION INLINE")) {
  //       val payload = command.substring(15).strip()
  //       for (m <- decisionMessageToModel(DecisionModelMessage.fromJsonString(payload))) {
  //         decisionModels += m
  //       }
  //     } else if (command.startsWith("DECISION PATH")) {
  //       val url = command.substring(8).strip()
  //       if (!urlsConsumed.contains(url)) {
  //         for (
  //           decoded <- decodeFromPath[DecisionModelHeader](url);
  //           m       <- decisionHeaderToModel(decoded)
  //         ) {

  //           decisionModels += m
  //         }
  //         urlsConsumed += url
  //       }
  //     } else if (command.startsWith("SOLVED INLINE")) {
  //       val payload = command.substring(13).strip()
  //       for (m <- decisionMessageToModel(DecisionModelMessage.fromJsonString(payload)))
  //         solvedDecisionModels += m
  //     } else if (command.startsWith("SOLVED")) {
  //       val url  = command.substring(6).strip()
  //       val path = os.Path(url)
  //       if (!urlsConsumed.contains(url)) {
  //         if (path.ext == "msgpack") {
  //           for (
  //             decoded <- decodeFromPath[DecisionModelHeader](url);
  //             m       <- decisionHeaderToModel(decoded)
  //           )
  //             solvedDecisionModels += m
  //         } else if (path.ext == "json") {
  //           for (
  //             decoded <- decodeFromPath[DecisionModelHeader](url);
  //             m       <- decisionHeaderToModel(decoded)
  //           )
  //             solvedDecisionModels += m
  //         }
  //         urlsConsumed += url
  //       }
  //     } else if (command.startsWith("BID")) {
  //       val payload      = command.substring(3).strip()
  //       val modelMessage = DecisionModelMessage.fromJsonString(payload)
  //       for (
  //         explorer <- explorers;
  //         model    <- decisionMessageToModel(modelMessage);
  //         bid = explorer.bid(model)
  //       ) {
  //         sendOutputLine("RESULT " + bid.asText)
  //       }
  //       sendOutputLine("FINISHED")
  //     } else if (command.startsWith("EXPLORE BEST")) {
  //       val payload      = command.substring(12).strip()
  //       val modelMessage = DecisionModelMessage.fromJsonString(payload)
  //       for (
  //         model <- decisionMessageToModel(modelMessage);
  //         ((solved, objs), idx) <- exploreBest(
  //           model,
  //           Set(),
  //           explorationTotalTimeOutInSecs,
  //           maximumSolutions,
  //           timeResolution,
  //           memoryResolution
  //         ).zipWithIndex;
  //         message = ExplorationSolutionMessage.fromSolution(solved, objs)
  //         // if !solvedDecisionModels.contains(solved)
  //       ) {
  //         // val (hPath, bPath, header) =
  //         //   solved.writeToPath(solvedPath, f"$idx%016d", uniqueIdentifier)
  //         solvedDecisionModels += solved
  //         solvedDecisionObjs(solved) = objs
  //         sendOutputLine(f"RESULT ${message.withEscapedNewLinesText.asText}")
  //       }
  //       sendOutputLine("FINISHED")
  //     } else if (command.startsWith("EXPLORE")) {
  //       val payloadArray = command.substring(7).strip().split(" ")
  //       val explorerName = payloadArray(0)
  //       val modelMessage = DecisionModelMessage.fromJsonString(payloadArray(1))
  //       for (
  //         explorer <- explorers.find(_.uniqueIdentifier == explorerName);
  //         model    <- decisionMessageToModel(modelMessage);
  //         ((solved, objs), idx) <- explorer
  //           .explore(
  //             model,
  //             Set(),
  //             explorationTotalTimeOutInSecs,
  //             maximumSolutions,
  //             timeResolution,
  //             memoryResolution
  //           )
  //           .zipWithIndex;
  //         message = ExplorationSolutionMessage.fromSolution(solved, objs)
  //       ) {
  //         // val (hPath, bPath, header) =
  //         //   solved.writeToPath(solvedPath, f"$idx%016d", uniqueIdentifier)
  //         solvedDecisionModels += solved
  //         solvedDecisionObjs(solved) = objs
  //         sendOutputLine(
  //           f"RESULT ${message.withEscapedNewLinesText.asText}"
  //         )
  //       }
  //       sendOutputLine("FINISHED")
  //     } else if (command.startsWith("STAT")) {
  //       sendOutputLine("DECISION " + decisionModels.toVector.map(_.category).mkString(", "))
  //       sendOutputLine("SOLVED " + solvedDecisionModels.toVector.map(_.category).mkString(", "))
  //     } else if (!command.startsWith("EXIT")) {
  //       logger.error("Passed invalid command: " + command)
  //     }
  //   }
  // }

  inline def standaloneHttpExplorationModule(port: Int): Unit = {
    var decisionModels       = mutable.Set[DecisionModel]()
    var solvedDecisionModels = mutable.Set[DecisionModel]()
    var solvedDecisionObjs   = mutable.Map[DecisionModel, Map[String, Double]]()
    val server = Javalin
      .create()
      .post(
        "/set",
        ctx => {
          if (ctx.queryParamMap().containsKey("parameter")) {
            ctx.queryParam("parameter").toLowerCase() match {
              case _ =>
            }
          }
        }
      )
      .post(
        "/decision",
        ctx =>
          for (m <- decisionMessageToModel(DecisionModelMessage.fromJsonString(ctx.body()))) {
            decisionModels += m
          }
      )
      .post(
        "/solved",
        ctx =>
          val sol = ExplorationSolutionMessage.fromJsonString(ctx.body())
          for (m <- decisionMessageToModel(sol.solved)) {
            solvedDecisionModels += m
            solvedDecisionObjs(m) = sol.objectives
          }
      )
      .get(
        "/bid",
        ctx => {
          var bids = decisionMessageToModel(
            DecisionModelMessage
              .fromJsonString(ctx.body())
          )
            .map(decisionModel =>
              explorers
                .map(explorer => explorer.bid(decisionModel))
            )
            .getOrElse(List())
          ctx.result(write(bids));
        }
      )
      .get(
        "/explorers",
        ctx => {
          ctx.result(write(explorers.map(_.uniqueIdentifier)));
        }
      )
      .get(
        "/{explorerName}/bid",
        ctx => {
          explorers
            .find(e => e.uniqueIdentifier == ctx.pathParam("explorerName"))
            .foreach(explorer => {
              decisionMessageToModel(
                DecisionModelMessage
                  .fromJsonString(ctx.body())
              ).foreach(decisionModel => ctx.result(write(explorer.bid(decisionModel))))
            })
        }
      )
      .ws(
        "/{explorerName}/explore",
        ws => {
          var solutions           = mutable.Set[DecisionModel]()
          var solutionsObjectives = mutable.Map[DecisionModel, Map[String, Double]]()
          var configuration       = ExplorerConfiguration(0, 0, 0, 0, 0, 0, false, Set())
          ws.onMessage(ctx => {
            ctx.enableAutomaticPings(5, TimeUnit.SECONDS)
            try {
              val prevSol = ExplorationSolutionMessage.fromJsonString(ctx.message())
              for (solved <- decisionMessageToModel(prevSol.solved)) {
                solutions += solved
                solutionsObjectives(solved) = prevSol.objectives
              }
            } catch {
              case e: AbortException =>
              case e                 => e.printStackTrace()
            }
            try {
              configuration = ExplorerConfiguration.fromJsonString(ctx.message())
            } catch {
              case e: AbortException =>
              // case e: ju.NoSuchElementException =>
              case e => e.printStackTrace()
            }
            try {

              val request = DecisionModelMessage.fromJsonString(ctx.message())
              for (
                explorer <- explorers
                  .find(e => e.uniqueIdentifier == ctx.pathParam("explorerName"));
                decisionModel <- decisionMessageToModel(request);
                (sol, objs) <- explorer.explore(
                  decisionModel,
                  solutionsObjectives.toSet,
                  configuration
                )
              ) {
                if (
                  !configuration.strict || solutionsObjectives.values
                    .exists(prevSol => objs.forall((k, v) => v < prevSol(k)))
                ) {
                  val message = ExplorationSolutionMessage.fromSolution(sol, objs)
                  solutions += sol
                  solutionsObjectives(sol) = objs
                  ctx.send(message.asText)
                }
              }
              ctx.closeSession()
            } catch {
              case e: AbortException            =>
              case e: ju.NoSuchElementException =>
              case e: ClosedChannelException    =>
              case e: EofException              =>
              case e                            => e.printStackTrace()
            }
          })
        }
      )
      .exception(
        classOf[Exception],
        (e, ctx) => {
          e match {
            case _: ClosedChannelException => println("Client closed channel during execution.")
            case _                         => e.printStackTrace()
          }
        }
      )
      .updateConfig(config => {
        config.jetty.multipartConfig.maxTotalRequestSize(1, SizeUnit.GB)
        config.jetty.contextHandlerConfig(ctx => {
          ctx.setMaxFormContentSize(100000000)
        })
        config.http.maxRequestSize = 100000000
      })
    server.events(es => {
      es.serverStarted(() => {
        System.out.println("INITIALIZED " + server.port());
      });
    });
    server.start(0);
  }

}
