package idesyde.blueprints

final case class ExplorationModuleConfiguration(
    val runPath: os.Path = os.pwd / "run",
    val explorationTotalTimeOutInSecs: Long = 0L,
    val maximumSolutions: Long = 0L,
    val decisionModelToGetCriterias: Option[os.Path] = None,
    val decisionModelToGetCombination: Option[os.Path] = None,
    val decisionModelToExplore: Option[os.Path] = None
)
