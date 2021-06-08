import picocli.CommandLine
import cli.IDeSyDeCLI

object Main {
  @main
  def executeCLI(args: String*): Unit = System.exit(CommandLine(IDeSyDeCLI()).execute(args*))
}