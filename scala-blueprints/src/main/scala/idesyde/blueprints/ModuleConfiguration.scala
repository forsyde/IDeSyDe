package idesyde.blueprints

final case class ModuleConfiguration(
    val runPath: os.Path = os.pwd / "run",
    val shouldIdentify: Boolean = true,
    val shouldIntegrate: Boolean = true,
    val iteration: Int = -1
)
