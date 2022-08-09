package idesyde.identification.forsyde.rules.platform

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.forsyde.{
  ForSyDeDecisionModel,
  ForSyDeIdentificationRule
}
import org.jgrapht.alg.shortestpath.{AllDirectedPaths, FloydWarshallShortestPaths}

import collection.JavaConverters.*
import org.jgrapht.graph.AsSubgraph
import idesyde.identification.forsyde.models.platform.NetworkedDigitalHardware
import forsyde.io.java.typed.viewers.platform.DigitalModule
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.GenericCommunicationModule
import idesyde.utils.MultipliableFractional
import idesyde.identification.forsyde.models.platform.TiledDigitalHardware
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import spire.math.Rational
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

final case class TiledDigitalHardwareIRule()(using Fractional[Rational])(using Conversion[Double, Rational])
    extends ForSyDeIdentificationRule[TiledDigitalHardware] {

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
    // check if the elements can all be distributed in tiles
    // basically this check to see if there are always neighboring
    // pe, mem and ce
    lazy val tilesExist = processingElements.forall(pe => {
      memoryElements
        .find(mem => model.hasConnection(mem, pe) || model.hasConnection(pe, mem))
        .map(mem =>
          communicationElements.exists(ce =>
            (model.hasConnection(ce, pe) || model.hasConnection(pe, ce)) &&
              (model.hasConnection(mem, ce) || model.hasConnection(ce, mem))
          )
        )
        .getOrElse(false)
    })
    // now tile elements via sorting of the processing elements
    lazy val tiledMemories = memoryElements.sortBy(mem => {
      processingElements
        .filter(pe => model.hasConnection(pe, mem) || model.hasConnection(mem, pe))
        .map(pe => processingElements.indexOf(pe))
        .minOption
        .getOrElse(-1)
    })
    // we separate the comms in NI and routers
    lazy val tileableCommElems = communicationElements.filter(ce => {
      processingElements.exists(pe => model.hasConnection(pe, ce) || model.hasConnection(ce, pe))
    })
    // and do the same as done for the memories
    lazy val tiledCommElems = tileableCommElems.sortBy(ce => {
      processingElements
        .filter(pe => model.hasConnection(pe, ce) || model.hasConnection(ce, pe))
        .map(pe => processingElements.indexOf(pe))
        .minOption
        .getOrElse(-1)
    })
    lazy val routers = communicationElements.filterNot(ce => tileableCommElems.contains(ce))
    // and also the subset of only communication elements
    lazy val interconGraph = {
      val g = SimpleDirectedGraph.createBuilder[GenericCommunicationModule, DefaultEdge](() =>
        DefaultEdge()
      )
      communicationElements.foreach(ce => g.addVertex(ce))
      communicationElements.foreach(srcce =>
        model
          .outgoingEdgesOf(srcce.getViewedVertex())
          .forEach(e =>
            val dst = model.getEdgeTarget(e)
            GenericCommunicationModule
              .safeCast(dst)
              .ifPresent(dstce => {
                g.addEdge(srcce, dstce)
              })
          )
      )
      g.buildAsUnmodifiable()
    }
    if (
      processingElements.length > 0 &&
      processingElements.size <= memoryElements.size &&
      processingElements.size <= communicationElements.size &&
      processingOnlyValidLinks && memoryOnlyValidLinks && tilesExist
    ) {
      val platformElements = processingElements ++ communicationElements ++ memoryElements
      val links =
        for (e <- platformElements; ee <- platformElements; if model.hasConnection(e, ee))
          yield (e, ee)
      IdentificationResult(
        true,
        TiledDigitalHardware(
          processors = processingElements,
          memories = tiledMemories,
          networkInterfaces = tiledCommElems,
          routers = routers,
          interconnectTopology = interconGraph
        )
      )
    } else IdentificationResult(true, Option.empty)
  }

}
