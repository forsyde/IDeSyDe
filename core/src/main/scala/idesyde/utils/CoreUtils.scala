package idesyde.utils

import scala.collection.mutable

object CoreUtils {

    def reachibilityClosure(matrix: Array[Array[Boolean]]): Array[Array[Boolean]] = {
        // necessary step to clone it
        val closure = matrix.map(row => row.clone()).toArray
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
        var connected = reachability.map(_ => false)
        for (i <- 0 until reachability.size - 1;
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
        var components = (0 until reachability.size).map(Set(_)).toArray
        for (i <- 0 until reachability.size - 1;
             j <- i + 1 until reachability.size;
             iSetIdx = components.indexWhere(c => c.contains(i));
             jSetIdx = components.indexWhere(c => c.contains(j))
             // if there exists any dominance path forward and back
             if reachability(i)(j) && reachability(j)(i)
        ) {
            components(iSetIdx) = components(iSetIdx) ++ components(jSetIdx)
            components = components.take(jSetIdx) ++ components.drop(jSetIdx + 1)
        }
        // now keep only the dominant ones
        val dominance = components.map(_ => true)
        for (
            i <- 0 until components.size - 1;
            j <- i + 1 until components.size
        ) {
            def ijDominance = components(i).exists(ci => components(j).exists(cj => reachability(ci)(cj)))
            def jiDominance = components(j).exists(cj => components(i).exists(ci => reachability(cj)(ci)))
            if (ijDominance) dominance(j) = false
            else if (jiDominance) dominance(i) = false
        }
        components.zipWithIndex.filter((c, i) => dominance(i)).flatMap((c, i) => c).toSet
    }

    /**
      * This is a 'for'-like lace which uses inlined while-laces
      * in order to have the utmost performance. This is to be used
      * in places where performance is far mor important than readability,
      * and not as a simple exchange for scala's native iteration methods
      * on collections, etc.
      * 
      * credits to https://august.nagro.us/scala-for-loop.html
      *
      * @param start the starting value
      * @param condition the condition for continued iteration
      * @param advance the code that gives a new iterated value
      * @param loopBody the body that is executed every iteration
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
  
}
