package idesyde.devicetree.identification

enum DeviceTreeProperty {
  case U32Property(val name: String, val prop: Int) extends DeviceTreeProperty
  case U64Property(val name: String, val prop: Long) extends DeviceTreeProperty
  case StringProperty(val name: String, val prop: String) extends DeviceTreeProperty
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
    children.flatMap(_.children).filter(_.isInstanceOf[CPUNode]).map(_.asInstanceOf[CPUNode])
}

final case class CPUNode(
    val nodeName: String,
    val addr: Option[Int],
    val label: Option[String],
    val properties: List[DeviceTreeProperty]
) extends DeviceTreeDesignModel {
  def children: List[DeviceTreeDesignModel] = List()
}

final case class GenericNode(
    val nodeName: String,
    val addr: Option[Int],
    val label: Option[String],
    val children: List[DeviceTreeDesignModel],
    val properties: List[DeviceTreeProperty]
) extends DeviceTreeDesignModel
