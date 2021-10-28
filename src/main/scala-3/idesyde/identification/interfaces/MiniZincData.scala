package idesyde.identification.interfaces

import scala.annotation.tailrec
import scala.collection.mutable.Queue
import scala.collection.mutable.Buffer

enum MiniZincData:
  case MznLiteral(val literal: Double | Int | Long | String | Boolean) extends MiniZincData
  case MznArray(val values: Seq[MiniZincData] | Buffer[MiniZincData]) extends MiniZincData
  case MznSet(val values: Set[MiniZincData])
  case MznEnum(val values: Seq[String]) extends MiniZincData

  def toJson(root: Boolean = false): ujson.Value =
    this match
      case MznLiteral(l) =>
        l match
          case d: Double  => ujson.Num(d)
          case i: Int     => ujson.Num(i)
          //TODO: fix the precision
          case ii: Long     => ujson.Num(ii.toDouble.round)
          case s: String  => ujson.Str(s)
          case b: Boolean => ujson.Bool(b)
        end match
      case MznArray(a) => 
        val arrEntries = a.map(_.toJson(false))
        ujson.Arr.from(arrEntries)
      case MznSet(s)   => 
        val setEntry = "set" -> s.map(_.toJson(false))
        ujson.Obj(setEntry)
      case MznEnum(e)  =>
        if (root)
          val entries = e.map(n => "e" -> n)
          ujson.Arr(entries)
        else
          ujson.Arr(e.indices)
    end match

end MiniZincData

object MiniZincData:

  val arrayRegex = "array(\\d+)d\\(((?:\\d+\\.\\.\\d+, )+)\\[(.+)\\]\\)".r

  def apply(x: Double | Int | Long | String | Boolean | Seq[?] | Set[?] | Buffer[?]) =
    x match {
      case arr: Seq[?] => MiniZincData.fromArray(arr)
      case buf: Buffer[?] => MiniZincData.fromArray(buf)
      case subset: Set[?] => MiniZincData.fromSet(subset)
      case l: (Double | Int | Long | String | Boolean | String) => MiniZincData.fromLiteral(l)
    }

  def fromLiteral(l: Double | Int | Long | String | Boolean): MiniZincData = MiniZincData.MznLiteral(l)

  def fromArray(a: Seq[?] | Buffer[?]): MiniZincData = MiniZincData.MznArray(a.map(e => {
    e match {
      case l: (Double | Int | Long | Boolean | String) => MiniZincData.fromLiteral(l)
      case arr: Seq[?] => MiniZincData.fromArray(arr)
      case subset: Set[?] => MiniZincData.fromSet(subset)
    }
  }))

  def fromSet(s: Set[?]): MiniZincData = MiniZincData.MznSet(s.map(e => {
    e match {
      case l: (Double | Int | Long | Boolean | String) => MiniZincData.fromLiteral(l)
      case arr: Seq[?] => MiniZincData.fromArray(arr)
      case subset: Set[?] => MiniZincData.fromSet(subset)
    }
  }))

  def fromResultString(s: String): MiniZincData =
    val stripped = s.strip
    if (stripped.startsWith("array")) then
      arrayRegex.findFirstMatchIn(stripped) match
        case Some(m) =>
          val dimensions = m.group(1).toInt
          val sizes = m.group(2).split(", ").map(s => 
            val innerSplit = s.split("..")
            val start = innerSplit.head.toInt
            val end = innerSplit.last.toInt
            end - start
          )
          val data = m.group(3).split(", ").map(s => 
            if (s.contains(".")) then MiniZincData(s.toDouble)
            else MiniZincData(s.toInt)
          )
          var topArray: Buffer[MiniZincData] = Buffer.empty
          var queue = Queue((0, 0, topArray))
          while (!queue.isEmpty)
            val (dim, offset, array) = queue.front
            if (dim < dimensions)
              val newBuffers: Array[Buffer[MiniZincData]] = Array.fill(sizes(dim))(Buffer.empty)
              for (i <- 0 until dim)
                queue.addOne((dim + 1, offset * sizes(dim) + i, newBuffers(i)))
              array.addAll(newBuffers.map(b => MiniZincData(b)))
            else
              array.addAll(
                data.slice(offset, offset + sizes(dim))
              )
          MiniZincData(topArray.toSeq)
        case _ => 
          MznArray(Seq.empty)
    else if (stripped.contains('.')) then
      MznLiteral(stripped.toDouble)
    else
      MznLiteral(stripped.toLong)  

end MiniZincData