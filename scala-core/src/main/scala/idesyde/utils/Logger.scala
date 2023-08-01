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
      println("DEBUG   " + s)
    case _ =>
  }
  def info(s: String): Unit = loggingLevel match {
    case LoggingLevel.DEBUG | LoggingLevel.INFO =>
      println("INFO    " + s)
    case _ =>
  }
  def warn(s: String): Unit = loggingLevel match {
    case LoggingLevel.DEBUG | LoggingLevel.INFO | LoggingLevel.WARN =>
      println("WARNING " + s)
    case _ =>
  }
  def error(s: String): Unit = loggingLevel match {
    case LoggingLevel.DEBUG | LoggingLevel.INFO | LoggingLevel.WARN | LoggingLevel.ERROR =>
      println("ERROR   " + s)
  }

  def setLoggingLevel(lvl: LoggingLevel): Logger = setLoggingLevel(lvl.repr)

  def setLoggingLevel(lvl: String): Logger

  def loggingLevel: LoggingLevel = LoggingLevel.valueOf(loggingLevelString)

  def loggingLevelString: String

}
