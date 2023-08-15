package idesyde.devicetree

import scala.collection.mutable.Buffer
import scala.collection.mutable

import org.virtuslab.yaml.*
import upickle.default._

enum DeviceTreeLink {
  def label: String

  case LabelOnly(val label: String) extends DeviceTreeLink
  case FullLink(val label: String, val target: DeviceTreeComponent) extends DeviceTreeLink
}

enum DeviceTreeProperty {
  case U32Property(val name: String, val prop: Int) extends DeviceTreeProperty
  case U64Property(val name: String, val prop: Long) extends DeviceTreeProperty
  case StringProperty(val name: String, val prop: String) extends DeviceTreeProperty
  case ReferenceProperty(
      val name: String,
      val prop: String,
      val ref: Option[DeviceTreeComponent] = None
  ) extends DeviceTreeProperty
  case StringListProperty(val name: String, val props: List[String]) extends DeviceTreeProperty
  case EncodedArray(val name: String, val props: List[Long]) extends DeviceTreeProperty

  def name: String
}

sealed trait DeviceTreeComponent {
  def nodeName: String
  def label: Option[String]
  def addr: Option[Int]
  def children: Iterable[DeviceTreeComponent]
  def connected: Iterable[DeviceTreeLink]
  def connect(dst: DeviceTreeLink): Unit
  def properties: Iterable[DeviceTreeProperty]
  def fullId: String = label.getOrElse(nodeName + label.map("@" + _).getOrElse(""))
  def prefixedFullId(prefix: String): String =
    label.getOrElse(prefix + "/" + nodeName + label.map("@" + _).getOrElse(""))

  def allChildren: Iterable[DeviceTreeComponent] = children ++ children.flatMap(_.allChildren)
  def propertiesNames                            = properties.map(_.name)
}

trait HasDefaultConnect extends DeviceTreeComponent {
  override def connected: Buffer[DeviceTreeLink]
  def connect(dst: DeviceTreeLink): Unit = {
    val idx = connected.indexWhere(conn => conn.label == dst.label)
    if (idx != -1) {
      connected(idx) = dst
    } else {
      connected.append(dst)
    }
  }
}

// final case class MultiRoot(
//     val roots: List[RootNode]
// ) extends DeviceTreeComponent {
//   def nodeName: String                     = "/"
//   def addr: Option[Int]                    = Option.empty
//   def label                                = Some("/")
//   def children: List[DeviceTreeComponent]  = roots
//   def properties: List[DeviceTreeProperty] = List.empty
// }

final case class RootNode(
    val children: List[DeviceTreeComponent],
    val properties: List[DeviceTreeProperty],
    val prefix: String = ""
) extends DeviceTreeComponent
    with HasDefaultConnect {
  def nodeName: String  = prefix + "/"
  def addr: Option[Int] = Option.empty
  def label             = Some("/")

  def connected: Buffer[DeviceTreeLink] = Buffer.empty

  def cpus =
    allChildren
      .flatMap(_ match {
        case cpu: CPUNode => Some(cpu)
        case _            => None
      })

  def memories = allChildren.flatMap(_ match {
    case a: MemoryNode => Some(a)
    case _             => None
  })

  def extraBuses = allChildren.flatMap(_ match {
    case bus: BusNode => Some(bus)
    case _            => None
  })

  def mainBusFrequency: Long = properties
    .flatMap(_ match {
      case DeviceTreeProperty.U64Property("bus-frequency", prop) => Some(prop)
      case DeviceTreeProperty.U32Property("bus-frequency", prop) => Some(prop.toLong)
      case _                                                     => None
    })
    .headOption
    .getOrElse(1L)

  def mainBusConcurrency: Int = properties
    .flatMap(_ match {
      case DeviceTreeProperty.U64Property("bus-concurrency", prop) => Some(prop.toInt)
      case DeviceTreeProperty.U32Property("bus-concurrency", prop) => Some(prop)
      case _                                                       => None
    })
    .headOption
    .getOrElse(1)

  def mainBusFlitSize: Long = properties
    .flatMap(_ match {
      case DeviceTreeProperty.U64Property("bus-flit", prop) => Some(prop)
      case DeviceTreeProperty.U32Property("bus-flit", prop) => Some(prop.toLong)
      case _                                                => None
    })
    .headOption
    .getOrElse(1L)

  def mainBusClockPerFlit: Long = properties
    .flatMap(_ match {
      case DeviceTreeProperty.U64Property("bus-clock-per-flit", prop) => Some(prop)
      case DeviceTreeProperty.U32Property("bus-clock-per-flit", prop) => Some(prop.toLong)
      case _                                                          => None
    })
    .headOption
    .getOrElse(1L)

  def linked: RootNode = {
    for (
      src <- allChildren;
      dst <- allChildren;
      if src.nodeName != dst.nodeName;
      (conn, i) <- src.connected.zipWithIndex;
      if conn.isInstanceOf[DeviceTreeLink.LabelOnly] && conn.label == dst.nodeName
    ) src.connect(DeviceTreeLink.FullLink(conn.label, dst))
    this
  }
}

final case class CPUNode(
    val nodeName: String,
    val addr: Option[Int],
    val label: Option[String],
    val children: List[DeviceTreeComponent],
    val properties: List[DeviceTreeProperty],
    var connected: Buffer[DeviceTreeLink]
) extends DeviceTreeComponent
    with HasDefaultConnect {

  def frequency: Long = properties
    .flatMap(prop =>
      prop match {
        case DeviceTreeProperty.U64Property("clock-frequency", prop) => Some(prop)
        case DeviceTreeProperty.U32Property("clock-frequency", prop) => Some(prop.toLong)
        case _                                                       => None
      }
    )
    .headOption
    .getOrElse(1L)

  def operationsProvided: Map[String, Map[String, Double]] = children
    .flatMap(_ match {
      case GenericNode(nodeName, addr, label, cs, properties, connected) =>
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
              Some(name -> props.head.toDouble / props.tail.head.toDouble)
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
    val children: List[DeviceTreeComponent],
    val properties: List[DeviceTreeProperty],
    var connected: Buffer[DeviceTreeLink]
) extends DeviceTreeComponent
    with HasDefaultConnect {

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
    val children: List[DeviceTreeComponent],
    val properties: List[DeviceTreeProperty],
    var connected: Buffer[DeviceTreeLink]
) extends DeviceTreeComponent
    with HasDefaultConnect {

  def busFrequency: Long = properties
    .flatMap(_ match {
      case DeviceTreeProperty.U64Property("bus-frequency", prop)   => Some(prop)
      case DeviceTreeProperty.U32Property("bus-frequency", prop)   => Some(prop.toLong)
      case DeviceTreeProperty.U64Property("clock-frequency", prop) => Some(prop)
      case DeviceTreeProperty.U32Property("clock-frequency", prop) => Some(prop.toLong)
      case _                                                       => None
    })
    .headOption
    .getOrElse(1L)

  def busConcurrency: Int = properties
    .flatMap(_ match {
      case DeviceTreeProperty.U64Property("bus-concurrency", prop) => Some(prop.toInt)
      case DeviceTreeProperty.U32Property("bus-concurrency", prop) => Some(prop)
      case _                                                       => None
    })
    .headOption
    .getOrElse(1)

  def busFlitSize: Long = properties
    .flatMap(_ match {
      case DeviceTreeProperty.U64Property("bus-flit", prop) => Some(prop)
      case DeviceTreeProperty.U32Property("bus-flit", prop) => Some(prop.toLong)
      case _                                                => None
    })
    .headOption
    .getOrElse(1L)

  def busClockPerFlit: Long = properties
    .flatMap(_ match {
      case DeviceTreeProperty.U64Property("bus-clock-per-flit", prop) => Some(prop)
      case DeviceTreeProperty.U32Property("bus-clock-per-flit", prop) => Some(prop.toLong)
      case _                                                          => None
    })
    .headOption
    .getOrElse(1L)

}

final case class GenericNode(
    val nodeName: String,
    val addr: Option[Int],
    val label: Option[String],
    val children: List[DeviceTreeComponent],
    val properties: List[DeviceTreeProperty],
    var connected: Buffer[DeviceTreeLink]
) extends DeviceTreeComponent
    with HasDefaultConnect {}

case class OSIsland(
    val name: String,
    val host: String,
    val affinity: List[String],
    val policy: List[String]
) derives YamlCodec,
      ReadWriter

case class OSDescription(
    val oses: Map[String, OSIsland]
) derives YamlCodec,
      ReadWriter {

  /** Return a new merged OSDescription
    *
    * @param o
    *   other OSDescription to be merged
    * @return
    *   the merge result, prioritizing left-most data (`this`)
    */
  def mergeLeft(right: OSDescription): OSDescription = OSDescription(
    oses ++ right.oses.filterNot((k, _) => oses.contains(k))
  )
}
