package idesyde

import idesyde.cli.IDeSyDeCLIParser
import idesyde.cli.IDeSyDeRunConfig
import scribe.Level
import idesyde.identification.IdentificationHandler
import idesyde.exploration.ExplorationHandler
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import scribe.format.FormatterInterpolator
import scribe.Level

object IDeSyDeStandalone {

  def main(args: Array[String]): Unit =
    // System.exit(CommandLine(IDeSyDeCLI()).execute(args *))
    IDeSyDeCLIParser().parse(
      args,
      IDeSyDeRunConfig(executionContext = ExecutionContext.global)
    ) match {
      case Some(runConfig) =>
        setLoggingLevel(Level.get(runConfig.verbosityLevel).getOrElse(Level.Info))
        runConfig.copy(
          debugLogger = (s) => scribe.debug(s),
          infoLogger = (s) => scribe.info(s),
          warnLogger = (s) => scribe.warn(s),
          errorLogger = (s) => scribe.error(s)
        ).run()
      case _ =>

    }

  def setLoggingLevel(loggingLevel: Level) =
    if (loggingLevel == Level.Debug)
      scribe.Logger.root
        .clearHandlers()
        .clearModifiers()
        .withHandler(
          minimumLevel = Some(loggingLevel),
          formatter =
            formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format
              .italic(scribe.format.classNameSimple)} - ${scribe.format.messages}"
        )
        .replace()
    else
      scribe.Logger.root
        .clearHandlers()
        .clearModifiers()
        .withHandler(
          minimumLevel = Some(loggingLevel),
          formatter =
            formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.messages}"
        )
        .replace()
    scribe.info(s"logging levels set to ${loggingLevel.name}.")

}
