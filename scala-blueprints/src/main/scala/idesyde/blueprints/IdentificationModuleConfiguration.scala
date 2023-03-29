package idesyde.blueprints

final case class IdentificationModuleConfiguration(
    val runPath: os.Path = os.pwd / "run",
    val shouldIdentify: Boolean = true,
    val shouldIntegrate: Boolean = true
)
