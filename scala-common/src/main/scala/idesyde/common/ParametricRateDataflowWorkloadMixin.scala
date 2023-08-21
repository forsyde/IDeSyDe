package idesyde.common

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
import scala.collection.immutable.LazyList.cons
import scalax.collection.GraphTraversal.DepthFirst

/** This traits captures the ParametricRateDataflow base MoC from [1]. Then, we hope to be able to
  * use the same code for analysis across different dataflow MoCs, specially the simpler ones like
  * SDF and CSDF.
  *
  * [1] A. Bouakaz, P. Fradet, and A. Girault, “A survey of parametric dataflow models of
  * computation,” ACM Transactions on Design Automation of Electronic Systems, vol. 22, no. 2, 2017,
  * doi: 10.1145/2999539.
  */
trait ParametricRateDataflowWorkloadMixin {
  def actorsIdentifiers: scala.collection.immutable.Vector[String]
  def channelsIdentifiers: scala.collection.immutable.Vector[String]
  def channelNumInitialTokens: scala.collection.immutable.Vector[Int]
  def channelTokenSizes: scala.collection.immutable.Vector[Long]

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
  def dataflowGraphs: scala.collection.immutable.Vector[Iterable[(String, String, Int)]]

  /** This graph defines how the dataflowGraphs can be changed between each other, assuming that the
    * paramters can change _only_ after an actor firing.
    */
  def configurations: Iterable[(Int, Int, String)]

  def computeMessagesFromChannels = dataflowGraphs.zipWithIndex.map((df, dfi) => {
    var lumpedChannels = mutable
      .Map[(String, String), (Vector[String], Long, Int, Int, Int)]()
      .withDefaultValue(
        (
          Vector(),
          0L,
          0,
          0,
          0
        )
      )
    for ((c, ci) <- channelsIdentifiers.zipWithIndex) {
      val thisInitialTokens = channelNumInitialTokens(ci)
      for (
        (src, _, produced) <- df.filter((s, d, _) => d == c);
        (_, dst, consumed) <- df.filter((s, d, _) => s == c)
      ) {
        val srcIdx             = actorsIdentifiers.indexOf(src)
        val dstIdex            = actorsIdentifiers.indexOf(dst)
        val sent               = produced * channelTokenSizes(ci)
        val (cs, d, p, q, tok) = lumpedChannels((src, dst))
        lumpedChannels((src, dst)) = (
          cs :+ c,
          d + sent,
          p + produced,
          q + consumed,
          tok + thisInitialTokens
        )
      }
    }
    lumpedChannels.map((k, v) => (k._1, k._2, v._1, v._2, v._3, v._4, v._5)).toVector
  })

  /** This parameter counts the number of disjoint actor sets in the application model.def That is,
    * how many 'subapplications' are contained in this application. for for each configuration.
    *
    * This is important to correctly calculate repetition vectors in analytical methods.
    */
  def disjointComponents
      : scala.collection.immutable.Vector[scala.collection.IndexedSeq[Iterable[String]]] =
    dataflowGraphs.zipWithIndex.map((g, gx) => {
      // val nodes    = g.map((s, _, _) => s).toSet.union(g.map((_, t, _) => t).toSet)
      val edges    = computeMessagesFromChannels(gx).map((src, dst, _, _, _, _, _) => src ~> dst)
      val gGraphed = Graph.from(actorsIdentifiers, edges)
      gGraphed.componentTraverser().map(comp => comp.nodes.map(_.value)).toArray
    })

  def computeBalanceMatrices = dataflowGraphs.map(df => {
    val m = Array.fill(channelsIdentifiers.size)(Array.fill(actorsIdentifiers.size)(0))
    for ((src, dst, rate) <- df) {
      if (actorsIdentifiers.contains(src) && channelsIdentifiers.contains(dst)) {
        m(channelsIdentifiers.indexOf(dst))(actorsIdentifiers.indexOf(src)) =
          m(channelsIdentifiers.indexOf(dst))(actorsIdentifiers.indexOf(src)) + rate
      } else if (actorsIdentifiers.contains(dst) && channelsIdentifiers.contains(src)) {
        m(channelsIdentifiers.indexOf(src))(actorsIdentifiers.indexOf(dst)) =
          m(channelsIdentifiers.indexOf(src))(actorsIdentifiers.indexOf(dst)) - rate
      }
    }
    m.map(_.toVector).toVector
  })

  def computeRepetitionVectors
      : scala.collection.immutable.Vector[scala.collection.immutable.Vector[Int]] =
    dataflowGraphs.zipWithIndex.map((df, dfi) => {
      // we also take care of the extreme case where all actors in independent
      if (df.size == 0) {
        Vector.fill(actorsIdentifiers.size)(1)
      } else {
        val minus_one = Rational(-1)
        val nodes     = df.map((s, _, _) => s).toSet.union(df.map((_, t, _) => t).toSet)
        // val g         = Graph.from(nodes, df.map((src, dst, w) => src ~> dst))
        // first we build a compressed g with only the actors
        // with the fractional flows in a matrix
        var gEdges = Buffer[(String, String)]()
        val mat =
          Buffer.fill(channelsIdentifiers.size)(Buffer.fill(actorsIdentifiers.size)(Rational.zero))
        for (
          (src, c, prod)  <- df;
          (cc, dst, cons) <- df;
          if c == cc && channelsIdentifiers.contains(c) && actorsIdentifiers
            .contains(src) && actorsIdentifiers
            .contains(dst);
          cIdx   = channelsIdentifiers.indexOf(c);
          srcIdx = actorsIdentifiers.indexOf(src);
          dstIdx = actorsIdentifiers.indexOf(dst)
        ) {
          gEdges += (src -> dst)
          mat(cIdx)(srcIdx) = prod
          mat(cIdx)(dstIdx) = -cons
        }
        // val gActors    = Graph.from(actorsIdentifiers, gEdges.map((src, dst) => src ~ dst))
        val gActorsDir = Graph.from(actorsIdentifiers, gEdges.map((src, dst) => src ~> dst))
        // we iterate on the undirected version as to 'come back'
        // to vertex in feed-forward paths
        // val rates      = actorsIdentifiers.map(_ => minus_one).toBuffer
        val reducedMat  = computeReducedForm(mat)
        val components  = gActorsDir.componentTraverser()
        val nComponents = components.size
        // count the basis
        val nullBasis = computeRightNullBasisFromReduced(reducedMat)
        val nullRank  = nullBasis.size
        val matRank   = actorsIdentifiers.size - nullRank
        if (nullRank == nComponents) { // it can be consistent
          // val componentBasis = computeRightNullBasisFromReduced(reducedMat)
          // now reduce each base vector to its "integer" values and just compose then
          val normalized = nullBasis.map(rates => {
            val gcdV = rates.map(_.numerator.toLong).reduce((i1, i2) => spire.math.gcd(i1, i2))
            val lcmV = rates
              .map(_.denominator.toLong)
              .reduce((i1, i2) => spire.math.lcm(i1, i2))
            rates.map(_ * lcmV / gcdV).map(_.numerator.toInt).toVector
          })
          // return the sum of all normalized vectors
          normalized.reduce(_.zip(_).map(_ + _))
        } else { // it cannot be consistent
          scala.collection.immutable.Vector()
        }
      }
      // var consistent = true
      // for (
      //   component <- gActors.componentTraverser();
      //   gActorRoot = component.root;
      //   v <- gActors.outerNodeTraverser(gActorRoot).withKind(DepthFirst);
      //   if consistent;
      //   vIdx = actorsIdentifiers.indexOf(v)
      // ) {
      //   // if there is no rate on this vertex already, it must be a root, so we populate it
      //   if (rates(vIdx) == minus_one) {
      //     rates(vIdx) = 1
      //   }
      //   // populate neighbors based on 'next' which have no rate yet
      //   for (neigh <- gActorsDir.get(v).outNeighbors) {
      //     val neighIdx = actorsIdentifiers.indexOf(neigh.value)
      //     // if no rate exists in the other actor yet, we create it...
      //     if (rates(neighIdx) == minus_one) {
      //       // it depenends if the other is a consumer...
      //       rates(neighIdx) = rates(vIdx) * (gRates(vIdx)(neighIdx))
      //     }
      //     // ...otherwise we check if the graph is consistent
      //     else {
      //       println("check 1")
      //       consistent = consistent && rates(neighIdx) == rates(vIdx) / (gRates(vIdx)(neighIdx))
      //     }
      //   }
      //   // for (neigh <- gActorsDir.get(v).inNeighbors) {
      //   //   val neighIdx = actorsIdentifiers.indexOf(neigh.value)
      //   //   // if no rate exists in the other actor yet, we create it...
      //   //   if (rates(neighIdx) == minus_one) {
      //   //     // it depenends if the other is a producer...
      //   //     rates(neighIdx) = rates(vIdx) / (gRates(neighIdx)(vIdx))
      //   //   }
      //   //   // ...otherwise we check if the graph is consistent
      //   //   else {
      //   //     println("check 2")
      //   //     consistent = consistent && rates(neighIdx) / (gRates(neighIdx)(vIdx)) == rates(vIdx)
      //   //   }
      //   // }
      // }
      // otherwise simplify the repVec
      // val gcdV = rates.map(_.numerator.toLong).reduce((i1, i2) => spire.math.gcd(i1, i2))
      // val lcmV = rates
      //   .map(_.denominator.toLong)
      //   .reduce((i1, i2) => spire.math.lcm(i1, i2))
      // val res = rates.map(_ * lcmV / gcdV).map(_.numerator.toInt).toVector
      // println(res.toString())
      // res
    })
  // computeBalanceMatrices.zipWithIndex.map((m, ind) => SDFUtils.getRepetitionVector(m, initialTokens, numDisjointComponents(ind)))

  // def isConsistent = repetitionVectors.forall(r => r.size == actors.size)

  // def isLive = maximalParallelClustering.zipWithIndex.map((cluster, i) => !cluster.isEmpty)

  def pessimisticTokensPerChannel(
      repetitionVectors: scala.collection.immutable.Vector[scala.collection.immutable.Vector[Int]] =
        computeRepetitionVectors
  ): scala.collection.immutable.Vector[Int] = {
    if (repetitionVectors.exists(_.isEmpty)) {
      scala.collection.immutable.Vector.fill(channelsIdentifiers.size)(-1)
    } else {
      channelsIdentifiers.zipWithIndex.map((c, cIdx) => {
        dataflowGraphs.zipWithIndex
          .flatMap((g, confIdx) => {
            g.filter((s, t, r) => s == c)
              .map((s, t, r) => {
                -repetitionVectors(confIdx)(
                  actorsIdentifiers.indexOf(t)
                ) * r + channelNumInitialTokens(cIdx)
              })
          })
          .max
      })
    }
  }

  private def computeReducedForm(m: Buffer[Buffer[Rational]]): Buffer[Buffer[Rational]] = {
    val mat = m.map(_.clone()).clone()
    // println("original")
    // println(mat.mkString("\n"))
    val nrows    = mat.size
    val ncols    = mat.head.size
    var pivotRow = 0
    var pivotCol = 0
    while (pivotCol < ncols && pivotRow < nrows) {
      val allZeros = mat.drop(pivotRow).forall(cols => cols(pivotCol) == 0)
      if (!allZeros) {
        if (mat(pivotRow)(pivotCol) == 0) {
          val (nextBest, newPivotRow) =
            mat.zipWithIndex.drop(pivotRow).maxBy((row, i) => row(pivotCol).abs)
          val saved = mat(pivotRow)
          mat(pivotRow) = mat(newPivotRow)
          mat(newPivotRow) = saved
        }
        // this is chaned outside the loop due to mutability problems
        for (j <- pivotCol + 1 until ncols) {
          mat(pivotRow)(j) = mat(pivotRow)(j) / mat(pivotRow)(pivotCol)
        }
        mat(pivotRow)(pivotCol) = 1
        for (i <- 0 until pivotRow; j <- pivotCol + 1 until ncols) {
          mat(i)(j) = mat(i)(j) - (mat(pivotRow)(j) * mat(i)(pivotCol))
        }
        // this is changed before because fue to mutability it would be zero
        // mid computation in the previous loop
        for (i <- 0 until pivotRow) {
          mat(i)(pivotCol) = 0
        }
        for (i <- (pivotRow + 1) until nrows; j <- pivotCol + 1 until ncols) {
          mat(i)(j) = mat(i)(j) - (mat(pivotRow)(j) * mat(i)(pivotCol))
        }
        // same as before
        for (i <- (pivotRow + 1) until nrows) {
          mat(i)(pivotCol) = 0
        }
        pivotRow += 1
      }
      pivotCol += 1
    }
    // // now go up
    // for (k <- (ncols - 1) to 1 by -1) {
    //   val (_, pivot) =
    //     mat.zipWithIndex
    //       .filter((col, i) => col(k) != 0 && i <= k)
    //       .maxBy((col, i) => col(k).abs)
    //   if (pivot != k) {
    //     val saved = mat(k)
    //     mat(k) = mat(pivot)
    //     mat(pivot) = saved
    //   }
    //   if (mat(k)(k) != 0) {
    //     for (i <- (k - 1) to 0 by -1) {
    //       mat(i)(j) = mat(i)(j) - (mat(i)(j) / mat(k)(k) * mat(i)(k))
    //       mat(i) = mat(i).zip(mat(k)).map((a, b) => a - (b / mat(k)(k) * mat(i)(k)))
    //     }
    //   }
    // }
    mat
  }

  private def computeRightNullBasisFromReduced(
      reducedOriginal: Buffer[Buffer[Rational]]
  ): Set[Vector[Rational]] = {
    val reduced = reducedOriginal.map(_.clone()).clone()
    // println("reduced before")
    // println(reduced.mkString("\n"))
    val nrows = reduced.size
    val ncols = reduced.head.size
    // count all pivots by having 1 and then only 0s to the left
    val matRank   = reduced.count(_.count(_ != 0) > 1)
    val nullRank  = ncols - matRank
    val pivotCols = for (row <- 0 until matRank) yield reducedOriginal(row).indexOf(1)
    // crop matrix to requirement
    // permutation matrix according to pivots
    for (
      (pivotCol, j) <- pivotCols.zipWithIndex;
      if pivotCol != j;
      i <- 0 until nrows
    ) {
      val saved = reduced(i)(j)
      reduced(i)(j) = reduced(i)(pivotCol)
      reduced(i)(pivotCol) = saved
    }
    // now the matrix is in the form [I F; 0 0] so we can use the parts that are mandatory
    // that is, we make the matrix [-F^T I]^T before permutation
    val basis = for (col <- matRank until ncols) yield {
      val thisCol = for (row <- 0 until ncols) yield {
        if (row < matRank) {
          -reduced(row)(col)
        } else if (row == col) {
          Rational(1)
        } else {
          Rational(0)
        }
      }
      var unpermutatedCol = thisCol.toBuffer
      for (
        (pivotCol, j) <- pivotCols.zipWithIndex.reverse;
        if pivotCol != j
      ) {
        val saved = unpermutatedCol(j)
        unpermutatedCol(j) = unpermutatedCol(pivotCol)
        unpermutatedCol(pivotCol) = saved
      }
      unpermutatedCol.toVector
      // val f = for (row <- 0 until ncols) yield {
      //   if (pivotCols.contains(row)) { // this is basically the inverse of the permutation when it is missing
      //     if (pivotCols.indexOf(row) > matRank) {} else {
      //       -reduced(pivotCols.indexOf(row))(col)
      //     }
      //   } else {
      //     -reduced(row)(col)
      //   }
      // }
      // val iden = for (row <- matRank until ncols) yield {
      //   if (row == col) then Rational(1) else Rational(0)
      // }
      // f.toVector ++ iden.toVector
    }
    basis.toSet
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
  //     val newStates = actorsSet
  //       .map(a => {
  //         val v = DenseVector.zeros[Int](actorsSet.size)
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
  // def maximalParallelClustering: Vector[Vector[Vector[Int]]] =
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
