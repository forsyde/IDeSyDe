package idesyde.utils

/** A simple trait to abstract possible logging implementations that IDeSyDe can have, for the sake
  * of keeping the core libraries lean.
  */
trait Logger {

  enum LoggingLevel(val repr: String) {
    case DEBUG extends LoggingLevel("DEBUG")
    case INFO extends LoggingLevel("INFO")
    case WARN extends LoggingLevel("WARN")
    case ERROR extends LoggingLevel("ERROR")
  }

  def debug(s: String): Unit = loggingLevel match {
    case LoggingLevel.DEBUG =>
      println("[debug  ] " + s)
    case _ =>
  }
  def info(s: String): Unit = loggingLevel match {
    case LoggingLevel.DEBUG | LoggingLevel.INFO =>
      println("[info   ] " + s)
    case _ =>
  }
  def warn(s: String): Unit = loggingLevel match {
    case LoggingLevel.DEBUG | LoggingLevel.INFO | LoggingLevel.WARN =>
      println("[warning] " + s)
    case _ =>
  }
  def error(s: String): Unit = loggingLevel match {
    case LoggingLevel.DEBUG | LoggingLevel.INFO | LoggingLevel.WARN | LoggingLevel.ERROR =>
      println("[error  ] " + s)
  }

  def setLoggingLevel(lvl: LoggingLevel): Logger = setLoggingLevel(lvl.repr)

  def setLoggingLevel(lvl: String): Logger

  def loggingLevel: LoggingLevel = LoggingLevel.valueOf(loggingLevelString)

  def loggingLevelString: String

}
