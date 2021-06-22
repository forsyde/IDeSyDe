package idesyde.identification.models


import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.typed.viewers.ReactorActor
import forsyde.io.java.typed.viewers.Signal
import forsyde.io.java.typed.viewers.AbstractProcessingComponent
import forsyde.io.java.typed.viewers.AbstractCommunicationComponent

type ReactorJobType = (ReactorActor, Int)

final case class TimeTriggeredReactorMinus(
    val reactorFires: Set[ReactorJobType],
    val extendedChannels: Map[
      (ReactorJobType, ReactorJobType),
      Signal
    ],
    val reactorApp: ReactorMinusApplication,
    val timeTriggeredPlatform: TimeTriggeredPlatform
) extends JobSchedulingDecisionModel[
      ReactorJobType,
      (Signal, (Int, Int)),
      Set[AbstractProcessingComponent],
      Set[AbstractCommunicationComponent]
    ] {

  def jobs() = reactorFires

  def channels() = extendedChannels.map((jj, s) => s -> (jj._1._2, jj._2._2)).toSet

  def processingElems() = timeTriggeredPlatform.processingElems

  def communicationElems() = timeTriggeredPlatform.communicationElems

  def coveredVertexes() = {
    for (v <- reactorApp.coveredVertexes()) yield v
    for (v <- timeTriggeredPlatform.coveredVertexes()) yield v
  }

  def coveredEdges() = {
    for (e <- reactorApp.coveredEdges()) yield e
    for (e <- timeTriggeredPlatform.coveredEdges()) yield e
  }

  override def dominates(o: DecisionModel) =
    super.dominates(o) && (o match {
      case o: TimeTriggeredReactorMinus =>
        reactorApp.dominates(o.reactorApp) && timeTriggeredPlatform.dominates(
          o.timeTriggeredPlatform
        )
      case _ => true
    })

}
