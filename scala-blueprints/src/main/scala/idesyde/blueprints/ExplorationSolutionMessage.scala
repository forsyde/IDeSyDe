package idesyde.blueprints

import upickle.default._
import idesyde.core.DecisionModel

final case class ExplorationSolutionMessage(
    val objectives: Map[String, Double],
    val solved: DecisionModelMessage
) derives ReadWriter {

  def asText: String = write(this)

  def withEscapedNewLinesText: ExplorationSolutionMessage =
    copy(solved = solved.withEscapedNewLinesText)

  def withUnescapedNewLinesText: ExplorationSolutionMessage =
    copy(solved = solved.withUnescapedNewLinesText)

}

object ExplorationSolutionMessage {
  def fromSolution(m: DecisionModel, objs: Map[String, Double]): ExplorationSolutionMessage =
    ExplorationSolutionMessage(
      objectives = objs,
      solved = DecisionModelMessage.fromDecisionModel(m)
    )

  def fromSolution(sol: (DecisionModel, Map[String, Double])): ExplorationSolutionMessage =
    ExplorationSolutionMessage(
      objectives = sol._2,
      solved = DecisionModelMessage.fromDecisionModel(sol._1)
    )

  def fromJsonString(s: String): ExplorationSolutionMessage =
    read(s)

}
