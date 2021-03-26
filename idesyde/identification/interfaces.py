import importlib.resources as resources
from dataclasses import dataclass
from typing import Union
from typing import Tuple
from typing import Set
from typing import List
from typing import Iterator
from typing import Optional
from typing import Dict
from typing import Iterable
from typing import Any

from forsyde.io.python.api import ForSyDeModel
from forsyde.io.python.core import Vertex
from forsyde.io.python.core import Edge
from minizinc import Model as MznModel
from minizinc import Instance as MznInstance
from minizinc import Result as MznResult


@dataclass
class DecisionModel(object):
    """Decision Models interface for the Design Space Identification procedure.

    Along with IdentificatioRules, DecisionModels is the most important interface
    for the identification activity within Design Space Exploration. It
    Characterizes 'subproblems' that are increasingly composoable and can
    stepwise be identified in the entry model which is assumed to be a ForSyDe IO
    model.

    Another important aspect of always implementing this interface
    (by subclassing it) is that it enables further pieces in the code
    to summon all DecisionModels, since they are all back-related to
    this interface. This strategy is used in the identification
    procedure to gather all available DecisionModel when a specific
    list is not given.
    """

    def __hash__(self):
        return hash((self.covered_vertexes(), self.covered_edges()))

    def short_name(self) -> str:
        '''Get the short name representation for the decision model

        If this method is not overriden in classes implementing this
        interface, then it will simply return the full name of the class.

        This short name exist mainly for debugging and informational purposes.
        '''
        return str(self.__class__.__name__)

    def compute_deduced_properties(self) -> None:
        '''Compute deducible properties for this decision model'''
        pass

    def covered_vertexes(self) -> Iterable[Vertex]:
        '''Get vertexes partially identified by the Decision Model.

        Returns:
            Iterable for the vertexes.
        '''
        raise NotImplementedError

    def covered_edges(self) -> Iterable[Edge]:
        '''Get edges partially identified by the Decision Model.

        Returns:
            Iterable for the edges.
        '''
        return []

    def covered_model(self) -> ForSyDeModel:
        '''Returns the covered ForSyDe Model.
        Returns:
            A copy of the vertexes and edges that this decision
            model partially identified.
        '''
        model = ForSyDeModel()
        for v in self.covered_vertexes():
            model.add_node(v, label=v.identifier)
        for e in self.covered_edges():
            model.add_edge(e.source_vertex, e.target_vertex, data=e)
        return model

    def dominates(self, other: "DecisionModel") -> bool:
        '''
        This function returns if one partial identification dominates
        the other. It also takes in consideration the explicit
        model domination set from 'self'.

        Args:
            other: the other decision model to be checked.

        Returns:
            True if 'self' dominates other. False otherwise.
        '''
        # other - self
        vertexes_other = set(other.covered_vertexes())
        vertexes_self = set(self.covered_vertexes())
        edges_other = set(other.covered_edges())
        edges_self = set(self.covered_edges())
        # other is fully contained in self and itersection is consistent
        # return all(v in other.covered_vertexes() for v in self.covered_vertexes())\
        #     and all(e in other.covered_edges() for e in self.covered_edges())\
        #     and not any(v in self.covered_vertexes() for v in other.covered_vertexes())\
        #     and not all(e in self.covered_edges() for e in other.covered_edges())
        return len(vertexes_self.difference(vertexes_other)) > 0\
            or len(edges_self.difference(edges_other)) > 0


class DirectDecisionModel(DecisionModel):
    '''DecisionModel interface that is solvable in python.

    As some decision problems can greatly benefit from fast pre-solving
    algorithms that run in polynomial time, this decision model provides
    such direct access, in pure python.

    Although not mandatory, and definitively not checked, any model
    DirectDecisionModel _should_ be complete (i.e. the solution is exact)
    in polynomial time.
    '''

    def execute(self) -> Any:
        return None


class MinizincableDecisionModel(DecisionModel):
    '''DecisionModel Interface that enables consumption by minizinc-based solvers.

    A refinement of the DecisionModel interface, models that satisfy this
    also must implment a few additional methods that enable automated exploration
    by minizinc-able solvers such as Gecode, Chuffed etc.
    '''

    def get_mzn_data(self) -> Dict[str, Any]:
        '''Build the input minizinc dictionary

        As the minizinc library has a dict-like interface but
        is immutable once a value is set, this pre-filling diciotnary
        is passed aroudn before feeding the minizinc interface for
        better code reuse.

        Returns:
            A dictionary containing all the data necessary to run
            the minizinc model attached to this decision model.
        '''
        return dict()

    def populate_mzn_model(self, model: Union[MznModel, MznInstance]) -> Union[MznModel, MznInstance]:
        '''Populate a minizinc model data dictionary

        Returns:
            Either an instance or a model with the data
            which then be solved by a minizinc solver.
        '''
        data_dict = self.get_mzn_data()
        for k in data_dict:
            model[k] = data_dict[k]
        return model

    def get_mzn_model_name(self) -> str:
        '''Get the number of the minizinc file for this class.

        Returns:
            the name of the file that represents this decision model.
            Although a method, it is expected that the string return
            is constant, i.e. static.
        '''
        return ""

    def rebuild_forsyde_model(self, result: MznResult) -> ForSyDeModel:
        '''Reconstruct a ForSyDeIO Model from the DecisionModel

        Returns:
            A subset of the original ForSyDe IO model that was used
            to create this DecisionModel, with the added decisions
            mainly in format of edges.
        '''
        return ForSyDeModel()

    def build_mzn_model(self, mzn: Union[MznModel, MznInstance] = MznModel()) -> Union[MznModel, MznInstance]:
        '''Builds the memory representaton of the minizinc model

        It uses the minizinc models packaged inside the python modules
        via the 'get_mzn_model_name' function.

        Returns:
            Minzinc model populated with the information that the
            decision model can fill.
        '''
        model_txt = resources.read_text('idesyde.minizinc', self.get_mzn_model_name())
        mzn.add_string(model_txt)
        self.populate_mzn_model(mzn)
        return mzn


class CompositeDecisionModel(DecisionModel):
    '''DecisionModel interface that enables composite problems to be described.

    This extension of the DecisionModel provides minimal functions that can be
    recursively used by an explorer so that data is propagated properly between
    parent and child decision models properly.

    Note that Hierarchically fedback decision models can also be generated by
    this class through basic usage of lazy model generation.

    Although arbitrary search procedures can be generated from this decision model
    via lazy generation, coding full search algorithms here is not intention.
    Gluing together much more efficient and faster solvers to do such search is
    the intention.
    '''

    def generate(self, current: Iterator[Tuple[DecisionModel, Any]]) -> Iterator[Tuple[DecisionModel, Any]]:
        '''Generate next batch of contained decision models, lazily.

        The iterator capture the flow of information by
        iterating through tuples of (model, input-data) which the explorers
        can consume for their exploration.

        The implementation of this function must obey at least these minimum
        laws:
            1. It should always handle the empty `current` case, which generates
            the 'root' case.
            2. the final results is produced by the tuple (None, result).
        '''
        return []


class IdentificationRule(object):
    """Class reprenseting a function that perform partial identification

    One of the major reasons why the functions are forced to be given
    as classes is to enable easier tracebility in other parts of the
    code. For instance, by injecting all the implementations (subclasses)
    of this interface (class) we obtain all the idenfitication rules
    available at a given run.
    """

    def identify(self, model: ForSyDeModel, identified: List[DecisionModel]) -> Tuple[bool, Optional[DecisionModel]]:
        """Perform identification procedure and obtain a new Decision Model

        This class function analyses the given design model (ForSyDe Model)
        and returns a decision model which partially idenfity it. It
        indicates when it can still be executed or not via a tuple.

        It should always be overriden.

        Arguments:
            model: Input ForSyDe model.
            identifier: Decision Models that have already been identified.

        Returns:
            A tuple where the first element indicates if any decision model
            belonging to 'cls' can still be identified and a decision model
            that partially identifies 'model' in the second element.
        """
        return (True, None)

    def short_name(self) -> str:
        '''Get the short name representation for the identification rule

        If this method is not overriden in classes implementing this
        interface, then it will simply return the full name of the class.

        This short name exist mainly hashing.
        '''
        return str(self.__class__.__name__)

    def __hash__(self):
        return hash(self.short_name())


