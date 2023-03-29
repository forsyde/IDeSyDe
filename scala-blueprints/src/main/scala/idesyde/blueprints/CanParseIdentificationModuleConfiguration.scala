package idesyde.blueprints
import idesyde.blueprints.IdentificationModuleConfiguration

trait CanParseIdentificationModuleConfiguration {
  def parse(args: Array[String], uniqueIdentifier: String = ""): Option[IdentificationModuleConfiguration] = {
    val builder = scopt.OParser.builder[IdentificationModuleConfiguration]
    scopt.OParser.parse(
      scopt.OParser.sequence(
        builder.head("Scala-based Identification module " + uniqueIdentifier),
        builder
          .arg[String]("run_path")
          .action((f, mc) =>
            mc.copy(runPath = if (f.startsWith("/")) then os.root / f else os.pwd / f)
          ),
        builder.opt[Boolean]("identify").action((b, mc) => mc.copy(shouldIdentify = b)),
        builder.opt[Boolean]("integrate").action((b, mc) => mc.copy(shouldIntegrate = b))
      ),
      args,
      IdentificationModuleConfiguration()
    )
  }
}
