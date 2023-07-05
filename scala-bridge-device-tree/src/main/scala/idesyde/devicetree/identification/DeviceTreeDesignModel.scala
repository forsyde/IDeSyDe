package idesyde.devicetree.identification

import idesyde.core.DesignModel
import idesyde.devicetree.{DeviceTreeLink, DeviceTreeComponent, RootNode}
import idesyde.core.headers.LabelledArcWithPorts

final case class DeviceTreeDesignModel(
    val roots: List[RootNode]
) extends DesignModel {

  type ElementT = String

  override def elementID(elem: String): String = elem

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

  lazy val elements: Set[String] = {
    val nodes = crossLinked.flatMap(_.allChildren).toSet.map(_.fullId)
    // root connects all of its elements in a memory mapped way
    val rootLinks = for (
      root  <- crossLinked;
      child <- root.children
    ) yield s"${root.fullId}/devicebus->${child.fullId}"
    // now get connections that exist otherwise
    val moreLinks = for (
      src <- crossLinked.flatMap(_.allChildren);
      dst <- crossLinked.flatMap(_.allChildren);
      if src != dst;
      l <- src.connected;
      if l == dst
    ) yield s"${src.fullId}->${dst.fullId}"
    nodes ++ rootLinks.toSet ++ moreLinks.toSet
  }

  override def merge(other: DesignModel): Option[DesignModel] = other match {
    case o: DeviceTreeDesignModel => {
      Some(
        DeviceTreeDesignModel(roots ++ o.roots)
      )
    }
    case _ => None
  }

  // override def elementID(elem: DeviceTreeComponent): String = elem.label.getOrElse(
  //   crossLinked
  //     .find(root => root.allChildren.exists(_ == elem))
  //     .map(root => root.prefix + "/" + elem.fullId)
  //     .getOrElse(elem.fullId)
  // )

  def category: String = "DeviceTreeDesignModel"
}
