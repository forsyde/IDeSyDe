package idesyde

import picocli.CommandLine
import idesyde.cli.IDeSyDeCLI
import idesyde.cli.IDeSyDeCLIParser
import idesyde.cli.IDeSyDeRunConfig
import scribe.Level
import idesyde.identification.api.Identification
import idesyde.exploration.api.Exploration
import forsyde.io.java.drivers.ForSyDeSystemGraphHandler
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext

object IDeSyDeStandalone {

  def main(args: Array[String]): Unit =
    // System.exit(CommandLine(IDeSyDeCLI()).execute(args *))
    IDeSyDeCLIParser().parse(args, IDeSyDeRunConfig(executionContext = ExecutionContext.global)) match {
      case Some(runConfig) =>
        runConfig.run()
      case _ =>
        println("problem encountered while parsing!!")
    }


}