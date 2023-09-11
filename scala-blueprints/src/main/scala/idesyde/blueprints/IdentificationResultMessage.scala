package idesyde.blueprints

import upickle.default._

final case class IdentificationResultMessage(
    val identified: Set[DecisionModelMessage],
    val errors: Set[String]
) derives ReadWriter
