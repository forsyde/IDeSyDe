package idesyde.identification.minizinc.interfaces

import scala.annotation.tailrec
import scala.collection.mutable.Queue
import scala.collection.mutable.Buffer

enum MiniZincData:
  case MznLiteral(val literal: Double | Int | Long | String | Boolean) extends MiniZincData
  case MznArray(val values: Seq[MiniZincData]) extends MiniZincData
  case MznSet(val values: Set[MiniZincData]) extends MiniZincData
  case MznEnum(val values: Seq[String]) extends MiniZincData

  def toJson(root: Boolean = false): ujson.Value =
    this match
      case MznLiteral(l) =>
        l match
          case d: Double => ujson.Num(d)
          case i: Int    => ujson.Num(i)
          //TODO: fix the precision
          case ii: Long   => ujson.Num(ii.toDouble.round)
          case s: String  => ujson.Str(s)
          case b: Boolean => ujson.Bool(b)
        end match
      case MznArray(a) =>
        val arrEntries = a.map(_.toJson(false))
        ujson.Arr.from(arrEntries)
      case MznSet(s) =>
        val setEntry = "set" -> s.map(_.toJson(false))
        ujson.Obj(setEntry)
      case MznEnum(e) =>
        if (root)
          val entries = e.map(n => "e" -> n)
          ujson.Arr(entries)
        else ujson.Arr(e.indices)
    end match

end MiniZincData

object MiniZincData:

  val arrayRegex = "array(\\d+)d\\(((?:\\d+\\.\\.\\d+, )+)\\[(.+)\\]\\)".r

  def apply(e: Double | Int | Long | String | Boolean | Seq[?] | Set[?]) =
    if (e.isInstanceOf[Double | Int | Long | Boolean | String]) then
      MiniZincData.fromLiteral(e.asInstanceOf[Double | Int | Long | Boolean | String])
    else if (e.isInstanceOf[Seq[?]]) then MiniZincData.fromArray(e.asInstanceOf[Seq[?]])
    else if (e.isInstanceOf[Set[?]]) then MiniZincData.fromSet(e.asInstanceOf[Set[?]])
    else throw MatchError(e)

  def fromLiteral(l: Double | Int | Long | String | Boolean): MiniZincData =
    MiniZincData.MznLiteral(l)

  def fromArray(a: Seq[?]): MiniZincData = MiniZincData.MznArray(a.map(e => {
    if (e.isInstanceOf[Double | Int | Long | Boolean | String]) then
      MiniZincData.fromLiteral(e.asInstanceOf[Double | Int | Long | Boolean | String])
    else if (e.isInstanceOf[Seq[?]]) then MiniZincData.fromArray(e.asInstanceOf[Seq[?]])
    else if (e.isInstanceOf[List[?]]) then MiniZincData.fromArray(e.asInstanceOf[List[?]])
    else if (e.isInstanceOf[Set[?]]) then MiniZincData.fromSet(e.asInstanceOf[Set[?]])
    else throw MatchError(e)
  }))

  def fromSet(s: Set[?]): MiniZincData = MiniZincData.MznSet(s.map(e => {
    if (e.isInstanceOf[Double | Int | Long | Boolean | String]) then
      MiniZincData.fromLiteral(e.asInstanceOf[Double | Int | Long | Boolean | String])
    else if (e.isInstanceOf[Seq[?]]) then MiniZincData.fromArray(e.asInstanceOf[Seq[?]])
    else if (e.isInstanceOf[Set[?]]) then MiniZincData.fromSet(e.asInstanceOf[Set[?]])
    else throw MatchError(e)
  }))

  def fromResultString(s: String): MiniZincData =
    val stripped = s.trim.dropRight(1) // The 1 refers to the trailing ";" in every message
    if (stripped.startsWith("array")) then
      arrayRegex.findFirstMatchIn(stripped) match
        case Some(m) =>
          val dimensions = m.group(1).toInt
          val sizes = m
            .group(2)
            .split(",")
            .dropRight(1)
            .map(s =>
              val innerSplit = s.trim.split('.')
              val start      = innerSplit(0)
              val end        = innerSplit(innerSplit.length - 1)
              end.toInt - start.toInt
            )
          val data = m
            .group(3)
            .split(",")
            .map(s =>
              if (s.contains(".")) then MiniZincData(s.trim.toDouble)
              else MiniZincData(s.trim.toInt)
            )
          fromFlatArray(dimensions, sizes.toIndexedSeq, data.toIndexedSeq)
        case _ =>
          MznArray(Seq.empty)
    else if (stripped.contains('.')) then MznLiteral(stripped.toDouble)
    else MznLiteral(stripped.toLong)

  def fromFlatArray(
      dim: Int,
      sizes: Seq[Int],
      data: Seq[MiniZincData],
      offset: Int = 0
  ): MiniZincData.MznArray =
    if dim == 1 then MiniZincData.MznArray(data.slice(offset, offset + sizes.head))
    else
      MiniZincData.MznArray(
        for (i <- 0 until sizes.head)
          yield fromFlatArray(dim - 1, sizes.tail, data, sizes.head * offset + i)
      )

end MiniZincData
