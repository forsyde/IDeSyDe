import idesyde.utils.Logger
import idesyde.utils.SimpleStandardIOLogger

package object misc {
  given Logger = SimpleStandardIOLogger("DEBUG")
}
