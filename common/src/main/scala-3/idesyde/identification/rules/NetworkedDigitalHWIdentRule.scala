package idesyde.identification.rules

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.{DecisionModel, IdentificationRule}
import org.jgrapht.alg.shortestpath.{AllDirectedPaths, FloydWarshallShortestPaths}

import collection.JavaConverters.*
import org.jgrapht.graph.AsSubgraph
import idesyde.identification.models.platform.NetworkedDigitalHardware
import forsyde.io.java.typed.viewers.platform.DigitalModule
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule

final case class NetworkedDigitalHWIdentRule()
    extends IdentificationRule {

  override def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = {
    if (NetworkedDigitalHWIdentRule.canIdentify(model, identified)) {
      val platformVertexes = model
            .vertexSet()
            .asScala
            .filter(e => DigitalModule.conforms(e))
      val processingElements = platformVertexes
        .filter(e => GenericProcessingModule.conforms(e))
        .map(e => GenericProcessingModule.safeCast(e).get())
      val memoryElements = platformVertexes
        .filter(e => GenericMemoryModule.conforms(e))
        .map(e => GenericMemoryModule.safeCast(e).get())
      val communicationElements = platformVertexes
        .filter(e => GenericCommunicationModule.conforms(e))
        .map(e => GenericCommunicationModule.safeCast(e).get())
      val platformElements = processingElements ++ communicationElements ++ memoryElements
      val links =
        for (e <- platformElements; ee <- platformElements; if model.hasConnection(e, ee))
          yield (e, ee)
      (
        true,
        Option(
          NetworkedDigitalHardware(
            processingElems = processingElements.toArray,
            communicationElems = communicationElements.toArray,
            storageElems = memoryElements.toArray,
            links = links.toArray
          )
        )
      )
    } else (true, Option.empty)
  }

}

object NetworkedDigitalHWIdentRule:

  def hasOneProcessor(model: ForSyDeSystemGraph): Boolean =
      model
        .vertexSet()
        .asScala
        .exists(v => GenericProcessingModule.conforms(v))

  def hasOnlyValidLinks(model: ForSyDeSystemGraph,
      procElems: Set[GenericProcessingModule],
      connElems: Set[GenericCommunicationModule]
  ): Boolean = !procElems.exists(pe =>
    procElems.exists(pe2 => model.hasConnection(pe, pe2) || model.hasConnection(pe2, pe))
  ) && !connElems.exists(pe =>
    connElems.exists(pe2 => model.hasConnection(pe, pe2) || model.hasConnection(pe2, pe))
  )

  def processingElementsHaveMemory(model: ForSyDeSystemGraph,
      platElems: Set[DigitalModule],
      procElems: Set[GenericProcessingModule],
      memElems: Set[GenericMemoryModule]
  ): Boolean = {
    val platGraph = AsSubgraph(model, platElems.map(_.getViewedVertex).asJava)
    val paths = AllDirectedPaths(platGraph)
    procElems.forall(pe =>
      memElems.exists(mem => 
        !paths.getAllPaths(pe.getViewedVertex, mem.getViewedVertex, true, null).isEmpty
        )
    )
  }

  def canIdentify(model: ForSyDeSystemGraph, identified: Set[DecisionModel]): Boolean =
    val platformVertexes = model
          .vertexSet()
          .asScala
          .filter(e => DigitalModule.conforms(e))
    val processingElements = platformVertexes
      .filter(e => GenericProcessingModule.conforms(e))
      .map(e => GenericProcessingModule.safeCast(e).get())
      .toSet
    val memoryElements = platformVertexes
      .filter(e => GenericMemoryModule.conforms(e))
      .map(e => GenericMemoryModule.safeCast(e).get())
      .toSet
    val communicationElements = platformVertexes
      .filter(e => GenericCommunicationModule.conforms(e))
      .map(e => GenericCommunicationModule.safeCast(e).get())
      .toSet
    val platformElements = processingElements ++ communicationElements ++ memoryElements
    hasOneProcessor(model) && hasOnlyValidLinks(model, processingElements, communicationElements) &&
     processingElementsHaveMemory(model, platformElements, processingElements, memoryElements)
  end canIdentify

end NetworkedDigitalHWIdentRule