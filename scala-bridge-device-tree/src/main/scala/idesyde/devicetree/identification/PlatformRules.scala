package idesyde.devicetree.identification

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.common.models.platform.SharedMemoryMultiCore
import idesyde.devicetree.utils.HasDeviceTreeUtils
import scala.collection.mutable.Buffer
import spire.math.Rational
import scala.collection.mutable

trait PlatformRules extends HasDeviceTreeUtils {

  def identSharedMemoryMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[SharedMemoryMultiCore] = toDeviceTreeDesignModel(models) { dtm =>
    val roots                 = dtm.crossLinked
    var peIDs                 = Buffer[String]()
    var peOps                 = Buffer[Map[String, Map[String, Rational]]]()
    var peFreq                = Buffer[Long]()
    var ceIDs                 = Buffer[String]()
    var meIDs                 = Buffer[String]()
    var meSizes               = Buffer[Long]()
    var topo                  = mutable.Set[(String, String)]()
    var ceMaxChannels         = Buffer[Int]()
    var ceBitPerSecPerChannel = Buffer[Rational]()
    var preComputedPaths      = mutable.Map[String, mutable.Map[String, Iterable[String]]]()
    for (island <- roots) {
      island match {
        case root @ RootNode(children, properties, prefix) =>
          val mainBus = root.prefix + "/devicebus"
          val pes = root.cpus.map(cpu => cpu.label.getOrElse(root.prefix + "/cpus/" + cpu.nodeName))
          val mems =
            root.memories.map(mem => mem.label.getOrElse(root.prefix + "/memory/" + mem.nodeName))
          topo ++= pes.map(pe => (mainBus, pe)) ++ pes.map(pe => (pe, mainBus)) ++
            mems.map(mem => (mainBus, mem)) ++ mems.map(mem => (mem, mainBus))
          peIDs ++= pes
          peOps ++= root.cpus.map(_.operationsProvided)
          peFreq ++= root.cpus.map(_.frequency)
          meIDs ++= mems
          meSizes ++= root.memories.map(_.memorySize)
          ceIDs += mainBus
          ceMaxChannels += 1
          ceBitPerSecPerChannel += root.cpus.map(cpu => Rational(cpu.frequency)).max
        case _ =>
      }
    }
    val (topoSrcs, topoDsts) = topo.toVector.unzip
    Set(
      SharedMemoryMultiCore(
        peIDs.toVector,
        meIDs.toVector,
        ceIDs.toVector,
        topoSrcs,
        topoDsts,
        peFreq.toVector,
        peOps.toVector,
        meSizes.toVector,
        ceMaxChannels.toVector,
        ceBitPerSecPerChannel.toVector,
        preComputedPaths.map((s, m) => s -> m.toMap).toMap
      )
    )
  }
}
