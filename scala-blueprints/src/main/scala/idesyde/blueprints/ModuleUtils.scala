package idesyde.blueprints

import upickle.default._

import idesyde.core.DecisionModel
import idesyde.core.CompleteDecisionModel
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader

trait ModuleUtils {

  def decodeFromPath[T: ReadWriter](p: String): Seq[T] = {
    if (p.endsWith(".msgpack") && !p.startsWith("/")) Seq(readBinary[T](os.read.bytes(os.pwd / p)))
    else if (p.endsWith(".msgpack") && p.startsWith("/"))
      Seq(readBinary[T](os.read.bytes(os.root / p)))
    else if (p.endsWith(".json") && !p.startsWith("/")) Seq(read[T](os.read(os.pwd / p)))
    else if (p.endsWith(".json") && p.startsWith("/")) Seq(read[T](os.read(os.root / p)))
    else Seq()
  }

  extension (m: DesignModel)
    def writeToPath(
        p: os.Path,
        prefix: String,
        suffix: String
    ): (Option[os.Path], Option[os.Path]) = {
      m.header.writeToPath(p, prefix, suffix)
    }

  extension (m: DesignModelHeader)
    def writeToPath(
        p: os.Path,
        prefix: String,
        suffix: String
    ): (Option[os.Path], Option[os.Path]) = {
      os.write.over(
        p / s"header_${prefix}_${m.category}_${suffix}.msgpack",
        m.asBinary
      )
      os.write.over(
        p / s"header_${prefix}_${m.category}_${suffix}.json",
        m.asText
      )
      (Some(p / s"header_${prefix}_${m.category}_${suffix}.msgpack"), None)
    }

  extension (m: DecisionModel)
    def writeToPath(
        p: os.Path,
        prefix: String,
        suffix: String
    ): (Option[os.Path], Option[os.Path]) = {
      val h = m match {
        case cm: CompleteDecisionModel =>
          os.write.over(
            p / s"body_${prefix}_${m.uniqueIdentifier}_${suffix}.json",
            cm.bodyAsText
          )
          os.write.over(
            p / s"body_${prefix}_${m.uniqueIdentifier}_${suffix}.msgpack",
            cm.bodyAsBinary
          )
          cm.header.copy(body_path =
            Seq(
              (p / s"body_${prefix}_${m.uniqueIdentifier}_${suffix}.msgpack").toString
            )
          )
        case _ =>
          m.header
      }
      os.write.over(
        p / s"header_${prefix}_${m.uniqueIdentifier}_${suffix}.json",
        h.asText
      )
      os.write.over(
        p / s"header_${prefix}_${m.uniqueIdentifier}_${suffix}.msgpack",
        h.asBinary
      )
      (
        Some(p / s"header_${prefix}_${m.uniqueIdentifier}_${suffix}.msgpack"),
        Some(p / s"body_${prefix}_${m.uniqueIdentifier}_${suffix}.msgpack")
      )
    }
}
