package idesyde.core

/**
  * This enumeration captures certain spects of a exploration solution to be
  * compared between pairs of [[idesyde.identification.DecisionModel]] and [[idesyde.exploration.Explorer]].
  * 
  * When there is a new criteria unknown to the distributed program, it becomes an "External", with an identifier.
  */
enum ExplorationCriteria(val identifier: String, val moreIsBetter: Boolean = false):
  case TimeUntilOptimality extends ExplorationCriteria("TimeUntilOptimality")
  case MemoryUntilOptimality extends ExplorationCriteria("MemoryUntilOptimality")
  case TimeUntilFeasibility extends ExplorationCriteria("TimeUntilFeasibility")
  case MemoryUntilFeasibility extends ExplorationCriteria("MemoryUntilFeasibility")
  case ExternalCriteria(externalIdentifier: String) extends ExplorationCriteria(externalIdentifier)
