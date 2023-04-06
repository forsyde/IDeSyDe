package idesyde.blueprints

trait CanParseExplorationModuleConfiguration {
  def parse(
      args: Array[String],
      uniqueIdentifier: String = ""
  ): Option[ExplorationModuleConfiguration] = {
    val builder = scopt.OParser.builder[ExplorationModuleConfiguration]
    scopt.OParser.parse(
      scopt.OParser.sequence(
        builder.head("Scala-based exploration module " + uniqueIdentifier),
        builder
          .arg[String]("dominant-path")
          .action((f, mc) =>
            mc.copy(dominantPath = if (f.startsWith("/")) then os.root / f else os.pwd / f)
          ),
        builder
          .arg[String]("solution-path")
          .action((f, mc) =>
            mc.copy(solutionPath = if (f.startsWith("/")) then os.root / f else os.pwd / f)
          ),
        builder
          .opt[String]('e', "explore")
          .action((f, mc) =>
            mc.copy(decisionModelToExplore =
              Some(if (f.startsWith("/")) then os.root / f else os.pwd / f)
            )
          ),
        builder
          .opt[String]('a', "available-criteria")
          .action((f, mc) =>
            mc.copy(decisionModelToGetCriterias =
              Some(if (f.startsWith("/")) then os.root / f else os.pwd / f)
            )
          ),
        builder
          .opt[String]('c', "combine")
          .action((f, mc) =>
            mc.copy(decisionModelToGetCombination =
              Some(if (f.startsWith("/")) then os.root / f else os.pwd / f)
            )
          ),
        builder
          .opt[Long]("total-timeout")
          .action((f, mc) => mc.copy(explorationTotalTimeOutInSecs = f)),
        builder
          .opt[Long]("maximum-solutions")
          .action((f, mc) => mc.copy(maximumSolutions = f))
      ),
      args,
      ExplorationModuleConfiguration()
    )
  }
}
