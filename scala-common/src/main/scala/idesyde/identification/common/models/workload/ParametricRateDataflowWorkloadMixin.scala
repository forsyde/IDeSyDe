package idesyde.identification.models.workload

import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import spire.math._
import spire.algebra._
import scala.collection.mutable.Queue
import java.util.stream.Collectors
import scala.collection.mutable
import scala.collection.mutable.Buffer
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.edge.WDiEdge
import scalax.collection.edge.Implicits._
import scalax.collection.GraphEdge.DiEdgeLike
import scalax.collection.GraphEdge.DiEdge

/** This traits captures the ParametricRateDataflow base MoC from [1]. Then, we hope to be able to
  * use the same code for analysis across different dataflow MoCs, specially the simpler ones like
  * SDF and CSDF.
  *
  * [1] A. Bouakaz, P. Fradet, and A. Girault, “A survey of parametric dataflow models of
  * computation,” ACM Transactions on Design Automation of Electronic Systems, vol. 22, no. 2, 2017,
  * doi: 10.1145/2999539.
  */
trait ParametricRateDataflowWorkloadMixin {
  def actors: Array[String]
  def channels: Array[String]
  def initialTokens: Array[Int]

  /** An actor is self-concurrent if two or more instance can be executed at the same time
    *
    * As a rule of thumb, actor with "state" are not self-concurrent.
    */
  def isSelfConcurrent(actor: String): Boolean

  /** The edges of the communication graph should have numbers describing how much data is
    * transferred from actors to channels. That is, both actors _and_ channels indexes are part of
    * the graph, for each configuration.
    *
    * The array of graphs represent each possible dataflow graph when the parameters are
    * instantiated.
    */
  def dataflowGraphs: Array[Graph[String, ? <: WDiEdge]]

  /** This graph defines how the dataflowGraphs can be changed between each other, assuming that the
    * paramters can change _only_ after an actor firing.
    */
  def configurations: Graph[String, ? <: WDiEdge]

  /** This parameter counts the number of disjoint actor sets in the application model.def That is,
    * how many 'subapplications' are contained in this application. for for each configuration.
    *
    * This is important to correctly calculate repetition vectors in analytical methods.
    */
  def disjointComponents: Array[Iterable[Iterable[String]]] = dataflowGraphs.map(g => {
    g.componentTraverser().map(comp => comp.nodes.map(_.toOuter.toString()))
  })

  def computeBalanceMatrices = dataflowGraphs.map(g => {
    val m = Array.fill(channels.size)(Array.fill(actors.size)(0))
    for (e <- g.edges) {
      val src = e.source.toOuter.toString()
      val dst = e.target.toOuter.toString()
      if (actors.contains(src) && channels.contains(dst)) {
        m(channels.indexOf(dst))(actors.indexOf(src)) =
          m(channels.indexOf(dst))(actors.indexOf(src)) + e.weight.toInt
      } else if (actors.contains(dst) && channels.contains(src)) {
        m(channels.indexOf(src))(actors.indexOf(dst)) =
          m(channels.indexOf(src))(actors.indexOf(dst)) - e.weight.toInt
      }
    }
    m
  })

  def computeRepetitionVectors: Array[Array[Int]] = dataflowGraphs.zipWithIndex.map((g, gi) => {
    def minus_one = Rational(-1)
    // first we build a compressed g with only the actors
    // with the fractional flows in a matrix
    // val gRates = Array.fill(actors.size)(Array.fill(actors.size)(Rational.zero))
    // val gActorsBuilder = SimpleDirectedGraph.createBuilder[Int, DefaultEdge](() => DefaultEdge())
    // and put the rates between them in a matrix
    // channels.foreach(c => {
    //   // we do a for, but there should only be one producer and one consumer per actor
    //   g.incomingEdgesOf(c)
    //     .forEach(producerEdge => {
    //       val producer = g.getEdgeSource(producerEdge)
    //       g.outgoingEdgesOf(c)
    //         .forEach(consumerEdge => {
    //           val consumer = g.getEdgeTarget(consumerEdge)
    //           // val rate = Rational(g.getEdgeWeight(producerEdge).toInt, g.getEdgeWeight(consumerEdge).toInt)
    //           val rate = gRates(producer)(consumer)
    //           gActorsBuilder.addEdge(producer, consumer)
    //           // the if-else is required to subtract the +1 denominator that comes with a zero fraction
    //           gRates(producer)(consumer) =
    //             if (rate.equals(Rational.zero)) then
    //               Rational(
    //                 rate.numerator.toInt + g.getEdgeWeight(producerEdge).toInt,
    //                 rate.denominator.toInt - 1 + g.getEdgeWeight(consumerEdge).toInt
    //               )
    //             else
    //               Rational(
    //                 rate.numerator.toInt + g.getEdgeWeight(producerEdge).toInt,
    //                 rate.denominator.toInt + g.getEdgeWeight(consumerEdge).toInt
    //               )
    //         })
    //     })
    // })
    // val gActors = gActorsBuilder.buildAsUnmodifiable()
    var gEdges = Buffer[(String, String)]()
    var gRates = Array.fill(actors.size)(Array.fill(actors.size)(Rational.zero))
    for (
      c <- channels;
      cNode = g.get(c);
      src <- cNode.diPredecessors;
      dst <- cNode.diSuccessors;
      srcIdx = actors.indexOf(src.value.toString());
      dstIdx = actors.indexOf(dst.value.toString());
      rate = Rational(
        (cNode <~ src).map(_.weight).sum.toLong,
        (cNode ~> src).map(_.weight).sum.toLong
      )
    ) {
      gEdges += (src.value.toString() -> dst.value.toString())
      gRates(srcIdx)(dstIdx) = rate
    }
    val gActors = Graph(gEdges.map((src, dst) => src ~> dst).toArray: _*)
    // we iterate on the undirected version as to 'come back'
    // to vertex in feed-forward paths
    val dfs        = DepthFirstIterator(AsUndirectedGraph(gActors))
    val rates      = actors.map(_ => minus_one)
    var consistent = true
    while (dfs.hasNext() && consistent) {
      val nextActor = dfs.next()
      val next      = actors.indexOf(nextActor)
      // if there is no rate on this vertex already, it must be a root, so we populate it
      if (rates(next).equals(minus_one)) {
        rates(next) = Rational.one
      }
      // populate neighbors based on 'next' which have no rate yet
      gActors
        .outgoingEdgesOf(nextActor)
        .forEach(e => {
          val other = actors.indexOf(gActors.getEdgeTarget(e))
          // if no rate exists in the other actor yet, we create it...
          if (rates(other).equals(minus_one)) {
            // it depenends if the other is a consumer...
            rates(other) = rates(next) * (gRates(next)(other))
          }
          // ...otherwise we check if the graph is consistent
          else {
            consistent = rates(other) == rates(next) / (gRates(next)(other))
          }
        })
      gActors
        .incomingEdgesOf(nextActor)
        .forEach(e => {
          val otherActor = gActors.getEdgeSource(e)
          val other      = actors.indexOf(otherActor)
          // if no rate exists in the other actor yet, we create it...
          if (rates(other).equals(minus_one)) {
            // it depenends if the other is a consumer...
            rates(other) = rates(next) / (gRates(other)(next))
          }
          // ...otherwise we check if the graph is consistent
          else {
            consistent = rates(next) == rates(other) / (gRates(other)(next))
          }
        })
    }
    // finish early in case of non consistency
    if (!consistent)
      return Array()
    // otherwise simplify the repVec
    val gcdV = rates.map(_.numerator.toLong).reduce((i1, i2) => spire.math.gcd(i1, i2))
    val lcmV = rates
      .map(_.denominator.toLong)
      .reduce((i1, i2) => spire.math.lcm(i1, i2))
    rates.map(_ * lcmV / gcdV).map(_.numerator.toInt)
  })
  // computeBalanceMatrices.zipWithIndex.map((m, ind) => SDFUtils.getRepetitionVector(m, initialTokens, numDisjointComponents(ind)))

  // def isConsistent = repetitionVectors.forall(r => r.size == actors.size)

  // def isLive = maximalParallelClustering.zipWithIndex.map((cluster, i) => !cluster.isEmpty)

  // def pessimisticTokensPerChannel: Array[Int] = {
  //   channels.zipWithIndex.map((c, cIdx) => {
  //     var pessimisticMax = 1
  //     dataflowGraphs.zipWithIndex
  //       .foreach((g, confIdx) => {
  //         g.incomingEdgesOf(c)
  //           .forEach(e => {
  //             val aIdx = actors.indexOf(g.getEdgeSource(e))
  //             pessimisticMax = Math.max(
  //               repetitionVectors(confIdx)(aIdx) * balanceMatrices(confIdx)(cIdx)(
  //                 aIdx
  //               ) + initialTokens(cIdx),
  //               pessimisticMax
  //             )
  //           })
  //       })
  //     pessimisticMax
  //   })
  // }

  // def stateSpace: Graph[Int, Int] = {
  //   // first, convert the arrays into a mathematical form
  //   val matrices = balanceMatrices.map(m => {
  //     val newM = DenseMatrix.zeros[Int](m.size, m(0).size)
  //     m.zipWithIndex.foreach((row, i) =>
  //       row.zipWithIndex.foreach((col, j) => {
  //         newM(i, j) = col
  //       })
  //     )
  //     newM
  //   })
  //   val g        = DefaultDirectedGraph[Int, Int](() => 0, () => 0, false)
  //   var explored = Array(DenseVector(initialTokens))
  //   // q is a queue of configuration and state
  //   var q = Queue((0, DenseVector(initialTokens)))
  //   //g.addVertex(initialTokens)
  //   while (!q.isEmpty) {
  //     val (conf, state) = q.dequeue
  //     val m             = matrices(conf)
  //     val newStates = actors
  //       .map(a => {
  //         val v = DenseVector.zeros[Int](actors.size)
  //         v(a) = 1
  //         (a, v)
  //       })
  //       .map((a, v) => (a, state + (m * v)))
  //       // all states must be non negative
  //       .filter((_, s) => s.forall(b => b >= 0))
  //       .filter((_, s) => !explored.contains(s))
  //     // we add the states to the space
  //     newStates.foreach((a, s) => {
  //       explored :+= s
  //       g.addEdge(explored.indexOf(state), explored.size - 1, a)
  //       // and product them with the possible next configurations
  //       configurations
  //         .outgoingEdgesOf(conf)
  //         .stream
  //         .map(e => configurations.getEdgeTarget(e))
  //         .forEach(newConf => q.enqueue((newConf, s)))
  //     })
  //   }
  //   g
  // }

  /** returns the cluster of actor firings that have zero time execution time and can fire in
    * parallel, until all the firings are exhausted in accordance to the
    * [[computeRepetitionVectors]]
    *
    * This is also used to check the liveness of each configuration. If a configuration is not live,
    * then its clusters are empty, since at the very least one should exist.
    */
  // def maximalParallelClustering: Array[Array[Array[Int]]] =
  //   dataflowGraphs.zipWithIndex.map((g, gi) => {
  //     val actors                               = 0 until actors.size
  //     val channels                             = 0 until channels.size
  //     var buffer                               = Buffer(DenseVector(initialTokens))
  //     val topologyMatrix                       = DenseMatrix(computeBalanceMatrices(gi): _*)
  //     var firings                              = DenseVector(computeRepetitionVectors(gi))
  //     var executions: Buffer[DenseVector[Int]] = Buffer(DenseVector.zeros(actors.size))
  //     var currentCluster                       = 0
  //     var moreToFire                           = firings.exists(_ > 0)
  //     while (moreToFire) {
  //       val fired = actors.zipWithIndex
  //         .flatMap((a, i) => {
  //           val qs = if (isSelfConcurrent(a)) then (1 to 1) else (firings(i) to 1 by -1)
  //           qs.map(q => {
  //             executions(currentCluster)(i) = q
  //             val result =
  //               (i, q, (topologyMatrix * executions(currentCluster)) + buffer(currentCluster))
  //             executions(currentCluster)(i) = 0
  //             result
  //           })
  //         })
  //         // keep only the options that do not underflow the buffer
  //         .filter((ai, q, b) => all(b >:= 0))
  //         .count((ai, q, b) => {
  //           // accept the change if there is any possible
  //           // scribe.debug((ai, q, currentCluster, b.toString).toString()) // it is +1 because the initial conditions are at 0
  //           executions(currentCluster)(ai) = q
  //           firings(ai) -= q
  //           true
  //         })
  //       moreToFire = firings.exists(_ > 0)
  //       if (moreToFire && fired == 0) { // more should be fired by cannot. Thus deadlock.
  //         return Array()
  //       } else if (moreToFire) { //double check for now just so the last empty entry is not added
  //         buffer :+= topologyMatrix * executions(currentCluster) + buffer(currentCluster)
  //         executions :+= DenseVector.zeros(actors.size)
  //         currentCluster += 1
  //       }
  //     }
  //     executions.map(_.data).toArray
  //   })
}
