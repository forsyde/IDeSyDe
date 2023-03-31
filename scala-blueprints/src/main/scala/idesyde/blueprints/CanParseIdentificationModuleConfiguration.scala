package idesyde.blueprints
import idesyde.blueprints.IdentificationModuleConfiguration

trait CanParseIdentificationModuleConfiguration {
  def parse(
      args: Array[String],
      uniqueIdentifier: String = ""
  ): Option[IdentificationModuleConfiguration] = {
    val builder = scopt.OParser.builder[IdentificationModuleConfiguration]
    scopt.OParser.parse(
      scopt.OParser.sequence(
        builder.head("Scala-based Identification module " + uniqueIdentifier),
        builder
          .arg[String]("run_path")
          .action((f, mc) =>
            mc.copy(runPath = if (f.startsWith("/")) then os.root / f else os.pwd / f)
          ),
        builder
          .arg[Long]("identification_step")
          .action((f, mc) => mc.copy(identificationStep = f)),
        builder.opt[Unit]("--no-identification").action((b, mc) => mc.copy(shouldIdentify = false)),
        builder.opt[Unit]("--no-integration").action((b, mc) => mc.copy(shouldIntegrate = false))
      ),
      args,
      IdentificationModuleConfiguration()
    )
  }
}
