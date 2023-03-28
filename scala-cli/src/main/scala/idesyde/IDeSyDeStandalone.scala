package idesyde

import idesyde.cli.IDeSyDeCLIParser
import idesyde.cli.IDeSyDeRunConfig
import forsyde.io.java.core.ForSyDeSystemGraph

import scala.concurrent.ExecutionContext

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.io.File
import scala.collection.mutable.Buffer
import idesyde.utils.Logger
import scala.collection.mutable
import idesyde.identification.common.CommonIdentificationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.ForSyDeIdentificationModule
import idesyde.identification.minizinc.MinizincIdentificationModule
import idesyde.exploration.ChocoExplorationModule
import idesyde.utils.SimpleStandardIOLogger

object IDeSyDeStandalone {

  val additionalLogFiles: mutable.Buffer[File] = Buffer[File]()
  var loggingLevel                             = "INFO"
  val logger0: Logger                           = SimpleStandardIOLogger(loggingLevel)
  given Logger                                 = logger0

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
        val logger = logger0.setLoggingLevel(loggingLevel)
        given Logger                                 = logger
        logger.info(s"logging levels set to ${loggingLevel}.")
        runConfig.run()
      case _ =>

    }

}
