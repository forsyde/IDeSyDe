package idesyde.identification.models.workload

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
  def numActors: Int
  def numChannels: Int
  def initialTokens: Array[Int]

  /** An actor is self-concurrent if two or more instance can be executed at the same time
    *
    * As a rule of thumb, actors with "state" are not self-concurrent.
    */
  def isSelfConcurrent(actor: Int): Boolean

  /** The edges of the communication graph should have numbers describing how much data is
    * transferred from actors to channels.
    *
    * The array of graphs represent each possible dataflow graph when the parameters are
    * instantiated.
    */
  def dataflowGraphs: Array[Graph[Int, Int]]

  /** This graph defines how the dataflowGraphs can be changed between each other, assuming that the
    * paramters can change _only_ after an actor firing.
    */
  def configurations: Graph[Int, DefaultEdge]

  lazy val balanceMatrices = dataflowGraphs.map(g => {
    val m = Array.fill(numChannels)(Array.fill(numActors)(0))
    (numActors until (numActors + numChannels)).foreach(c => {
      (0 until numActors).foreach(a => {
        m(c)(a) = g.getAllEdges(a, c).stream.mapToInt(i => i).sum - g
          .getAllEdges(c, a)
          .stream
          .mapToInt(i => i)
          .sum
      })
    })
    m
  })

  lazy val repetitionVectors =
    balanceMatrices.map(m => SDFUtils.getRepetitionVector(m, initialTokens))

  lazy val isConsistent = repetitionVectors.forall(r => r.size == numActors)

  lazy val liveSchedules = balanceMatrices.zipWithIndex.map((m, i) =>
    SDFUtils.getPASS(m, initialTokens, repetitionVectors(i))
  )

  lazy val isLive = liveSchedules.forall(l => l.size == numActors)

  lazy val stateSpace: Graph[Int, Int] = {
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
      val newStates = (0 until numActors)
        .map(a => {
          val v = DenseVector.zeros[Int](numActors)
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
    // SparseIntDirectedGraph(
    //   explored.size,
    //   transitions.size,
    //   () =>
    //     transitions
    //       .map(e => Pair.of(e._1.asInstanceOf[Integer], e._2.asInstanceOf[Integer]))
    //       .asJavaSeqStream,
    //   IncomingEdgesSupport.LAZY_INCOMING_EDGES
    // ).asInstanceOf[Graph[Int, Int]] // the casting is required due to the Int vs Integer problem between java and scala
  }
}
