package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import idesyde.utils.CoreUtils.wfor
import breeze.linalg._
import org.chocosolver.util.ESat

class SDFLikeTokensPropagator(
      val maxFiringsPerActor: Array[Int],
      val balanceMatrix: Array[Array[Int]],
      val initialTokens: Array[Int],
      val channelsCommunicate: Array[Array[Array[BoolVar]]],
      val firingsInSlots: Array[Array[Array[IntVar]]]
  ) extends Propagator[IntVar](
        firingsInSlots.flatten.flatten ++ channelsCommunicate.flatten.flatten,
        PropagatorPriority.TERNARY,
        false
      ) {

    private val numSchedulers = firingsInSlots.head.size
    private val numActors = firingsInSlots.size
    private val numSlots = firingsInSlots.head.head.size
    private val numChannels = channelsCommunicate.size

    private val channels      = (0 until numChannels).toArray
    private val actors        = (0 until numActors).toArray
    private val schedulers    = (0 until numSchedulers).toArray
    private val slots         = (0 until numSlots).toArray
    
    private val tokensBefore = DenseVector(initialTokens) +: Array.fill(numSlots - 1)(DenseVector.zeros[Int](numChannels))
  // def tokens(slot: Int) = if (slot < 0) then tokens0 else tokensBefore(slot)
    private val tokensAfter = Array.fill(numSlots)(DenseVector.zeros[Int](numChannels))
    private val mat = DenseMatrix(balanceMatrix:_*)
    private val consMat = mat.map(v => if (v < 0) then v else 0)
    private val prodMat = mat.map(v => if (v > 0) then v else 0)
    private val firingVector = Array.fill(numSlots)(DenseVector.zeros[Int](numActors))
    private val firingVectorPerCore = Array.fill(numSchedulers)(Array.fill(numSlots)(DenseVector.zeros[Int](numActors)))

    // val firingVector: Array[SparseVector[Int]] =
    //   slots.map(slot => SparseVector.zeros[Int](actors.size)).toArray

    val singleActorFire = actors
      .map(a => (0 to maxFiringsPerActor(a)).map(q => SDFChocoRecomputeMethods.mkSingleActorFire(numActors)(a)(q)).toArray)
      .toArray
    val horizonMaxFires = actors.map(a => schedulers.map(p => 0))

    var latestClosedSlot = 0
    var tailZeros        = 0

    def propagate(evtmask: Int): Unit = {
      var nextSlot = -1
      // var sendVecSaved = SparseVector.zeros[Int](channels.size)
      // println("propagate")
      SDFChocoRecomputeMethods.recomputeFiringVectors(firingsInSlots)(firingVector, firingVectorPerCore)
      SDFChocoRecomputeMethods.recomputeTokens(mat)(firingVector, firingVectorPerCore)(tokensBefore, tokensAfter)
      // latestClosedSlot = 0
      latestClosedSlot = SDFChocoRecomputeMethods.recomputeLowerestDecidedSlot(firingsInSlots)
      // println("latestClosedSlot " + latestClosedSlot)
      // if there is an open hole in the schedule. We avoid that.
      // if (latestClosedSlot < -1) fails()
      // slotsPrettyPrint()
      // for (s <- slots) {
      //   // instantiedCount = 0
      //   if (slotIsClosed(s)) {
      //     if (latestClosedSlot >= 0 && latestClosedSlot < s - 1) {
      //       println("skipped slot")
      //       fails() // there is an open hole in the schedule. We avoid that.
      //     } else if (latestClosedSlot >= s - 1) {
      //       latestClosedSlot = s
      //     }
      //   }
      // }
      // println(s"latestClosedSlot ${latestClosedSlot}")
      // now, we also avoid instantiations that are farther than the decision slot
      // tailZeros = 0
      // if (latestClosedSlot > -1) {
      wfor(1, it => it < numSlots - 1 && it <= latestClosedSlot, _ + 1) { s =>
        if (
          SDFChocoRecomputeMethods.slotIsClosed(firingsInSlots)(s) && max(firingVector(s - 1)) > 0 && max(firingVector(s)) == 0 && max(
            firingVector(s + 1)
          ) > 0
        ) {
          // tailZeros += 1
          println(s"zero ${s}")
          // schedulePrettyPrint()
          fails()
          // throw ContradictionException().set(this, firingsInSlots(a)(p)(s), s"${a}_${p}_${s} invalidated order")
        }
      }
      // first, we check if the model is still sane
      wfor(0, it => it <= latestClosedSlot && it < slots.size, _ + 1) { s =>
        // make sure that the tokens are not negative.
        if (min(consMat * firingVector(s) + tokensBefore(s)) < 0) {
          println("invalidated by token!")
          fails()
        }
      }
      // if (latestClosedSlot > -1) println(tokensBefore(latestClosedSlot).toString)
      // make sure that the latest slot only has admissible firings
      var maxlookAhead = 0
      if (latestClosedSlot < slots.size - 1) {
        // slotsPrettyPrint()
        nextSlot = latestClosedSlot + 1
        // go through the actors in the scheduling horizon and find which actors can be fired
        wfor(0, _ < numSchedulers, _ + 1) { p =>
          wfor(0, _ < numActors, _ + 1) { a =>
            // find the maximum firing possible
            horizonMaxFires(a)(p) = 0
            wfor(
              firingsInSlots(a)(p)(nextSlot).getUB(),
              _ > 0 && horizonMaxFires(a)(p) == 0,
              _ - 1
            ) { q =>
              if (min(consMat * singleActorFire(a)(q) + tokensBefore(nextSlot)) >= 0) {
                horizonMaxFires(a)(p) = q
              }
            }
          }
        }
        // println("horizonMaxFires " + horizonMaxFires.map(_.mkString("[", ", ", "]")).mkString("\n"))
        // now we find the maximum lookAhead
        wfor(0, _ < numSchedulers, _ + 1) { p =>
          var lookAhead = 0
          wfor(0, _ < numActors, _ + 1) { a =>
            if (horizonMaxFires(a)(p) > 0) then lookAhead += 1
          }
          if (lookAhead > maxlookAhead) then maxlookAhead = lookAhead
        }
        // println("maxlookAhead " + maxlookAhead)
        // and finally we limit we limit all firings to the scheduling horizon _and_
        // to the lookAhead, in order to enable serial execution as well as parallel
        wfor(0, _ < numSchedulers, _ + 1) { p =>
          wfor(0, _ < numActors, _ + 1) { a =>
            firingsInSlots(a)(p)(nextSlot).updateUpperBound(horizonMaxFires(a)(p), this)
            // wfor(nextSlot, it => it <= nextSlot + maxlookAhead && it < numSlots, _ + 1) {s =>

            // }
            // after the lookAhead, there should be no firings
            wfor(nextSlot + maxlookAhead, _ < numSlots, _ + 1) { s =>
              if (horizonMaxFires(a)(p) > 0) {
                firingsInSlots(a)(p)(s).updateUpperBound(0, this)
              }
            }
          }
        }
        // println("After propagation")
        // slotsPrettyPrint()
      }

    }

    def isEntailed(): ESat = {
      var allFired        = true
      var allCommunicated = true
      // println("checking entailment")
      // slotsPrettyPrint()
      SDFChocoRecomputeMethods.recomputeFiringVectors(firingsInSlots)(firingVector, firingVectorPerCore)
      SDFChocoRecomputeMethods.recomputeTokens(mat)(firingVector, firingVectorPerCore)(tokensBefore, tokensAfter)
      // if (latestClosedSlot > 0) {
      //   for (i <- slots.drop(1).take(latestClosedSlot)) {
      //     // println(i)
      //     for (
      //       dst <- schedulers; src <- schedulers;
      //       if src != dst && SDFChocoRecomputeMethods.slotAtSchedulerIsTaken(firingsInSlots)(dst)(i) && SDFChocoRecomputeMethods.slotAtSchedulerIsTaken(firingsInSlots)(src)(i - 1)
      //     ) {
      //       val diffAtSrc = diffTokenVec(i)(src)(src)
      //       val diffAtDst = diffTokenVec(i)(dst)(dst)
      //       // println(s"src vec from ${src} to ${src} at ${i}: ${diffAtSrc.toString()}")
      //       // println(s"dst vec from ${dst} to ${dst} at ${i}: ${diffAtDst.toString()}")
      //       for (c <- channels) {
      //         // should have communicated
      //         allCommunicated = channelsCommunicate(c)(src)(dst).isInstantiated() && allCommunicated
      //         if (
      //           diffAtDst(c) < 0 && diffAtSrc(c) >= -diffAtDst(c) && channelsCommunicate(c)(src)(
      //             dst
      //           ).getLB() < 1
      //         ) {
      //           // println(s" failing due to no comm of ${c} from ${src} to ${dst} at ${i}")
      //           return ESat.FALSE
      //         }
      //       }
      //     }
      //   }
      // }
      // work based first on the slots and update the timing whenever possible
      for (
        i <- slots
        // p <- schedulers
      ) {
        // make sure there are no holes in the schedule
        // if (any(firingVector(i)) && !any(firingVector(i - 1))) then return ESat.FALSE
        // actors.exists(a => firingVector(i).)
        if (min(consMat * firingVector(i) + tokensBefore(i)) < 0) {
          // println("wrong tokens for entailment")
          return ESat.FALSE
        }
        for (
          p <- schedulers;
          a <- actors
        ) {
          allFired = firingsInSlots(a)(p)(i).isInstantiated() && allFired
        }
      }
      // println("all is fired " + allFired)
      // println("all is comm " + allCommunicated)
      if (allFired && allCommunicated) then
        if (tokensAfter.last == tokensBefore.head) {
          // println("passed final tokens for entailment")
          return ESat.TRUE
        } else {
          // println("wrong final tokens for entailment")
          return ESat.FALSE
        }
      else return ESat.UNDEFINED
    }

    // def computeStateTime(): Unit = {
    //   stateTime.clear()
    //   stateTime += 0 -> initialTokens
    //   // we can do a simple for loop since we assume
    //   // that the schedules are SAS
    //   for (
    //     q <- slots;
    //     p <- schedulers;
    //     a <- actors;
    //     if firingsInSlots(a)(p)(q).isInstantiated();
    //     firings = firingsInSlots(a)(p)(q).getValue()
    //   ) {
    //     stateTime.foreach((t, b) => {
    //       // if ()
    //     })
    //   }
    // }
  }