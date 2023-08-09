package idesyde.blueprints

trait CanParseExplorationModuleConfiguration extends ModuleUtils {
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
                stringToPath(f)
              )
            )
          ),
        builder
          .opt[String]('o', "solution-path")
          .action((f, mc) =>
            mc.copy(solutionPath =
              Some(
                stringToPath(f)
              )
            )
          ),
        builder
          .opt[String]('e', "explore")
          .action((f, mc) =>
            mc.copy(decisionModelToExplore =
              Some(
                stringToPath(f)
              )
            )
          ),
        builder
          .opt[String]('c', "combine")
          .action((f, mc) =>
            mc.copy(decisionModelToGetCombination =
              Some(
                stringToPath(f)
              )
            )
          ),
        builder
          .opt[String]('n', "explorer-id")
          .action((f, mc) => mc.copy(explorerId = Some(f))),
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
          .action((f, mc) => mc.copy(memoryResolution = Some(f))),
        builder
          .opt[String]("server")
          .text(
            "Run this module continously in server mode with STDIO or a TCP port as the interface."
          )
          .action((f, mc) => mc.copy(serverMode = Some(f)))
      ),
      args,
      ExplorationModuleConfiguration()
    )
  }
}
