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
      var peIDs                 = mutable.Set[String]()
      var peOps                 = Map[String, Map[String, Map[String, Double]]]()
      var peFreq                = Map[String, Long]()
      var ceIDs                 = mutable.Set[String]()
      var meIDs                 = mutable.Set[String]()
      var meSizes               = Map[String, Long]()
      var topo                  = mutable.Set[(String, String)]()
      var ceMaxChannels         = Map[String, Int]()
      var ceBitPerSecPerChannel = Map[String, Double]()
      var preComputedPaths      = mutable.Map[String, mutable.Map[String, Iterable[String]]]()
      for (island <- roots) {
        island match {
          case root @ RootNode(children, properties, prefix) =>
            val mainBus = root.prefix + "/devicebus"
            val pes = root.cpus
              .map(cpu => cpu.prefixedFullId(root.prefix + "/cpus"))
              .filterNot(peIDs.contains)
            val mems =
              root.memories
                .map(mem => mem.prefixedFullId(root.prefix))
                .filterNot(meIDs.contains)
            var othersCEs =
              root.extraBuses.map(ce => ce.prefixedFullId(root.prefix))
            // topo ++= pes.map(pe => (mainBus, pe)) ++ pes.map(pe => (pe, mainBus)) ++
            //   mems.map(mem => (mainBus, mem)) ++ mems.map(mem => (mem, mainBus))
            // now add additional connections of the main bus
            for (comp <- pes ++ mems ++ othersCEs) {
              topo += (mainBus, comp)
              topo += (comp, mainBus)
            }
            // the found elements
            peIDs ++= pes
            meIDs ++= mems
            ceIDs += mainBus
            ceIDs ++= othersCEs
            // now the properties for each important element
            peOps ++= root.cpus.map(pe =>
              pe.prefixedFullId(root.prefix + "/cpus") -> pe.operationsProvided
            )
            peFreq ++= root.cpus.map(pe => pe.prefixedFullId(root.prefix + "/cpus") -> pe.frequency)
            meSizes ++= root.memories.map(me => me.prefixedFullId(root.prefix) -> me.memorySize)
            ceMaxChannels += mainBus -> root.mainBusConcurrency
            ceMaxChannels ++= root.extraBuses.map(ce =>
              ce.prefixedFullId(root.prefix) -> ce.busConcurrency
            )
            ceBitPerSecPerChannel += mainBus ->
              root.mainBusFrequency.toDouble * root.mainBusFlitSize.toDouble / root.mainBusClockPerFlit.toDouble
            ceBitPerSecPerChannel ++=
              root.extraBuses.map(bus =>
                bus.prefixedFullId(root.prefix) ->
                  bus.busFrequency.toDouble * bus.busFlitSize.toDouble / bus.busClockPerFlit.toDouble
              )
        }
      }
      val (topoSrcs, topoDsts) = topo.toVector.unzip
      val peVec                = peIDs.toVector
      val meVec                = meIDs.toVector
      val ceVec                = ceIDs.toVector
      Set(
        SharedMemoryMultiCore(
          peVec,
          meVec,
          ceVec,
          topoSrcs,
          topoDsts,
          peVec.map(peFreq),
          peVec.map(peOps),
          meVec.map(meSizes),
          ceVec.map(ceMaxChannels),
          ceVec.map(ceBitPerSecPerChannel),
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
