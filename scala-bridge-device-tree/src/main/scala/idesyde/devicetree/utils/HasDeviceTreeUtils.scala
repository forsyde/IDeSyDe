package idesyde.devicetree.utils

import idesyde.core.DecisionModel
import idesyde.core.DesignModel
import idesyde.devicetree.identification.DeviceTreeDesignModel
import idesyde.devicetree.identification.OSDescriptionDesignModel
import idesyde.devicetree.identification.CanParseDeviceTree
import idesyde.core.OpaqueDesignModel
import idesyde.devicetree.OSDescription

import org.virtuslab.yaml.*

trait HasDeviceTreeUtils extends CanParseDeviceTree {
  inline def toDeviceTreeDesignModel[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (DeviceTreeDesignModel) => (Set[M], Set[String])
  ): (Set[M], Set[String]) = {
    var messages = scala.collection.mutable.Set[String]()
    val allRoots = models
      .flatMap(_ match {
        case dt: DeviceTreeDesignModel => dt.roots
        case m: OpaqueDesignModel =>
          if (m.format() == "dts") {
            parseDeviceTree(m.body()) match {
              case Success(result, next) => Some(result)
              case Failure(msg, next) => {
                messages += msg
                None
              }
              case Error(msg, next) => {
                messages += msg
                None
              }
            }
          } else None
        case _ => None
      })
    val merged = DeviceTreeDesignModel(allRoots.toList)
    val (ms, msgs) = body(merged)
    (ms, msgs ++ messages.toSet)
  }

  inline def toOSDescriptionDesignModel[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (OSDescriptionDesignModel) => (Set[M], Set[String])
  ): (Set[M], Set[String]) = {
    var messages = scala.collection.mutable.Set[String]()
    val allOSes = models
      .flatMap(designModel => designModel match {
        case osDesc: OSDescriptionDesignModel => Some(osDesc.description)
        case m: OpaqueDesignModel =>
          if (m.format() == "yaml") {
            m.body().as[OSDescription] match {
              case Left(value) => {
                messages += "Failed to parse OSDescriptionDesignModel: " + value.msg
                None
              }
              case Right(value) => Some(value)
            }
          } else None
        case _ => None
      })
    val merged = allOSes.reduceOption((a, b) => a.mergeLeft(b))
    merged.map(desc => body(OSDescriptionDesignModel(desc))) match {
      case None => (Set(), messages.toSet)
      case Some((ms, msgs)) => (ms, msgs ++ messages.toSet)
    }
  }
  
}
