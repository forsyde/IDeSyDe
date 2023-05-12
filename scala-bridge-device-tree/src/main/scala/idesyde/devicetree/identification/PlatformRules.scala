package idesyde.devicetree.identification

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.common.SharedMemoryMultiCore
import idesyde.devicetree.utils.HasDeviceTreeUtils
import scala.collection.mutable.Buffer
import spire.math.Rational
import scala.collection.mutable
import idesyde.common.PartitionedCoresWithRuntimes
import idesyde.devicetree.RootNode
import idesyde.utils.HasUtils

trait PlatformRules extends HasDeviceTreeUtils with HasUtils {

  def identSharedMemoryMultiCore(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[SharedMemoryMultiCore] =
    mergedDesignModel[DeviceTreeDesignModel, SharedMemoryMultiCore](models) { dtm =>
      val roots                 = dtm.crossLinked
      var peIDs                 = Buffer[String]()
      var peOps                 = Buffer[Map[String, Map[String, Double]]]()
      var peFreq                = Buffer[Long]()
      var ceIDs                 = Buffer[String]()
      var meIDs                 = Buffer[String]()
      var meSizes               = Buffer[Long]()
      var topo                  = mutable.Set[(String, String)]()
      var ceMaxChannels         = Buffer[Int]()
      var ceBitPerSecPerChannel = Buffer[Double]()
      var preComputedPaths      = mutable.Map[String, mutable.Map[String, Iterable[String]]]()
      for (island <- roots) {
        island match {
          case root @ RootNode(children, properties, prefix) =>
            val mainBus = root.prefix + "/devicebus"
            val pes = root.cpus
              .map(cpu =>
                cpu.label.getOrElse(
                  root.prefix + "/cpus/" + cpu.fullId
                )
              )
              .filterNot(peIDs.contains)
            val mems =
              root.memories
                .map(mem =>
                  mem.label
                    .getOrElse(
                      root.prefix + "/" + mem.fullId
                    )
                )
                .filterNot(meIDs.contains)
            topo ++= pes.map(pe => (mainBus, pe)) ++ pes.map(pe => (pe, mainBus)) ++
              mems.map(mem => (mainBus, mem)) ++ mems.map(mem => (mem, mainBus))
            // now add additional connections of the main bus
            for (comp <- root.allChildren; idx = ceIDs.indexOf(comp); if idx > -1) {
              topo += (mainBus, comp.label.getOrElse(root.prefix + comp.nodeName))
              topo += (comp.label.getOrElse(root.prefix + comp.nodeName), mainBus)
            }
            // the found elements
            peIDs ++= pes
            meIDs ++= mems
            ceIDs += mainBus
            // now the properties for each important element
            peOps ++= root.cpus.map(_.operationsProvided)
            peFreq ++= root.cpus.map(_.frequency)
            meSizes ++= root.memories.map(_.memorySize)
            ceMaxChannels += root.mainBusConcurrency
            ceBitPerSecPerChannel +=
              root.mainBusFrequency.toDouble * root.mainBusFlitSize.toDouble / root.mainBusClockPerFlit.toDouble
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

  def identPartitionedCoresWithRuntimes(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[PartitionedCoresWithRuntimes] =
    mergedDesignModel[OSDescriptionDesignModel, PartitionedCoresWithRuntimes](models) { dm =>
      val isPartitioned = dm.description.oses.values.forall(_.affinity.size == 1)
      if (isPartitioned) {
        Set(
          PartitionedCoresWithRuntimes(
            processors = dm.description.oses.values.map(_.affinity.head).toVector,
            schedulers = dm.description.oses.keySet.toVector,
            isBareMetal =
              dm.description.oses.values.map(o => o.policy.exists(_ == "standalone")).toVector,
            isFixedPriority =
              dm.description.oses.values.map(o => o.policy.exists(_.contains("FP"))).toVector,
            isCyclicExecutive =
              dm.description.oses.values.map(o => o.policy.exists(_.contains("SCS"))).toVector
          )
        )
      } else Set()
    }

}
