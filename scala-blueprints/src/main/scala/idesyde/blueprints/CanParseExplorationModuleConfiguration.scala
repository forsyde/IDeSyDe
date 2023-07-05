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
          .opt[String]('i', "dominant-path")
          .action((f, mc) =>
            mc.copy(dominantPath =
              Some(
                if (f.startsWith("/")) then os.Path(f)
                else os.pwd / os.RelPath(f)
              )
            )
          ),
        builder
          .opt[String]('o', "solution-path")
          .action((f, mc) =>
            mc.copy(solutionPath =
              Some(
                if (f.startsWith("/")) then os.Path(f)
                else os.pwd / os.RelPath(f)
              )
            )
          ),
        builder
          .opt[String]('e', "explore")
          .action((f, mc) =>
            mc.copy(decisionModelToExplore =
              Some(
                if (f.startsWith("/")) then os.Path(f)
                else os.pwd / os.RelPath(f)
              )
            )
          ),
        builder
          .opt[String]('c', "combine")
          .action((f, mc) =>
            mc.copy(decisionModelToGetCombination =
              Some(
                if (f.startsWith("/")) then os.Path(f)
                else os.pwd / os.RelPath(f)
              )
            )
          ),
        builder
          .opt[Int]('n', "explorer-idx")
          .action((f, mc) => mc.copy(explorerIdx = Some(f))),
        builder
          .opt[Long]("total-timeout")
          .action((f, mc) => mc.copy(explorationTotalTimeOutInSecs = f)),
        builder
          .opt[Long]("maximum-solutions")
          .action((f, mc) => mc.copy(maximumSolutions = f)),
        builder
          .opt[Long]("time-resolution")
          .action((f, mc) => mc.copy(timeResolution = Some(f))),
        builder
          .opt[Long]("memory-resolution")
          .action((f, mc) => mc.copy(memoryResolution = Some(f)))
      ),
      args,
      ExplorationModuleConfiguration()
    )
  }
}
