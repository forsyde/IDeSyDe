package idesyde.blueprints

import upickle.default._

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger
import idesyde.utils.SimpleStandardIOLogger
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import idesyde.core.MarkedIdentificationRule
import idesyde.core.CompleteDecisionModel
import idesyde.identification.IdentificationModule

trait StandaloneIdentificationModule extends CanParseModuleConfiguration with IdentificationModule {

  def designModelDecoders: Set[(DesignModelHeader) => Option[DesignModel]]

  def decisionModelDecoders: Set[(DecisionModelHeader) => Option[DecisionModel]]

  def uniqueIdentifier: String

  def logger: Logger = SimpleStandardIOLogger("WARN")

  inline def standaloneIdentificationModule(
      args: Array[String]
  ): Unit = {
    parse(args, uniqueIdentifier) match {
      case Some(value) =>
        val runPath                   = value.runPath
        val designModelsPath          = runPath / "inputs" / "msgpack"
        val decisionModelsPathMsgPack = runPath / "identified" / "msgpack"
        val decisionModelsPathJson    = runPath / "identified" / "json"
        if (value.shouldIdentify) {
          val designModelHeaders =
            os.list(designModelsPath)
              .filter(_.last.startsWith("header"))
              .map(f => readBinary[DesignModelHeader](os.read(f)))
          val decisionModelHeaders =
            os.list(decisionModelsPathMsgPack)
              .filter(_.last.startsWith("header"))
              .map(f => readBinary[DecisionModelHeader](os.read(f)))
          val designModels =
            designModelDecoders.flatMap(dec => designModelHeaders.flatMap(h => dec(h)))
          val decisionModels =
            decisionModelDecoders.flatMap(dec => decisionModelHeaders.flatMap(h => dec(h)))
          val iterRules = if (value.iteration == 0) {
            identificationRules.flatMap(_ match {
              case r: MarkedIdentificationRule.DecisionModelOnlyIdentificationRule         => None
              case r: MarkedIdentificationRule.SpecificDecisionModelOnlyIdentificationRule => None
              case r => Some(r)
            })
          } else if (value.iteration > 0) {
            identificationRules.flatMap(_ match {
              case r: MarkedIdentificationRule.DesignModelOnlyIdentificationRule => None
              case r                                                             => Some(r)
            })
          } else identificationRules
          val identified = identificationRules.flatMap(irule => irule(designModels, decisionModels))
          val numCur     = decisionModelHeaders.size
          for ((m, i) <- identified.zipWithIndex; h = m.header) {
            os.write.over(
              decisionModelsPathJson / s"header_${numCur}_${uniqueIdentifier}_${m.uniqueIdentifier}.json",
              h.asText
            )
            os.write.over(
              decisionModelsPathMsgPack / s"header_${numCur}_${uniqueIdentifier}_${m.uniqueIdentifier}.msgpack",
              h.asBinary
            )
            m match {
              case cm: CompleteDecisionModel =>
                os.write.over(
                  decisionModelsPathJson / s"body_${numCur}_${uniqueIdentifier}_${m.uniqueIdentifier}.json",
                  cm.bodyAsText
                )
                os.write.over(
                  decisionModelsPathMsgPack / s"body_${numCur}_${uniqueIdentifier}_${m.uniqueIdentifier}.msgpack",
                  cm.bodyAsBinary
                )
              case _ =>
            }
          }
        }
      case None =>
    }
  }
}
