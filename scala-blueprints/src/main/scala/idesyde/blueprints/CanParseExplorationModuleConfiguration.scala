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
          .arg[String]("run_path")
          .action((f, mc) =>
            mc.copy(runPath = if (f.startsWith("/")) then os.root / f else os.pwd / f)
          ),
        builder.opt[Unit]("no-exploration").action((f, mc) => mc.copy(shoulExplore = false)),
        builder
          .opt[Long]("total-timeout")
          .action((f, mc) => mc.copy(explorationTotalTimeOutInSecs = f))
      ),
      args,
      ExplorationModuleConfiguration()
    )
  }
}
