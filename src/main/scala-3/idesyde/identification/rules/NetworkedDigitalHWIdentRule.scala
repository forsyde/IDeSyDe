package idesyde.identification.rules

import forsyde.io.java.core.ForSyDeModel
import forsyde.io.java.typed.viewers.{
  AbstractDigitalModule,
  GenericDigitalInterconnect,
  GenericDigitalStorage,
  GenericProcessingModule
}
import idesyde.identification.interfaces.{DecisionModel, IdentificationRule}
import idesyde.identification.models.NetworkedDigitalHardware
import org.jgrapht.alg.shortestpath.{AllDirectedPaths, FloydWarshallShortestPaths}

import collection.JavaConverters.*

final case class NetworkedDigitalHWIdentRule()
    extends IdentificationRule[NetworkedDigitalHardware] {

  override def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[NetworkedDigitalHardware]) = {
    val platformElements = model
      .vertexSet()
      .asScala
      .filter(e => AbstractDigitalModule.conforms(e))
      .map(e => AbstractDigitalModule.safeCast(e).get())
      .toSet
    val processingElements = platformElements
      .filter(e => GenericProcessingModule.conforms(e))
      .map(e => GenericProcessingModule.safeCast(e).get())
      .toSet
    val memoryElements = platformElements
      .filter(e => GenericDigitalStorage.conforms(e))
      .map(e => GenericDigitalStorage.safeCast(e).get())
      .toSet
    val communicationElements = platformElements
      .filter(e => GenericDigitalInterconnect.conforms(e))
      .map(e => GenericDigitalInterconnect.safeCast(e).get())
      .toSet
    given Set[GenericProcessingModule]    = processingElements
    given Set[GenericDigitalStorage]      = memoryElements
    given Set[GenericDigitalInterconnect] = communicationElements
    scribe.debug(s"hasValidTraits: ${hasOneProcessor(model)}")
    scribe.debug(s"hasOnlyValidLinks: ${hasOnlyValidLinks(model)}")
    scribe.debug(s"processingElementsHaveMemory: ${processingElementsHaveMemory(model)}")
    if (hasOneProcessor(model) && hasOnlyValidLinks(model) && processingElementsHaveMemory(model)) {
      val links =
        for (e <- platformElements; ee <- platformElements; if model.hasConnection(e, ee))
          yield (e, ee)
      val shortestPaths = FloydWarshallShortestPaths(model)
      val paths = (for (
        e <- platformElements; ee <- platformElements;
        // multiple levels of call required since getPath may be null
        path  = shortestPaths.getPath(e.getViewedVertex, ee.getViewedVertex);
        vList = if (path != null) path.getVertexList.asScala.toSeq else Seq.empty
        if !vList.isEmpty && vList.forall(_.isInstanceOf[GenericDigitalInterconnect])
      ) yield (e, ee) -> vList.map(_.asInstanceOf[GenericDigitalInterconnect])).toMap
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
      procElems: Set[GenericProcessingModule],
      memElems: Set[GenericDigitalStorage],
      connElems: Set[GenericDigitalInterconnect]
  ): Boolean = {
    val paths = AllDirectedPaths(model)
    procElems.forall(pe =>
      !paths
        .getAllPaths(
          Set(pe.getViewedVertex).asJava,
          memElems.map(_.getViewedVertex).asJava,
          true,
          null
        )
        .isEmpty
//      memElems.exists(mem =>
//        paths.getAllPaths(pe.getViewedVertex, mem.getViewedVertex, true, null).asScala
//          .exists(p => p.getVertexList.asScala.ex)
//      )
    )
  }

}
