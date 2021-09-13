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
        .filter(e => GenericDigitalStorage.conforms(e))
        .map(e => GenericDigitalStorage.safeCast(e).get())
        .toSet
      val communicationElements = platformVertexes
        .filter(e => GenericDigitalInterconnect.conforms(e))
        .map(e => GenericDigitalInterconnect.safeCast(e).get())
        .toSet
      val platformElements = processingElements ++ communicationElements ++ memoryElements
      given Set[GenericProcessingModule]    = processingElements
      given Set[GenericDigitalStorage]      = memoryElements
      given Set[GenericDigitalInterconnect] = communicationElements
      given Set[AbstractDigitalModule] = platformElements
      val links =
        for (e <- platformElements; ee <- platformElements; if model.hasConnection(e, ee))
          yield (e, ee)
      val shortestPaths = FloydWarshallShortestPaths(model)
      val paths = (for (
        e <- platformElements; ee <- platformElements;
        // multiple levels of call required since getPath may be null
        path  = shortestPaths.getPath(e.getViewedVertex, ee.getViewedVertex);
        vList = if (path != null) path.getVertexList.asScala.toSeq else Seq.empty
        if !vList.isEmpty && vList.forall(GenericDigitalInterconnect.conforms(_))
      ) yield (e, ee) -> vList.map(GenericDigitalInterconnect.safeCast(_).get())).toMap
      (
        true,
        Option(
          NetworkedDigitalHardware(
            processingElems = processingElements,
            communicationElems = communicationElements,
            storageElems = memoryElements,
            links = links,
            paths = paths
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

  def hasOnlyValidLinks(model: ForSyDeModel)(using
      procElems: Set[GenericProcessingModule],
      memElems: Set[GenericDigitalStorage],
      connElems: Set[GenericDigitalInterconnect]
  ): Boolean = !procElems.exists(pe =>
    procElems.exists(pe2 => model.hasConnection(pe, pe2) || model.hasConnection(pe2, pe))
  ) && !connElems.exists(pe =>
    connElems.exists(pe2 => model.hasConnection(pe, pe2) || model.hasConnection(pe2, pe))
  )

  def processingElementsHaveMemory(model: ForSyDeModel)(using
      platElems: Set[AbstractDigitalModule],
      procElems: Set[GenericProcessingModule],
      memElems: Set[GenericDigitalStorage],
      connElems: Set[GenericDigitalInterconnect]
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
      .filter(e => GenericDigitalStorage.conforms(e))
      .map(e => GenericDigitalStorage.safeCast(e).get())
      .toSet
    val communicationElements = platformVertexes
      .filter(e => GenericDigitalInterconnect.conforms(e))
      .map(e => GenericDigitalInterconnect.safeCast(e).get())
      .toSet
    val platformElements = processingElements ++ communicationElements ++ memoryElements
    given Set[GenericProcessingModule]    = processingElements
    given Set[GenericDigitalStorage]      = memoryElements
    given Set[GenericDigitalInterconnect] = communicationElements
    given Set[AbstractDigitalModule] = platformElements
    hasOneProcessor(model) && hasOnlyValidLinks(model) && processingElementsHaveMemory(model)
  end canIdentify

end NetworkedDigitalHWIdentRule