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
            closure(i)(j) = closure(i)(j) || (closure(i)(k) && closure(j)(k))
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
            components(componentIdx) += j
            connected(j) = true
        }
        components.map(_.toSet)
    }
  
}
