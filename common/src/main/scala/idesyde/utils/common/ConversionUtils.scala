package idesyde.utils

import collection.JavaConverters.*
import java.util.stream.Collectors

object ConversionUtils {
  
  def javaToScala[K <: Object, V <: Object](elem: java.util.Map[K, V]): scala.collection.immutable.Map[K, V] = ???
      
}
