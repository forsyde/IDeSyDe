package idesyde.devicetree.identification

import idesyde.identification.DesignModel
import idesyde.devicetree.{DeviceTreeLink, DeviceTreeComponent, RootNode}

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

  override def elementRelationID(rel: (DeviceTreeComponent, DeviceTreeComponent)): String =
    rel.toString()

  lazy val elements: collection.Set[DeviceTreeComponent] =
    crossLinked.flatMap(_.allChildren).toSet

  lazy val elementRelations: collection.Set[(DeviceTreeComponent, DeviceTreeComponent)] =
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

  override def elementID(elem: DeviceTreeComponent): String = elem.nodeName
}
