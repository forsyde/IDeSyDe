import picocli.CommandLine
import idesyde.cli.IDeSyDeCLI

object Main {
  @main
  def executeCLI(args: String*): Unit =
    System.exit(CommandLine(IDeSyDeCLI()).execute(args *))
}