package idesyde.utils

class SimpleStandardIOLogger(val lvlString: String) extends Logger {

  def setLoggingLevel(lvl: String): Logger = SimpleStandardIOLogger(lvl)

  def loggingLevelString: String = lvlString
}
