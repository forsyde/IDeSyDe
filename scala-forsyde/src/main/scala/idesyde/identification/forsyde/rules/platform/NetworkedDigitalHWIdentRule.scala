package idesyde.identification.forsyde.rules.platform

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.forsyde.{ForSyDeDecisionModel, ForSyDeIdentificationRule}
import org.jgrapht.alg.shortestpath.{AllDirectedPaths, FloydWarshallShortestPaths}

import collection.JavaConverters.*
import org.jgrapht.graph.AsSubgraph
import idesyde.identification.forsyde.models.platform.NetworkedDigitalHardware
import forsyde.io.java.typed.viewers.platform.DigitalModule
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule
import idesyde.utils.MultipliableFractional
import spire.math.Rational
import spire.implicits.*
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

final case class NetworkedDigitalHWIdentRule()
    extends ForSyDeIdentificationRule[NetworkedDigitalHardware] {

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ) = {
    var processingElements    = Array.empty[GenericProcessingModule]
    var memoryElements        = Array.empty[GenericMemoryModule]
    var communicationElements = Array.empty[GenericCommunicationModule]
    model.vertexSet.stream
      .filter(v => DigitalModule.conforms(v))
      .forEach(v => {
        GenericProcessingModule
          .safeCast(v)
          .ifPresent(p => processingElements :+= p)
        GenericMemoryModule
          .safeCast(v)
          .ifPresent(p => memoryElements :+= p)
        GenericCommunicationModule
          .safeCast(v)
          .ifPresent(p => communicationElements :+= p)
      })
    // check if pes and mes connect only to CE etc
    lazy val processingOnlyValidLinks = processingElements.forall(pe => {
      model
        .outgoingEdgesOf(pe.getViewedVertex)
        .stream
        .map(model.getEdgeTarget(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v => GenericCommunicationModule.conforms(v) || GenericMemoryModule.conforms(v))
      &&
      model
        .incomingEdgesOf(pe.getViewedVertex)
        .stream
        .map(model.getEdgeSource(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v => GenericCommunicationModule.conforms(v) || GenericMemoryModule.conforms(v))
    })
    // do the same for MEs
    lazy val memoryOnlyValidLinks = memoryElements.forall(me => {
      model
        .outgoingEdgesOf(me.getViewedVertex)
        .stream
        .map(model.getEdgeTarget(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v =>
          GenericCommunicationModule.conforms(v) || GenericProcessingModule.conforms(v)
        )
      &&
      model
        .incomingEdgesOf(me.getViewedVertex)
        .stream
        .map(model.getEdgeSource(_))
        .filter(DigitalModule.conforms(_))
        .allMatch(v =>
          GenericCommunicationModule.conforms(v) || GenericProcessingModule.conforms(v)
        )
    })
    if (processingElements.length > 0 && processingOnlyValidLinks && memoryOnlyValidLinks) {
      val platformElements = processingElements ++ communicationElements ++ memoryElements
      val links =
        for (e <- platformElements; ee <- platformElements; if model.hasConnection(e, ee))
          yield (e, ee)
      new IdentificationResult(
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
    } else new IdentificationResult(true, Option.empty)
  }

}

object NetworkedDigitalHWIdentRule:

  def hasOneProcessor(model: ForSyDeSystemGraph): Boolean =
    model
      .vertexSet()
      .asScala
      .exists(v => GenericProcessingModule.conforms(v))

  def hasOnlyValidLinks(
      model: ForSyDeSystemGraph,
      procElems: Set[GenericProcessingModule],
      connElems: Set[GenericCommunicationModule]
  ): Boolean = !procElems.exists(pe =>
    procElems.exists(pe2 => model.hasConnection(pe, pe2) || model.hasConnection(pe2, pe))
  ) && !connElems.exists(pe =>
    connElems.exists(pe2 => model.hasConnection(pe, pe2) || model.hasConnection(pe2, pe))
  )

  def processingElementsHaveMemory(
      model: ForSyDeSystemGraph,
      platElems: Set[DigitalModule],
      procElems: Set[GenericProcessingModule],
      memElems: Set[GenericMemoryModule]
  ): Boolean = {
    val platGraph = AsSubgraph(model, platElems.map(_.getViewedVertex).asJava)
    val paths     = AllDirectedPaths(platGraph)
    procElems.forall(pe =>
      memElems.exists(mem =>
        !paths.getAllPaths(pe.getViewedVertex, mem.getViewedVertex, true, null).isEmpty
      )
    )
  }

  def canIdentify(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ): Boolean =
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
