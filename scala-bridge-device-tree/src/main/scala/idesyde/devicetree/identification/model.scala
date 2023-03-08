package idesyde.devicetree.identification

import spire.math.Rational

enum DeviceTreeProperty {
  case U32Property(val name: String, val prop: Int) extends DeviceTreeProperty
  case U64Property(val name: String, val prop: Long) extends DeviceTreeProperty
  case StringProperty(val name: String, val prop: String) extends DeviceTreeProperty
  case ReferenceProperty(
      val name: String,
      val prop: String,
      val ref: Option[DeviceTreeDesignModel] = None
  ) extends DeviceTreeProperty
  case StringListProperty(val name: String, val props: List[String]) extends DeviceTreeProperty
  case EncodedArray(val name: String, val props: List[Long]) extends DeviceTreeProperty

  def name: String
}

sealed trait DeviceTreeDesignModel {
  def nodeName: String
  def label: Option[String]
  def addr: Option[Int]
  def children: List[DeviceTreeDesignModel]
  def properties: List[DeviceTreeProperty]

  def propertiesNames = properties.map(_.name)
}

final case class RootNode(
    val children: List[DeviceTreeDesignModel],
    val properties: List[DeviceTreeProperty]
) extends DeviceTreeDesignModel {
  def nodeName: String  = "/"
  def addr: Option[Int] = Option.empty
  def label             = Some("/")

  def cpus =
    children
      .flatMap(_.children)
      .flatMap(_ match {
        case cpu: CPUNode => Some(cpu)
        case _            => None
      })

  def memories = children.flatMap(_ match {
    case a: MemoryNode => Some(a)
    case _             => None
  })
}

final case class CPUNode(
    val nodeName: String,
    val addr: Option[Int],
    val label: Option[String],
    val children: List[DeviceTreeDesignModel],
    val properties: List[DeviceTreeProperty]
) extends DeviceTreeDesignModel {

  def operationsProvided: Map[String, Map[String, Rational]] = children
    .flatMap(_ match {
      case GenericNode(nodeName, addr, label, cs, properties) =>
        nodeName match {
          case "operationsPerCycle" => cs
          case "ops-per-cycle"      => cs
          case "opsPerCycle"        => cs
          case _                    => List.empty
        }
      case _ => List.empty
    })
    .map(opNode =>
      opNode.nodeName -> opNode.properties
        .flatMap(prop =>
          prop match
            case DeviceTreeProperty.EncodedArray(name, props) =>
              Some(name -> Rational(props.head, props.tail.head))
            case _ => None
        )
        .toMap
    )
    .toMap

}

final case class MemoryNode(
    val nodeName: String,
    val addr: Option[Int],
    val label: Option[String],
    val children: List[DeviceTreeDesignModel],
    val properties: List[DeviceTreeProperty]
) extends DeviceTreeDesignModel {

  def memorySize: Long = properties
    .flatMap(_ match {
      case DeviceTreeProperty.EncodedArray("reg", props) =>
        props.sliding(2).map(v => v(1) - v(0))
      case _ => Iterator.empty
    })
    .sum
}

final case class BusNode(
    val nodeName: String,
    val addr: Option[Int],
    val label: Option[String],
    val children: List[DeviceTreeDesignModel],
    val properties: List[DeviceTreeProperty]
) extends DeviceTreeDesignModel {}

final case class GenericNode(
    val nodeName: String,
    val addr: Option[Int],
    val label: Option[String],
    val children: List[DeviceTreeDesignModel],
    val properties: List[DeviceTreeProperty]
) extends DeviceTreeDesignModel
