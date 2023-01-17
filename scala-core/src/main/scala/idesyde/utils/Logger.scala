package idesyde.utils

/** A simple trait to abstract possible logging implementations that IDeSyDe can have, for the sake
  * of keeping the core libraries lean.
  */
trait Logger {

  def debug(s: String): Unit = println("[debug] " + s)
  def info(s: String): Unit  = println("[info ] " + s)
  def warn(s: String): Unit  = println("[warn ] " + s)
  def error(s: String): Unit = println("[error] " + s)
}
