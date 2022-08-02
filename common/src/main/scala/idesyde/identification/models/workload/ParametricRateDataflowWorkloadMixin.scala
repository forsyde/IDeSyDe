package idesyde.identification.models.workload

import scala.jdk.StreamConverters.*
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import idesyde.utils.SDFUtils
import org.apache.commons.math3.fraction.BigFraction
import org.jgrapht.graph.DefaultDirectedGraph
import scala.collection.mutable.Queue
import org.apache.commons.math3.linear.MatrixUtils
import breeze.linalg.DenseMatrix
import breeze.linalg.DenseVector
import breeze.linalg._
import breeze.numerics._
import org.jgrapht.opt.graph.sparse.SparseIntDirectedGraph
import org.jgrapht.alg.util.Pair
import scala.jdk.StreamConverters._
import org.jgrapht.opt.graph.sparse.IncomingEdgesSupport

/** This traits captures the ParametricRateDataflow base MoC from [1]. Then, we hope to be able to
  * use the same code for analysis across different dataflow MoCs, specially the simpler ones like
  * SDF and CSDF.
  *
  * [1] A. Bouakaz, P. Fradet, and A. Girault, “A survey of parametric dataflow models of
  * computation,” ACM Transactions on Design Automation of Electronic Systems, vol. 22, no. 2, 2017,
  * doi: 10.1145/2999539.
  */
trait ParametricRateDataflowWorkloadMixin(using Integral[BigFraction]) {
  def actorsSet: Array[Int]
  def channelsSet: Array[Int]
  def initialTokens: Array[Int]

  /** An actor is self-concurrent if two or more instance can be executed at the same time
    *
    * As a rule of thumb, actor with "state" are not self-concurrent.
    */
  def isSelfConcurrent(actor: Int): Boolean

  /** The edges of the communication graph should have numbers describing how much data is
    * transferred from actors to channels. That is, both actors _and_ channels indexes are
    * part of the graph, for each configuration.
    *
    * The array of graphs represent each possible dataflow graph when the parameters are
    * instantiated.
    */
  def dataflowGraphs: Array[Graph[Int, Int]]

  /** This graph defines how the dataflowGraphs can be changed between each other, assuming that the
    * paramters can change _only_ after an actor firing.
    */
  def configurations: Graph[Int, DefaultEdge]

  def balanceMatrices = dataflowGraphs.map(g => {
    val m = Array.fill(channelsSet.size)(Array.fill(actorsSet.size)(0))
    channelsSet.foreach(c => {
      actorsSet.foreach(a => {
        m(c)(a) = g.getAllEdges(a, c).stream.mapToInt(i => i).sum - g
          .getAllEdges(c, a)
          .stream
          .mapToInt(i => i)
          .sum
      })
    })
    m
  })

  def repetitionVectors =
    balanceMatrices.map(m => SDFUtils.getRepetitionVector(m, initialTokens))

  def isConsistent = repetitionVectors.forall(r => r.size == actorsSet.size)

  def liveSchedules = balanceMatrices.zipWithIndex.map((m, i) =>
    SDFUtils.getPASS(m, initialTokens, repetitionVectors(i))
  )

  def isLive = liveSchedules.forall(l => l.size == actorsSet.size)

  def pessimisticTokensPerChannel = channelsSet.map(c => {
    dataflowGraphs.zipWithIndex.flatMap((g, confIdx) => {
      g.incomingEdgesOf(c).stream().mapToInt(a => repetitionVectors(confIdx)(a) * balanceMatrices(confIdx)(c)(a) + initialTokens(c)).toScala(List)
    }).max
  })

  def stateSpace: Graph[Int, Int] = {
    // first, convert the arrays into a mathematical form
    val matrices = balanceMatrices.map(m => {
      val newM = DenseMatrix.zeros[Int](m.size, m(0).size)
      m.zipWithIndex.foreach((row, i) =>
        row.zipWithIndex.foreach((col, j) => {
          newM(i, j) = col
        })
      )
      newM
    })
    val g        = DefaultDirectedGraph[Int, Int](() => 0, () => 0, false)
    var explored = Array(DenseVector(initialTokens))
    // q is a queue of configuration and state
    var q = Queue((0, DenseVector(initialTokens)))
    //g.addVertex(initialTokens)
    while (!q.isEmpty) {
      val (conf, state) = q.dequeue
      val m             = matrices(conf)
      val newStates = actorsSet
        .map(a => {
          val v = DenseVector.zeros[Int](actorsSet.size)
          v(a) = 1
          (a, v)
        })
        .map((a, v) => (a, state + (m * v)))
        // all states must be non negative
        .filter((_, s) => s.forall(b => b >= 0))
        .filter((_, s) => !explored.contains(s))
      // we add the states to the space
      newStates.foreach((a, s) => {
        explored :+= s
        g.addEdge(explored.indexOf(state), explored.size - 1, a)
        // and product them with the possible next configurations
        configurations
          .outgoingEdgesOf(conf)
          .stream
          .map(e => configurations.getEdgeTarget(e))
          .forEach(newConf => q.enqueue((newConf, s)))
      })
    }
    g
  }
}
