package idesyde.devicetree.identification

import scala.util.parsing.combinator.RegexParsers

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
          val split = value
            .flatMap(_.split(","))
            .map(lString => parseLongSpecial(lString))
          if (split.size == 1) then DeviceTreeProperty.U64Property(propName, split.head)
          else DeviceTreeProperty.EncodedArray(propName, split)
        }
      }
    }

  def nodeNameString: Parser[String] = "[A-z][A-z0-9_,+-.]{0,30}".r ^^ { _.toString() }
  def nodeAddrString: Parser[String] = "[A-z0-9_,+-.]{1,31}".r ^^ { _.toString() }
  def labelString: Parser[String]    = "[A-z][A-z0-9_]{0,30}".r ^^ { _.toString() }
  def node: Parser[DeviceTreeDesignModel] =
    (labelString ~ ":").? ~ nodeAddrString ~ ("@" ~ nodeAddrString).? ~ "{" ~ (node | property).`*` ~ "}" ^^ {
      case nodeLabel ~ nodename ~ nodeaddr ~ _ ~ inner ~ _ => {
        val children = inner
          .filter(_.isInstanceOf[DeviceTreeDesignModel])
          .map(_.asInstanceOf[DeviceTreeDesignModel])
        val props =
          inner.filter(_.isInstanceOf[DeviceTreeProperty]).map(_.asInstanceOf[DeviceTreeProperty])
        var newNode = GenericNode(nodename, Option.empty, Option.empty, children, props)
        nodeaddr match {
          case Some("@" ~ nodeaddr) =>
            newNode = newNode.copy(addr = Some(parseLongSpecial(nodeaddr).toInt))
          case _ =>
        }
        nodeLabel match {
          case Some(l ~ ":") => newNode = newNode.copy(label = Some(l))
          case _             =>
        }
        // special case
        if (nodename == "cpus") {
          newNode = newNode.copy(children = newNode.children.map(c => {
            CPUNode(c.nodeName, c.addr, c.label, c.properties)
          }))
        }
        newNode
      }
    }
  def root: Parser[RootNode] =
    (node | property).`*` ^^ { inner =>
      val children = inner
        .filter(_.isInstanceOf[DeviceTreeDesignModel])
        .map(_.asInstanceOf[DeviceTreeDesignModel])
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
