package idesyde.identification.forsyde.rules.sdf

import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.moc.sdf.SDFActor
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem

trait SDFQueriesMixin {

  /*
  def sdfIsWellFormed(
      model: ForSyDeSystemGraph,
      actors: Array[SDFActor],
      channels: Array[SDFChannel],
      delays: Array[SDFDelay]
  ): Boolean =
    // all channels connect to one and only one delay or actor.
    channels.forall(channel => {
      model
        .incomingEdgesOf(channel.getViewedVertex)
        .stream
        .map(model.getEdgeSource(_))
        .filter(SDFElem.conforms(_))
        .filter(v => SDFActor.conforms(v) || SDFDelay.conforms(v))
        .count <= 1
      &&
      model
        .outgoingEdgesOf(channel.getViewedVertex)
        .stream
        .map(model.getEdgeTarget(_))
        .filter(SDFElem.conforms(_))
        .filter(v => SDFActor.conforms(v) || SDFDelay.conforms(v))
        .count <= 1
    })

  def getSDFTopology(
      model: ForSyDeSystemGraph,
      actors: Array[SDFActor],
      channels: Array[SDFChannel],
      delays: Array[SDFDelay]
  ): (Array[Array[Int]], Array[Array[SDFElem]]) =
    val aggregated: Array[Array[SDFElem]] =
      delays.map(d =>
        Array(d.getNotDelayedChannelPort(model).get, d, d.getDelayedChannelPort(model).get)
      )
    // now add the signals that were not part of any delay chain
    val channelsLumping =
      aggregated ++ (channels
        .filterNot(channel => aggregated.exists(_.indexOf(channel) > 0)))
        // it is fine to cast it.
        .map(channel => Array(channel.asInstanceOf[SDFElem]))
    val matrix =
      Array.fill(actors.length)(
        Array.fill(channelsLumping.length)(0)
      )
    for (
      (actor, i)       <- actors.zipWithIndex;
      (channelLump, j) <- channelsLumping.zipWithIndex
    ) {
      if (model.hasConnection(actor, channelLump.head)) then
        model
          .getAllEdges(actor.getViewedVertex, channelLump.head.getViewedVertex)
          .stream
          .filter(e => e.sourcePort.map(actor.getPorts.contains(_)).orElse(false))
          .map(e => e.sourcePort.map(actor.getProduction.getOrDefault(_, 0)).orElse(0))
          .findFirst
          .ifPresent(produced => matrix(i).update(j, produced.toInt))
      else if (model.hasConnection(channelLump.last, actor)) then
        model
          .getAllEdges(channelLump.last.getViewedVertex, actor.getViewedVertex)
          .stream
          .filter(e => e.targetPort.map(actor.getPorts.contains(_)).orElse(false))
          .map(e => e.targetPort.map(actor.getConsumption.getOrDefault(_, 0)).orElse(0))
          .findFirst
          .ifPresent(consumed => matrix(i).update(j, -consumed.toInt))
    }
    (matrix, channelsLumping)

  def calculateRepetitionVector(
      topology: Array[Array[Int]]
  ): Option[Array[Int]] =
    val matrix =
      new Array2DRowRealMatrix(topology.map(_.map(_.toDouble)))
    val svd     = SingularValueDecomposition(matrix)
    val zeroIdx = svd.getSingularValues.indexOf(0.0)
    if (zeroIdx > -1) then
      val repetitionFractions = svd.getVT.getColumn(zeroIdx).map(Fraction(_))
      val factor = repetitionFractions.reduce((frac1, frac2) =>
        // the LCM of a nunch of Rationals n1/d1, n2/d2... is lcm(n1, n2,...)/gcd(d1, d2,...). You can check.
        Fraction(
          ArithmeticUtils.lcm(frac1.getNumerator, frac2.getNumerator),
          ArithmeticUtils.gcd(frac1.getDenominator, frac2.getDenominator)
        )
      )
      Option(repetitionFractions.map(_.multiply(factor).getNumerator).toArray)
    else Option.empty[Array[Int]]
   */

}
