package idesyde.identification.common.models.workload

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
import scalax.collection.edge.Implicits._
import scalax.collection.GraphEdge.DiEdgeLike
import scalax.collection.edge.WDiEdge
import scalax.collection.GraphTraversal.Parameters.apply
import scalax.collection.GraphTraversal.Parameters

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
  def channelNumInitialTokens: Array[Int]

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
  def dataflowGraphs: Array[Iterable[(String, String, Int)]]

  /** This graph defines how the dataflowGraphs can be changed between each other, assuming that the
    * paramters can change _only_ after an actor firing.
    */
  def configurations: Iterable[(Int, Int, String)]

  /** This parameter counts the number of disjoint actor sets in the application model.def That is,
    * how many 'subapplications' are contained in this application. for for each configuration.
    *
    * This is important to correctly calculate repetition vectors in analytical methods.
    */
  def disjointComponents: Array[Iterable[Iterable[String]]] = dataflowGraphs.map(g => {
    val gGraphed = Graph(g.map((src, dst, w) => src ~> dst).toArray: _*)
    gGraphed.componentTraverser().map(comp => comp.nodes.map(_.value))
  })

  def computeBalanceMatrices = dataflowGraphs.map(df => {
    val m = Array.fill(channels.size)(Array.fill(actors.size)(0))
    for ((src, dst, rate) <- df) {
      if (actors.contains(src) && channels.contains(dst)) {
        m(channels.indexOf(dst))(actors.indexOf(src)) =
          m(channels.indexOf(dst))(actors.indexOf(src)) + rate
      } else if (actors.contains(dst) && channels.contains(src)) {
        m(channels.indexOf(src))(actors.indexOf(dst)) =
          m(channels.indexOf(src))(actors.indexOf(dst)) - rate
      }
    }
    m
  })

  def computeRepetitionVectors: Array[Array[Int]] = dataflowGraphs.zipWithIndex.map((df, dfi) => {
    def minus_one = Rational(-1)
    val g         = Graph(df.map((src, dst, w) => src ~> dst).toArray: _*)
    // first we build a compressed g with only the actors
    // with the fractional flows in a matrix
    var gEdges = Buffer[(String, String)]()
    var gRates = Array.fill(actors.size)(Array.fill(actors.size)(Rational.zero))
    for (
      (src, c, prod)  <- df;
      (cc, dst, cons) <- df;
      if channels.contains(c) && c == cc && actors.contains(src) && actors.contains(dst);
      srcIdx = actors.indexOf(src);
      dstIdx = actors.indexOf(dst);
      rate = Rational(
        prod,
        cons
      )
    ) {
      gEdges += (src -> dst)
      gRates(srcIdx)(dstIdx) = rate
    }
    val gActors = Graph(gEdges.map((src, dst) => src ~ dst).toArray: _*)
    // we iterate on the undirected version as to 'come back'
    // to vertex in feed-forward paths
    val rates      = actors.map(_ => minus_one)
    var consistent = true
    for (
      root <- gActors.nodes.filter(n => n.inDegree == 0);
      if consistent;
      traverser = gActors.outerNodeTraverser(root).withParameters(Parameters.Dfs());
      v <- traverser;
      vIdx = actors.indexOf(v)
    ) {
      // if there is no rate on this vertex already, it must be a root, so we populate it
      if (rates(vIdx) == minus_one) {
        rates(vIdx) = 1
      }
      // populate neighbors based on 'next' which have no rate yet
      for (neigh <- g.get(v).outNeighbors) {
        val neighIdx = actors.indexOf(neigh.value)
        // if no rate exists in the other actor yet, we create it...
        if (rates(neighIdx) == minus_one) {
          // it depenends if the other is a consumer...
          rates(neighIdx) = rates(vIdx) * (gRates(vIdx)(neighIdx))
        }
        // ...otherwise we check if the graph is consistent
        else {
          consistent = consistent && rates(neighIdx) == rates(vIdx) / (gRates(vIdx)(neighIdx))
        }
      }
      for (neigh <- g.get(v).inNeighbors) {
        val neighIdx = actors.indexOf(neigh.value)
        // if no rate exists in the other actor yet, we create it...
        if (rates(neighIdx) == minus_one) {
          // it depenends if the other is a consumer...
          rates(neighIdx) = rates(vIdx) / (gRates(neighIdx)(vIdx))
        }
        // ...otherwise we check if the graph is consistent
        else {
          consistent = consistent && rates(vIdx) == rates(neighIdx) / (gRates(neighIdx)(vIdx))
        }
      }
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

  def pessimisticTokensPerChannel: Array[Int] = {
    channels.zipWithIndex
      .map((c, cIdx) => {
        computeBalanceMatrices.zipWithIndex
          .map((m, mi) => {
            m(cIdx).zipWithIndex
              .filter((col, a) => col > 0)
              .map((col, a) => computeRepetitionVectors(mi)(a) * col)
              .sum
          })
          .max
      })
  }

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
