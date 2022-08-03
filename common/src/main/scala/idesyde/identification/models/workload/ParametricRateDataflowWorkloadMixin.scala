package idesyde.identification.models.workload

import scala.jdk.CollectionConverters.*
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
import org.jgrapht.opt.graph.sparse.IncomingEdgesSupport
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.graph.AsSubgraph
import java.util.stream.Collectors
import org.jgrapht.graph.AsUndirectedGraph
import org.jgrapht.traverse.BreadthFirstIterator
import java.util.function.BiFunction
import org.apache.commons.math3.util.ArithmeticUtils

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
    * transferred from actors to channels. That is, both actors _and_ channels indexes are part of
    * the graph, for each configuration.
    *
    * The array of graphs represent each possible dataflow graph when the parameters are
    * instantiated.
    */
  def dataflowGraphs: Array[Graph[Int, DefaultEdge]]

  /** This graph defines how the dataflowGraphs can be changed between each other, assuming that the
    * paramters can change _only_ after an actor firing.
    */
  def configurations: Graph[Int, DefaultEdge]

  /** This parameter counts the number of disjoint actor sets in the application model.def That is,
    * how many 'subapplications' are contained in this application. for for each configuration.
    *
    * This is important to correctly calculate repetition vectors in analytical methods.
    */
  def numDisjointComponents: Array[Int] = dataflowGraphs.map(g => {
    ConnectivityInspector(AsUndirectedGraph(g)).connectedSets().size()
  })

  def balanceMatrices = dataflowGraphs.map(g => {
    val m = Array.fill(channelsSet.size)(Array.fill(actorsSet.size)(0))
    channelsSet.zipWithIndex.foreach((c, ci) => {
      actorsSet.zipWithIndex.foreach((a, ai) => {
        if (g.containsEdge(a, c)) then
          m(ci)(ai) += g.getAllEdges(a, c).stream.mapToInt(e => g.getEdgeWeight(e).toInt).sum
        else if (g.containsEdge(c, a))
          m(ci)(ai) -= g.getAllEdges(c, a).stream.mapToInt(e => g.getEdgeWeight(e).toInt).sum
        else
          m(ci)(ai) = 0
      })
    })
    // scribe.debug(m.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
    m
  })

  def repetitionVectors: Array[Array[Int]] = dataflowGraphs.map(g => {
    val bfs   = BreadthFirstIterator(g)
    var rates = (actorsSet ++ channelsSet).map(i => i -> BigFraction.MINUS_ONE).toMap
    while (bfs.hasNext()) {
      val v      = bfs.next()
      val vRate  = rates(v)
      val parent = Option(bfs.getParent(v))
      val possibleRate = parent
        .map(parv => {
          // from an actor to a channel
          if (channelsSet.contains(v))
            rates(parv)
              .multiply(
                g.getAllEdges(parv, v).stream().mapToInt(e => g.getEdgeWeight(e).toInt).sum()
              )
          else // from a channel to an actor
            rates(parv)
              .divide(
                g.getAllEdges(parv, v).stream().mapToInt(e => g.getEdgeWeight(e).toInt).sum()
              )
        })
        // if there is no parent, return 1
        .getOrElse(BigFraction.ONE)
      if (vRate.equals(BigFraction.MINUS_ONE)) {
        rates += v -> possibleRate
      } else if (!vRate.divide(possibleRate).equals(BigFraction.ONE)) { //otherwise the rates should balance out
        // termine early if this is not the case, because they are inconsistent
        return Array()
      }
    }
    // now we put the rates in a more mangeable format
    val gcd =
      rates.map((v, r) => r.getNumeratorAsLong).reduce((i1, i2) => ArithmeticUtils.gcd(i1, i2))
    val lcm = rates
      .map((v, r) => r.getDenominatorAsLong)
      .reduce((i1, i2) => ArithmeticUtils.lcm(i1, i2))
    actorsSet.map(a => rates(a).multiply(lcm).divide(gcd).getNumeratorAsInt()).toArray
  })

  def isConsistent = repetitionVectors.forall(r => r.size == actorsSet.size)

  def liveSchedules = balanceMatrices.zipWithIndex.map((m, i) =>
    SDFUtils.getPASS(m, initialTokens, repetitionVectors(i))
  )

  def isLive = liveSchedules.forall(l => l.size == actorsSet.size)

  def pessimisticTokensPerChannel = channelsSet.map(c => {
    dataflowGraphs.zipWithIndex
      .flatMap((g, confIdx) => {
        g.incomingEdgesOf(c)
          .stream()
          .map(e => g.getEdgeSource(e))
          .mapToInt(a =>
            repetitionVectors(confIdx)(a) * balanceMatrices(confIdx)(c)(a) + initialTokens(c)
          )
          .toScala(List)
      })
      .max
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
