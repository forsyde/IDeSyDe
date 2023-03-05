package idesyde

import idesyde.cli.IDeSyDeCLIParser
import idesyde.cli.IDeSyDeRunConfig
import scribe.Level
import forsyde.io.java.core.ForSyDeSystemGraph

import scala.concurrent.ExecutionContext
import scribe.format.FormatterInterpolator
import scribe.Level
import scribe.file.*

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.io.File
import scala.collection.mutable.Buffer
import idesyde.utils.Logger
import idesyde.cli.ScribeLogger
import scala.collection.mutable
import idesyde.identification.common.CommonIdentificationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.ForSyDeIdentificationModule
import idesyde.identification.minizinc.MinizincIdentificationModule
import idesyde.exploration.ChocoExplorationModule

object IDeSyDeStandalone {

  val additionalLogFiles: mutable.Buffer[File] = Buffer[File]()
  var loggingLevel: Level                      = Level.Info
  given Logger                                 = ScribeLogger

  def main(args: Array[String]): Unit =
    // System.exit(CommandLine(IDeSyDeCLI()).execute(args *))
    IDeSyDeCLIParser().parse(
      args,
      IDeSyDeRunConfig(
        Set(
          CommonIdentificationModule(),
          ChocoIdentificationModule(),
          ForSyDeIdentificationModule(),
          MinizincIdentificationModule()
        ),
        Set(ChocoExplorationModule())
      )
    ) match {
      case Some(runConfig) =>
        ScribeLogger.setLoggingLevel(loggingLevel, additionalLogFiles.toArray)
        ScribeLogger.info(s"logging levels set to ${loggingLevel.name}.")
        runConfig.run()
      case _ =>

    }

}
