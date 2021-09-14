package idesyde.identification.interfaces

import scala.annotation.tailrec

enum MiniZincData:
  case MznLiteral(val literal: Double | Int | Long | String | Boolean) extends MiniZincData
  case MznArray(val values: Seq[MiniZincData]) extends MiniZincData
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

  def apply(x: Double | Int | Long | String | Boolean | Seq[?] | Set[?]) =
    x match {
      case arr: Seq[?] => MiniZincData.fromArray(arr)
      case subset: Set[?] => MiniZincData.fromSet(subset)
      case l: (Double | Int | Long | String | Boolean | String) => MiniZincData.fromLiteral(l)
    }

  def fromLiteral(l: Double | Int | Long | String | Boolean): MiniZincData = MiniZincData.MznLiteral(l)

  def fromArray(a: Seq[?]): MiniZincData = MiniZincData.MznArray(a.map(e => {
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
end MiniZincData