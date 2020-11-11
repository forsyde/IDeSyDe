from typing import Optional, List, Dict

from forsyde.io.python import ForSyDeModel
import minizinc

class LogicalEngine(object):

    """Docstring for LogicalEngine. """

    def __init__(self):
        """TODO: to be defined. """
    
    def query(self, db: str, goal: str, var_list: List[str] = []): -> List[Dict[str, str]]:
        """TODO: Docstring for query.

        :db: TODO
        :goal: TODO
        :returns: TODO

        """
        return []

class Decideable(object):

    """Docstring for Decideable. """

    def __init__(self):
        """TODO: to be defined. """
        

class SolverInterface(object):

    """Docstring for SolvableInterface. """

    def __init__(self):
        """TODO: to be defined. """

    def can_solve(self, input_model: ForSyDeModel) -> bool:
        return False
        
    def solve(self, input_model: ForSyDeModel) -> Optional[ForSyDeModel]:
        """TODO: Docstring for solve.

        :f: TODO
        :returns: TODO

        """
        return None

class ConstraintProgramSolverInterface(SolverInterface):

    """Docstring for ConstraintProgramSolverInterface. """

    def __init__(self):
        """TODO: to be defined. """
        SolverInterface.__init__(self)

        
class MinizincSolverInterface(ConstraintProgramSolverInterface):

    """Docstring for MinizincSolverInterface. """

    def __init__(self):
        """TODO: to be defined. """
        ConstraintProgramSolverInterface.__init__(self)

