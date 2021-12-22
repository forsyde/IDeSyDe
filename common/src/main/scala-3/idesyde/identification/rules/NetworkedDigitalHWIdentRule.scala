package idesyde.identification.rules

import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.typed.viewers.{
  AbstractDigitalModule,
  GenericDigitalInterconnect,
  GenericDigitalStorage,
  GenericProcessingModule
}
import idesyde.identification.{DecisionModel, IdentificationRule}
import idesyde.identification.models.NetworkedDigitalHardware
import org.jgrapht.alg.shortestpath.{AllDirectedPaths, FloydWarshallShortestPaths}

import collection.JavaConverters.*
import org.jgrapht.graph.AsSubgraph
import forsyde.io.java.typed.viewers.GenericMemoryModule

final case class NetworkedDigitalHWIdentRule()
    extends IdentificationRule {

  override def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) = {
    if (NetworkedDigitalHWIdentRule.canIdentify(model, identified)) {
      val platformVertexes = model
            .vertexSet()
            .asScala
            .filter(e => AbstractDigitalModule.conforms(e))
      val processingElements = platformVertexes
        .filter(e => GenericProcessingModule.conforms(e))
        .map(e => GenericProcessingModule.safeCast(e).get())
        .toSet
      val memoryElements = platformVertexes
        .filter(e => GenericMemoryModule.conforms(e))
        .map(e => GenericMemoryModule.safeCast(e).get())
        .toSet
      val communicationElements = platformVertexes
        .filter(e => GenericDigitalInterconnect.conforms(e))
        .map(e => GenericDigitalInterconnect.safeCast(e).get())
        .toSet
      val platformElements = processingElements ++ communicationElements ++ memoryElements
      val links =
        for (e <- platformElements; ee <- platformElements; if model.hasConnection(e, ee))
          yield (e, ee)
      (
        true,
        Option(
          NetworkedDigitalHardware(
            processingElems = processingElements,
            communicationElems = communicationElements,
            storageElems = memoryElements,
            links = links
          )
        )
      )
    } else (true, Option.empty)
  }

}

object NetworkedDigitalHWIdentRule:

  def hasOneProcessor(model: ForSyDeModel): Boolean =
      model
        .vertexSet()
        .asScala
        .exists(v => GenericProcessingModule.conforms(v))

  def hasOnlyValidLinks(model: ForSyDeModel,
      procElems: Set[GenericProcessingModule],
      connElems: Set[GenericDigitalInterconnect]
  ): Boolean = !procElems.exists(pe =>
    procElems.exists(pe2 => model.hasConnection(pe, pe2) || model.hasConnection(pe2, pe))
  ) && !connElems.exists(pe =>
    connElems.exists(pe2 => model.hasConnection(pe, pe2) || model.hasConnection(pe2, pe))
  )

  def processingElementsHaveMemory(model: ForSyDeModel,
      platElems: Set[AbstractDigitalModule],
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

  def canIdentify(model: ForSyDeModel, identified: Set[DecisionModel]): Boolean =
    val platformVertexes = model
          .vertexSet()
          .asScala
          .filter(e => AbstractDigitalModule.conforms(e))
    val processingElements = platformVertexes
      .filter(e => GenericProcessingModule.conforms(e))
      .map(e => GenericProcessingModule.safeCast(e).get())
      .toSet
    val memoryElements = platformVertexes
      .filter(e => GenericMemoryModule.conforms(e))
      .map(e => GenericMemoryModule.safeCast(e).get())
      .toSet
    val communicationElements = platformVertexes
      .filter(e => GenericDigitalInterconnect.conforms(e))
      .map(e => GenericDigitalInterconnect.safeCast(e).get())
      .toSet
    val platformElements = processingElements ++ communicationElements ++ memoryElements
    hasOneProcessor(model) && hasOnlyValidLinks(model, processingElements, communicationElements) &&
     processingElementsHaveMemory(model, platformElements, processingElements, memoryElements)
  end canIdentify

end NetworkedDigitalHWIdentRule