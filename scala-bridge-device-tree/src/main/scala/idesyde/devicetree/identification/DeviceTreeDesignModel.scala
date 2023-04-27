package idesyde.devicetree.identification

import idesyde.core.DesignModel
import idesyde.devicetree.{DeviceTreeLink, DeviceTreeComponent, RootNode}
import idesyde.core.headers.LabelledArcWithPorts

final case class DeviceTreeDesignModel(
    val roots: List[RootNode]
) extends DesignModel {

  type ElementT         = DeviceTreeComponent
  type ElementRelationT = (DeviceTreeComponent, DeviceTreeComponent)

  lazy val crossLinked: List[RootNode] = {
    val locallyLinked = roots.map(_.linked)
    for (
      srcRoot <- roots;
      dstRoot <- roots;
      if srcRoot.prefix != dstRoot.prefix;
      src <- srcRoot.allChildren;
      dst <- dstRoot.allChildren;
      if src.nodeName != dst.nodeName;
      (conn, i) <- src.connected.zipWithIndex;
      if conn.isInstanceOf[DeviceTreeLink.LabelOnly] && conn.label == dst.nodeName
    ) src.connect(DeviceTreeLink.FullLink(conn.label, dst))
    locallyLinked
  }

  override def elementRelationID(
      rel: (DeviceTreeComponent, DeviceTreeComponent)
  ): LabelledArcWithPorts =
    LabelledArcWithPorts(rel._1.fullId, None, None, rel._2.fullId, None)

  lazy val elements: Set[DeviceTreeComponent] =
    crossLinked.flatMap(_.allChildren).toSet

  lazy val elementRelations: Set[(DeviceTreeComponent, DeviceTreeComponent)] =
    elements.flatMap(elem => elem.children.map(child => elem -> child)) ++ elements.flatMap(elem =>
      elem.connected.flatMap(link =>
        link match {
          case DeviceTreeLink.FullLink(label, other) => Some(elem -> other)
          case _                                     => None
        }
      )
    )

  override def merge(other: DesignModel): Option[DesignModel] = other match {
    case o: DeviceTreeDesignModel => {
      Some(
        DeviceTreeDesignModel(roots ++ o.roots)
      )
    }
    case _ => None
  }

  override def elementID(elem: DeviceTreeComponent): String = elem.label.getOrElse(
    crossLinked
      .find(root => root.allChildren.exists(_ == elem))
      .map(root => root.prefix + "/" + elem.fullId)
      .getOrElse(elem.fullId)
  )

  def uniqueIdentifier: String = "DeviceTreeDesignModel"
}
