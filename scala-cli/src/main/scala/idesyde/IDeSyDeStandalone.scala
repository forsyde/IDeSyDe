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
import scribe.file._
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.io.File
import scala.collection.mutable.Buffer

object IDeSyDeStandalone {

  val additionalLogFiles = Buffer[File]()
  var loggingLevel = Level.Info

  def main(args: Array[String]): Unit =
    // System.exit(CommandLine(IDeSyDeCLI()).execute(args *))
    IDeSyDeCLIParser().parse(
      args,
      IDeSyDeRunConfig(executionContext = ExecutionContext.global)
    ) match {
      case Some(runConfig) =>
        setLoggingLevel(loggingLevel, additionalLogFiles.toArray)
        runConfig
          .copy(
            debugLogger = (s) => scribe.debug(s),
            infoLogger = (s) => scribe.info(s),
            warnLogger = (s) => scribe.warn(s),
            errorLogger = (s) => scribe.error(s)
          )
          .run()
      case _ =>

    }

  def setLoggingLevel(loggingLevel: Level, additionalFiles: Array[File] = Array.empty) = {
    var builder = scribe.Logger.root
      .clearHandlers()
      .clearModifiers()
    if (loggingLevel == Level.Debug) {
      builder = builder
        .withHandler(
          minimumLevel = Some(loggingLevel),
          formatter =
            formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format
              .italic(scribe.format.classNameSimple)} - ${scribe.format.messages}"
        )
      for (outlet <- additionalFiles) {
        builder = builder.withHandler(
          minimumLevel = Some(loggingLevel),
          formatter =
            formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format
              .italic(scribe.format.classNameSimple)} - ${scribe.format.messages}",
          writer = FileWriter(outlet)
        )
      }
    } else {
      builder = builder
        .withHandler(
          minimumLevel = Some(loggingLevel),
          formatter = formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.messages}"
        )
      for (outlet <- additionalFiles) {
        builder = builder.withHandler(
          minimumLevel = Some(loggingLevel),
          formatter = formatter"${scribe.format.dateFull} [${scribe.format.levelColoredPaddedRight}] ${scribe.format.messages}",
          writer = FileWriter(outlet)
        )
      }
    }
    builder.replace()
    scribe.info(s"logging levels set to ${loggingLevel.name}.")
  }

}
