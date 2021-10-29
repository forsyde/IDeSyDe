package idesyde.exploration.interfaces

import idesyde.identification.interfaces.MiniZincDecisionModel
import idesyde.exploration.Explorer
import forsyde.io.java.core.ForSyDeModel
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scala.sys.process._
import idesyde.identification.DecisionModel
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import idesyde.identification.interfaces.MiniZincData
import scala.collection.mutable.Buffer
import scala.collection.mutable

// import me.shadaj.scalapy.py

// val minizinc = py.module("minizinc")

trait SimpleMiniZincCPExplorer extends Explorer:

  def canExplore(decisionModel: DecisionModel): Boolean =
    decisionModel match
      // Just discard the minizinc output
      case m: MiniZincDecisionModel => 
        "minizinc".!(ProcessLogger(out => ())) == 1
      case _                        => false

  def explorationSolve(
      decisionModel: DecisionModel,
      minizincSolverName: String = "gecode",
      tempModelFileName: String = "idesyde-minizinc-model.mzn",
      tempDataFileName: String = "idesyde-minizinc-data.json",
      extraHeader: String = "",
      extraInstruction: String = "",
      callExtraFlags: List[String] = List.empty
  )(using ExecutionContext): LazyList[Map[String, MiniZincData]] =
    decisionModel match
      case m: MiniZincDecisionModel =>
        // val modelFile = Files.createTempFile("idesyde-minizinc-model", ".mzn")
        // val dataFile = Files.createTempFile("idesyde-minizinc-data", ".json")
        val modelPath = Paths.get(tempModelFileName)
        val dataPath  = Paths.get(tempDataFileName)
        val dataJson  = ujson.Obj.from(m.mznInputs.map((k, v) => k -> v.toJson(true)))
        val dataOutStream = Files.newOutputStream(
          dataPath,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
        Files.write(
          modelPath,
          (extraHeader + m.mznModel + extraInstruction).getBytes,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
        dataJson.writeBytesTo(dataOutStream, 2, false)
        dataOutStream.close
        // initiate solution procedure
        val command = 
          s"minizinc --solver ${minizincSolverName} " +
            "-a " +
            callExtraFlags.foldLeft("")((f1, f2) => f1 + " " + f2) + " " +
            s"${tempModelFileName} ${tempDataFileName}"
        command.lazyLines
          .filterNot(l => l.startsWith("%"))
          .scanLeft((Map.empty[String, MiniZincData], mutable.Map.empty[String, MiniZincData]))((b1, b2) =>
            // b1.head.addString(b2)
            val (_, accum) = b1
            if (b2.endsWith("----------") || b2.endsWith("==========")) then (accum.toMap, mutable.Map.empty)
            // b1 ++ List.empty
            else {
              val splitStr = b2.split("=")
              accum(splitStr.head.trim) = MiniZincData.fromResultString(splitStr.last.trim)
              b1
            }
          )
          // .map(sb => sb.toString)
          .filter((res, builder) => !res.isEmpty && builder.isEmpty)
          .map(m => m._1)
      case _ => LazyList.empty

// def