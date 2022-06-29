package idesyde.identification

import java.util.concurrent.ThreadPoolExecutor
import idesyde.identification.DecisionModel

/**
 * The trait/interface for an identification rule in the design space identification methodology, as defined in [1].
 *  
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by Composable Design Space Identification," 
 *     2021 Design, Automation & Test in Europe Conference & Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 */
trait IdentificationRule[+M <: DecisionModel] {

  def identify[DesignModel](
      model: DesignModel,
      identified: Set[DecisionModel]
  ): (Boolean, Option[M])

}
