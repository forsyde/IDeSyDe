import abc
import importlib.resources as resources
from typing import Any, List, Union, Tuple

import minizinc
import numpy as np
import sympy
from forsyde.io.python import ForSyDeModel

import desyder.math as mathutil
import desyder.sdf as sdfapi



class MinizincAble(abc.ABC):

    @abc.abstractmethod
    def populate_mzn_model(
            self,
            model: Union[minizinc.Model, minizinc.Instance]
            ) -> Union[minizinc.Model, minizinc.Instance]:
        return model

    @abc.abstractmethod
    def get_mzn_model_name(self) -> str:
        return ""

    def build_mzn_model(self):
        model_txt = resources.read_text(
            'desyder.minizinc',
            self.get_mzn_model_name()
        )
        model.add_string(model_txt)
        self.populate_mzn_model(model)
        return model


class DecisionProblem(abc.ABC):

    """Docstring for DecisionProblem. """

    def __init__(self):
        """TODO: to be defined. """
        self.is_identified = False
        self.at_fix_point = False

    @abc.abstractmethod
    def identify(
            self,
            model: ForSyDeModel,
            subproblems: List["DecisionProblem"]
            ) -> bool:
        """TODO: Docstring for identify.

        :db: TODO
        :returns: TODO

        """
        return self.is_identified


class SDFExecution(DecisionProblem, MinizincAble):

    def __init__(self):
        DecisionProblem.__init__(self)
        self.sdf_actors = []
        self.sdf_channels = []
        self.sdf_topology = np.zeros((0, 0))
        self.repetition_vector = []
        self.sdf_pass = []

    def identify(self,
                  model: ForSyDeModel,
                  subproblems: List[DecisionProblem]) -> bool:
        """TODO: Docstring for identify.
        :returns: TODO

        """
        self.sdf_actors = list(
            a['actor_id'] for a in model.query_view('sdf_actors')
        )
        self.sdf_channels = list(
            c['channel_id'] for c in model.query_view('sdf_channels')
        )
        self.sdf_topology = np.zeros(
            (len(self.sdf_channels), len(self.sdf_actors)),
            dtype=int
        )
        for row in model.query_view('sdf_topology'):
            a_index = self.sdf_actors.index(row['actor_id'])
            c_index = self.sdf_channels.index(row['channel_id'])
            self.sdf_topology[c_index, a_index] = int(row['tokens'])
        null_space = sympy.Matrix(self.sdf_topology).nullspace()
        if len(null_space) == 1:
            repetition_vector = mathutil.integralize_vector(null_space[0])
            repetition_vector = np.array(repetition_vector, dtype=int)
            initial_tokens = np.zeros((self.sdf_topology.shape[0], 1))
            schedule = sdfapi.get_PASS(self.sdf_topology,
                                       repetition_vector,
                                       initial_tokens)
            if schedule != []:
                self.repetition_vector = repetition_vector
                self.sdf_pass = [self.sdf_actors[idx] for idx in schedule]
                self.is_identified = True
        self.at_fix_point = True
        return self.is_identified

    def populate_mzn_model(self, model):
        model['sdf_actors'] = range(1, len(self.sdf_actors)+1)
        model['sdf_channels'] = range(1, len(self.sdf_channels)+1)
        model['max_steps'] = len(self.sdf_pass)
        cloned_firings = np.array([
            self.repetition_vector.transpose()
            for i in range(len(self.sdf_channels))
        ])
        model['max_tokens'] = np.amax(
            cloned_firings * np.absolute(self.sdf_topology)
        )
        model['activations'] = self.repetition_vector
        return model

    def get_mzn_model_name(self):
        return 'sdf_linear_dmodel.mzn'


class SDFToSlots(DecisionProblem, MinizincAble):

    def __init__(self):
        DecisionProblem.__init__(self)
        self.sdf_subproblem = None
        self.tasks = []

    def identify(self,
                 model: ForSyDeModel,
                 subproblems: List[DecisionProblem]) -> bool:
        """TODO: Docstring for identify.
        :returns: TODO

        """
        for prob in subproblems:
            if isinstance(prob, SDFExecution):
                self.sdf_subproblem = prob
                break
        if self.sdf_subproblem:
            self.tasks = list(
                r['vertex_id'] for r in model.query_view('tasks')
            )
            if self.sdf_subproblem.is_identified and len(self.tasks):
                self.is_identified = True
            if self.sdf_subproblem.at_fix_point:
                self.at_fix_point = True
        else:
            self.is_identified = False
            self.at_fix_point = True
        return self.is_identified

    def populate_mzn_model(self, model):
        self.sdf_subproblem.populate_mzn_model(model)
        model['static_orders'] = range(len(self.tasks))
        return model

    def get_mzn_model_name(self):
        return 'sdf_order_linear_dmodel.mzn'


class SDFToSlotMultiCore(DecisionProblem, MinizincAble):

    """Docstring for SDFToSlotMultiCore. """

    def __init__(self):
        """TODO: to be defined. """
        DecisionProblem.__init__(self)
        self.cores = set()
        self.fabrics = set()

    def identify(self, model, identified):
        """TODO: Docstring for identify.
        :returns: TODO

        """
        self.at_fix_point = True
        self.is_identified = True
        return self.is_identified

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def populate_mzn_model(self, model):
        return model
