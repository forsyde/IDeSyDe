package idesyde.blueprints

final case class ExplorationModuleConfiguration(
    val runPath: os.Path = os.pwd / "run",
    val shoulExplore: Boolean = true,
    val explorationTotalTimeOutInSecs: Long = 0L
)
