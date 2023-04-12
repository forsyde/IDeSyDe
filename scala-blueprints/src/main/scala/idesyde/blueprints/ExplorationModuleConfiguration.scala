package idesyde.blueprints

final case class ExplorationModuleConfiguration(
    val dominantPath: Option[os.Path] = None,
    val solutionPath: Option[os.Path] = None,
    val decisionModelToGetCriterias: Option[os.Path] = None,
    val decisionModelToGetCombination: Option[os.Path] = None,
    val decisionModelToExplore: Option[os.Path] = None,
    val timeResolution: Option[Long] = None,
    val memoryResolution: Option[Long] = None,
    val maximumSolutions: Long = 0L,
    val explorationTotalTimeOutInSecs: Long = 0L
)
