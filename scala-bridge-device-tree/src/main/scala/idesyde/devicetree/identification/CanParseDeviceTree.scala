package idesyde.devicetree.identification

import scala.util.parsing.combinator.RegexParsers
import scala.collection.mutable.Buffer
import idesyde.devicetree.{
  GenericNode,
  DeviceTreeComponent,
  RootNode,
  MemoryNode,
  DeviceTreeProperty,
  CPUNode
}

trait CanParseDeviceTree extends RegexParsers {
  override def skipWhitespace: Boolean = true

  def propertyNameToken: Parser[String]  = "[A-z][A-z0-9_,+-.]+".r ^^ { _.toString() }
  def propertyValueToken: Parser[String] = "[A-z0-9_,+-.]+".r ^^ { _.toString() }
  def property: Parser[DeviceTreeProperty] =
    propertyNameToken ~ "=" ~ (
      "\"" ~ propertyValueToken.* ~ "\"" | "<" ~ propertyValueToken.* ~ ">"
    ) ^^ { case propName ~ _ ~ propValue =>
      propValue match {
        case "\"" ~ value ~ "\"" => {
          val split = value.flatMap(_.split(","))
          if (split.size == 1) then DeviceTreeProperty.StringProperty(propName, split.head)
          else DeviceTreeProperty.StringListProperty(propName, split)
        }
        case "<" ~ value ~ ">" => {
          if (value.exists(_.contains("&"))) {
            DeviceTreeProperty.ReferenceProperty(
              propName,
              value.find(_.contains("&")).map(_.drop(1)).get,
              None
            )
          } else {
            val split = value
              .flatMap(_.split(","))
              .map(lString => parseLongSpecial(lString))
            if (split.size == 1) then DeviceTreeProperty.U64Property(propName, split.head)
            else DeviceTreeProperty.EncodedArray(propName, split)
          }
        }
        case _1 ~ _2 => DeviceTreeProperty.StringProperty(propName, s"ERROR AT ${propName}: UNKOWN DELIMITER FOR PROPERTY")
      }
    }

  def nodeNameString: Parser[String] = "[A-z][A-z0-9_,+-.]{0,30}".r ^^ { _.toString() }
  def nodeAddrString: Parser[String] = "[A-z0-9_,+-.]{1,31}".r ^^ { _.toString() }
  def labelString: Parser[String]    = "[A-z][A-z0-9_]{0,30}".r ^^ { _.toString() }
  def node: Parser[DeviceTreeComponent] =
    (labelString ~ ":").? ~ nodeAddrString ~ ("@" ~ nodeAddrString).? ~ "{" ~ (node | property).`*` ~ "}" ^^ {
      case nodeLabel ~ nodename ~ nodeaddr ~ _ ~ inner ~ _ => {
        val children = inner
          .filter(_.isInstanceOf[DeviceTreeComponent])
          .map(_.asInstanceOf[DeviceTreeComponent])
        val props =
          inner.filter(_.isInstanceOf[DeviceTreeProperty]).map(_.asInstanceOf[DeviceTreeProperty])
        var newNode =
          GenericNode(nodename, Option.empty, Option.empty, children, props, Buffer.empty)
        nodeaddr match {
          case Some("@" ~ nodeaddr) =>
            newNode = newNode.copy(addr = Some(parseLongSpecial(nodeaddr).toInt))
          case _ =>
        }
        nodeLabel match {
          case Some(l ~ ":") => newNode = newNode.copy(label = Some(l))
          case _             =>
        }
        // special cases
        if (nodename == "cpu") {
          CPUNode(
            newNode.nodeName,
            newNode.addr,
            newNode.label,
            newNode.children,
            newNode.properties,
            newNode.connected
          )
        } else if (nodename == "memory") {
          MemoryNode(
            newNode.nodeName,
            newNode.addr,
            newNode.label,
            newNode.children,
            newNode.properties,
            newNode.connected
          )
        } else {
          newNode
        }
      }
    }
  def root: Parser[RootNode] =
    (node | property).`*` ^^ { inner =>
      val children = inner
        .filter(_.isInstanceOf[DeviceTreeComponent])
        .map(_.asInstanceOf[DeviceTreeComponent])
      val props =
        inner.filter(_.isInstanceOf[DeviceTreeProperty]).map(_.asInstanceOf[DeviceTreeProperty])
      RootNode(children, props)
    }

  def parseDeviceTree(source: String) = parse(root, source)

  private def parseLongSpecial(s: String): Long = {
    if (s.contains("0x")) then {
      val sd  = s.drop(2)
      var idx = 0
      var i   = 0L
      while (idx < sd.size) {
        i = 16 * i + charToInt(sd.charAt(idx))
        idx += 1
      }
      i
    } else {
      s.toLong
    }
  }

  private def charToInt(c: Char): Int = {
    c match {
      case '1' => 1
      case '2' => 2
      case '3' => 3
      case '4' => 4
      case '5' => 5
      case '6' => 6
      case '7' => 7
      case '8' => 8
      case '9' => 9
      case _   => 0
    }
  }
}
