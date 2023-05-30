package idesyde.blueprints

final case class IdentificationModuleConfiguration(
    val designPath: Option[os.Path] = None,
    val identifiedPath: Option[os.Path] = None,
    val solvedPath: Option[os.Path] = None,
    val integrationPath: Option[os.Path] = None,
    val outputPath: Option[os.Path] = None,
    val identificationStep: Long = 0,
    val printSchemas: Boolean = false
)
