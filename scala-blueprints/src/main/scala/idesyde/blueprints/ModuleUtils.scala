package idesyde.blueprints

import upickle.default._

import idesyde.core.DecisionModel
import idesyde.core.CompleteDecisionModel
import idesyde.core.DesignModel
import idesyde.core.headers.DesignModelHeader
import idesyde.core.headers.DecisionModelHeader
import os.PathError

trait ModuleUtils {

  def decodeFromPath[T: ReadWriter](p: String): Option[T] = {
    var decoded: Option[T] = None
    if (p.endsWith(".msgpack"))
      decoded = Some(readBinary[T](os.read.bytes(stringToPath(p))))
    else if (p.endsWith(".json"))
      decoded = Some(read[T](os.read(stringToPath(p))))
    decoded
    // if (p.endsWith(".msgpack") && !p.startsWith("/"))
    //   Some(readBinary[T](os.read.bytes(os.pwd / os.RelPath(p))))
    // else if (p.endsWith(".msgpack") && p.startsWith("/"))
    //   Some(readBinary[T](os.read.bytes(os.root / os.RelPath(p.substring(1)))))
    // else if (p.endsWith(".json") && !p.startsWith("/"))
    //   Some(read[T](os.read(os.pwd / os.RelPath(p))))
    // else if (p.endsWith(".json") && p.startsWith("/"))
    //   Some(read[T](os.read(os.root / os.RelPath(p.substring(1)))))
    // else None
  }

  def stringToPath(s: String): os.Path = {
    try {
      val p = os.RelPath(s)
      p.resolveFrom(os.pwd)
    } catch {
      case err: IllegalArgumentException =>
        os.Path(s)
      case err: PathError.NoRelativePath =>
        os.Path(s)
      case err =>
        throw err
    }
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
            p / s"body_${prefix}_${m.category}_${suffix}.json",
            cm.bodyAsText
          )
          os.write.over(
            p / s"body_${prefix}_${m.category}_${suffix}.msgpack",
            cm.bodyAsBinary
          )
          cm.header.copy(body_path =
            Some(
              (p / s"body_${prefix}_${m.category}_${suffix}.msgpack").toString
            )
          )
        case _ =>
          m.header
      }
      os.write.over(
        p / s"header_${prefix}_${m.category}_${suffix}.json",
        h.asText
      )
      os.write.over(
        p / s"header_${prefix}_${m.category}_${suffix}.msgpack",
        h.asBinary
      )
      (
        Some(p / s"header_${prefix}_${m.category}_${suffix}.msgpack"),
        Some(p / s"body_${prefix}_${m.category}_${suffix}.msgpack")
      )
    }
}
