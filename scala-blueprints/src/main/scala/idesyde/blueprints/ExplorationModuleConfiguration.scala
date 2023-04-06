package idesyde.blueprints

final case class ExplorationModuleConfiguration(
    val dominantPath: os.Path = os.pwd / "run" / "dominant",
    val solutionPath: os.Path = os.pwd / "run" / "explored",
    val explorationTotalTimeOutInSecs: Long = 0L,
    val maximumSolutions: Long = 0L,
    val decisionModelToGetCriterias: Option[os.Path] = None,
    val decisionModelToGetCombination: Option[os.Path] = None,
    val decisionModelToExplore: Option[os.Path] = None
)
