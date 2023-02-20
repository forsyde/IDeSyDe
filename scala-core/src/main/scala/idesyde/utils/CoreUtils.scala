package idesyde.utils

import scala.collection.mutable
import scala.collection.mutable.Buffer
import scala.collection.mutable.Queue

object CoreUtils {

  /** This method is an adaptation of Tarjan's algorithm to compute SCCs. The difference is that
    * dominance of design models is taken into consideration while effecting the original algorithm,
    * to speed-up the process.
    *
    * It is done with only scala std-libs to try to keep the core dependencies at a minimum.
    *
    * @param reachability
    *   a matrix of dominance between decision models
    * @return
    *   the mask of dominant decision models
    */
  def computeDominant(reachability: Array[Array[Boolean]]): Array[Int] = {
    val explored = Array.fill(reachability.size)(false)
    val lowlevel = Array.fill(reachability.size)(0)
    val dominant = Array.fill(reachability.size)(true)
    for (
      possibleDominant <- 0 until reachability.size;
      if !explored(possibleDominant)
    ) {
      strongConnect(
        reachability,
        possibleDominant,
        explored,
        lowlevel,
        List.empty
      )
    }
    for (
      fst <- 0 until reachability.size;
      snd <- 0 until reachability.size;
      if fst != snd && lowlevel(fst) != lowlevel(snd) && reachability(fst)(snd) && dominant(snd)
    ) {
      lowlevel.zipWithIndex
        .filter((l, i) => l == lowlevel(snd))
        .map((l, i) => i)
        .foreach(i => {
          dominant(i) = false
        })
    }
    (0 until reachability.size).filter(dominant(_)).toArray
  }

  protected def strongConnect(
      reachability: Array[Array[Boolean]],
      vertex: Int,
      explored: Array[Boolean],
      lowlevel: Array[Int],
      traversed: List[Int]
  ): Unit = {
    explored(vertex) = true
    lowlevel(vertex) = vertex
    for (
      sucessor <- 0 until reachability.size;
      if reachability(vertex)(sucessor)
    ) {
      // a new vertex never found before is here
      if (!explored(sucessor)) {
        strongConnect(
          reachability,
          sucessor,
          explored,
          lowlevel,
          traversed :+ vertex
        )
        lowlevel(vertex) = Math.min(lowlevel(vertex), lowlevel(sucessor))
      } else if (traversed.contains(sucessor)) { // we hit a strong component that is part of this dominant Vertex
        lowlevel(vertex) = Math.min(lowlevel(vertex), sucessor)
      }
    }
  }

  def reachibilityClosure(matrix: Array[Array[Boolean]]): Array[Array[Boolean]] = {
    // necessary step to clone it
    val closure  = matrix.map(row => row.clone()).toArray
    def numElems = matrix.length
    for (
      k <- 0 until numElems;
      i <- 0 until numElems - 1;
      j <- 1 until numElems
    ) {
      closure(i)(j) = closure(i)(j) || (closure(i)(k) && closure(k)(j))
    }
    closure
  }

  def computeSSCFromReachibility(reachability: Array[Array[Boolean]]): Array[Set[Int]] = {
    var components = Array(mutable.Set(0))
    var connected  = reachability.map(_ => false)
    for (
      i <- 0 until reachability.size - 1;
      j <- i + 1 until reachability.size;
      // if there exists any dominance path forward and back
      if reachability(i)(j) && reachability(j)(i) && !connected(j)
    ) {
      def componentIdx = components.indexWhere(c => c.contains(i))
      if (componentIdx < 0) then components :+= mutable.Set(j) else components(componentIdx) += j
      connected(j) = true
    }
    components.map(_.toSet)
  }

  def computeDominantFromReachability(reachability: Array[Array[Boolean]]): Set[Int] = {
    var masks = Array.fill(reachability.size)(0)
    var i     = 0
    var j     = 0
    while (i < masks.size - 1) {
      j = i + 1
      while (j < masks.size) {
        if (reachability(i)(j) && reachability(j)(i)) {
          masks(j) = masks(i)
        } else if (reachability(i)(j) && !reachability(j)(i)) {
          masks(j) = masks(i) + 1
        }
        j += 1
      }
      i += 1
    }
    // now keep only the dominant ones
    val dominantMask = masks.min
    // for (
    //     i <- 0 until components.size - 1;
    //     j <- i + 1 until components.size
    // ) {
    //     def ijDominance = components(i).exists(ci => components(j).exists(cj => reachability(ci)(cj)))
    //     def jiDominance = components(j).exists(cj => components(i).exists(ci => reachability(cj)(ci)))
    //     if (ijDominance) dominance(j) = false
    //     else if (jiDominance) dominance(i) = false
    // }
    masks.zipWithIndex.filter((c, i) => c <= dominantMask).map((c, i) => i).toSet
    // components.zipWithIndex.filter((c, i) => dominance(i)).flatMap((c, i) => c).toSet
  }

  /** This is a 'for'-like lace which uses inlined while-laces in order to have the utmost
    * performance. This is to be used in places where performance is far mor important than
    * readability, and not as a simple exchange for scala's native iteration methods on collections,
    * etc.
    *
    * credits to https://august.nagro.us/scala-for-loop.html
    *
    * @param start
    *   the starting value
    * @param condition
    *   the condition for continued iteration
    * @param advance
    *   the code that gives a new iterated value
    * @param loopBody
    *   the body that is executed every iteration
    */
  inline def wfor[A](
      inline start: A,
      inline condition: A => Boolean,
      inline advance: A => A
  )(inline loopBody: A => Any): Unit =
    var a = start
    while condition(a) do
      loopBody(a)
      a = advance(a)

  /** A simple function to get the ceil of two longs since the standard library only provides a
    * reliable function for floor ([[Math.floorDiv]]).
    *
    * @param numerand
    *   @param dividend
    * @return
    *   the ceil of the numerand and the dividend
    */
  def ceil(numerand: Long, dividend: Long): Long = {
    if (numerand % dividend > 0) then (numerand / dividend) + 1
    else numerand / dividend
  }
}
