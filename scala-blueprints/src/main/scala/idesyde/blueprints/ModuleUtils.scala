package idesyde.blueprints

import upickle.default._

import idesyde.core.DecisionModel
import idesyde.core.CompleteDecisionModel
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader

trait ModuleUtils {

  def decodeFromPath[T: ReadWriter](p: String): Option[T] = {
    if (p.endsWith(".msgpack")) Some(readBinary[T](os.read.bytes(os.pwd / p)))
    else if (p.endsWith(".json")) Some(read[T](os.read(os.pwd / p)))
    else None
  }

  extension (m: DesignModel)
    def writeToPath(p: os.Path, prefix: String, suffix: String): Unit = {
      os.write.over(
        p / s"header_${prefix}_${m.uniqueIdentifier}_${suffix}.msgpack",
        m.header
          .copy(model_paths =
            Set((p / s"header_${prefix}_${m.uniqueIdentifier}_${suffix}.msgpack").toString)
          )
          .asBinary
      )
      os.write.over(
        p / s"header_${prefix}_${m.uniqueIdentifier}_${suffix}.json",
        m.header
          .copy(model_paths =
            Set((p / s"header_${prefix}_${m.uniqueIdentifier}_${suffix}.json").toString)
          )
          .asText
      )
    }

  extension (m: DesignModelHeader)
    def writeToPath(p: os.Path, prefix: String, suffix: String): Unit = {
      os.write.over(
        p / s"header_${prefix}_${m.category}_${suffix}.msgpack",
        m.asBinary
      )
      os.write.over(
        p / s"header_${prefix}_${m.category}_${suffix}.json",
        m.asText
      )
    }

  extension (m: DecisionModel)
    def writeToPath(p: os.Path, prefix: String, suffix: String): Unit = {
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
            Some(
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
    }
}
