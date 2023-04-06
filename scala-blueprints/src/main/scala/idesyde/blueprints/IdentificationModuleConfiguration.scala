package idesyde.blueprints

final case class IdentificationModuleConfiguration(
    val decisionPath: os.Path = os.pwd / "run" / "identified",
    val designPath: os.Path = os.pwd / "run" / "inputs",
    val shouldIdentify: Boolean = true,
    val shouldIntegrate: Boolean = true,
    val identificationStep: Long = 0
)
