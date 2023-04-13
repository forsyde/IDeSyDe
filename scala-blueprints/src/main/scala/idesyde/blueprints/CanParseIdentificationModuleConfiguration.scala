package idesyde.blueprints
import idesyde.blueprints.IdentificationModuleConfiguration

trait CanParseIdentificationModuleConfiguration {
  def parse(
      args: Array[String],
      uniqueIdentifier: String = ""
  ): Either[String, IdentificationModuleConfiguration] = {
    val builder = scopt.OParser.builder[IdentificationModuleConfiguration]
    val seq = scopt.OParser.sequence(
      builder.head("Scala-based Identification module " + uniqueIdentifier),
      builder
        .opt[String]('m', "design-path")
        .text("The path where the design models (and headers) are stored.")
        .action((f, mc) =>
          mc.copy(designPath = Some(if (f.startsWith("/")) then os.root / os.RelPath(f) else os.pwd / os.RelPath(f)))
        ),
      builder
        .opt[String]('i', "identified-path")
        .text("The path where identified decision models (and headers) are stored.")
        .action((f, mc) =>
          mc.copy(identifiedPath = Some(if (f.startsWith("/")) then os.root / os.RelPath(f) else os.pwd / os.RelPath(f)))
        ),
      builder
        .opt[String]('s', "solved-path")
        .text("The path where explored decision models (and headers) are stored.")
        .action((f, mc) =>
          mc.copy(solvedPath = Some(if (f.startsWith("/")) then os.root / os.RelPath(f) else os.pwd / os.RelPath(f)))
        ),
      builder
        .opt[String]('r', "integration-path")
        .text("The path where integrated design models (and headers) are stored.")
        .action((f, mc) =>
          mc.copy(integrationPath = Some(if (f.startsWith("/")) then os.root / os.RelPath(f) else os.pwd / os.RelPath(f)))
        ),
      builder
        .opt[String]('o', "output-path")
        .text("The path where final integrated design models are stored, in their original format.")
        .action((f, mc) =>
          mc.copy(outputPath = Some(if (f.startsWith("/")) then os.root / os.RelPath(f) else os.pwd / os.RelPath(f)))
        ),
      builder
        .opt[Long]('t', "identification_step")
        .text("The overall identification iteration number.")
        .action((f, mc) => mc.copy(identificationStep = f)),
      builder.note(
        """The module needs to be invoked with at least the design and identified paths. 
        If the module is invoked with the integration path as well, 
        it moves to the integration stage instead of the identification one."""
      )
    )
    scopt.OParser.parse(
      seq,
      args,
      IdentificationModuleConfiguration()
    ) match {
      case Some(value) => Right(value)
      case None        => Left(scopt.OParser.usage(seq))
    }
  }
}
