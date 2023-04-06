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
          .arg[String]("design-path")
          .text("The path where the design models (and headers) are stored.")
          .action((f, mc) =>
            mc.copy(designPath = if (f.startsWith("/")) then os.root / f else os.pwd / f)
          ),
        builder
          .arg[String]("decision-path")
          .text("The path where identified decision models (and headers) are stored.")
          .action((f, mc) =>
            mc.copy(decisionPath = if (f.startsWith("/")) then os.root / f else os.pwd / f)
          ),
        builder
          .arg[Long]("identification_step")
          .text("The overall identification iteration number.")
          .action((f, mc) => mc.copy(identificationStep = f)),
        builder.opt[Unit]("no-identification").action((b, mc) => mc.copy(shouldIdentify = false)),
        builder.opt[Unit]("no-integration").action((b, mc) => mc.copy(shouldIntegrate = false))
      ),
      args,
      IdentificationModuleConfiguration()
    )
  }
}
