import abc
import importlib.resources as resources
from dataclasses import dataclass, field
from typing import Any, List, Union, Tuple, Set, Optional

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

    def build_mzn_model(self, model=minizinc.Model()):
        model_txt = resources.read_text(
            'desyder.minizinc',
            self.get_mzn_model_name()
        )
        model.add_string(model_txt)
        self.populate_mzn_model(model)
        return model


class DecisionProblem(abc.ABC):

    """Docstring for DecisionProblem. """

    @abc.abstractclassmethod
    def identify(
            cls,
            model: ForSyDeModel,
            subproblems: List["DecisionProblem"]
            ) -> Tuple[bool, Optional["DecisionProblem"]]:
        """TODO: Docstring for identify.

        :db: TODO
        :returns: TODO

        """
        return (True, None)


@dataclass
class SDFExecution(DecisionProblem, MinizincAble):

    sdf_actors: List[str] = field(default_factory=lambda: [])
    sdf_channels: List[str] = field(default_factory=lambda: [])
    sdf_topology: np.ndarray = np.zeros((0, 0))
    sdf_repetition_vector: List[int] = field(default_factory=lambda: [])
    sdf_pass: List[str] = field(default_factory=lambda: [])

    @classmethod
    def identify(cls, model, identified):
        """TODO: Docstring for identify.
        :returns: TODO

        """
        sdf_actors = list(
            a['vertex_id'] for a in model.query_view('sdf_actors')
        )
        sdf_channels = list(
            c['vertex_id'] for c in model.query_view('sdf_channels')
        )
        sdf_topology = np.zeros(
            (len(sdf_channels), len(sdf_actors)),
            dtype=int
        )
        for row in model.query_view('sdf_topology'):
            a_index = sdf_actors.index(row['actor_id'])
            c_index = sdf_channels.index(row['channel_id'])
            sdf_topology[c_index, a_index] = int(row['tokens'])
        null_space = sympy.Matrix(sdf_topology).nullspace()
        if len(null_space) == 1:
            repetition_vector = mathutil.integralize_vector(null_space[0])
            repetition_vector = np.array(repetition_vector, dtype=int)
            initial_tokens = np.zeros((sdf_topology.shape[0], 1))
            schedule = sdfapi.get_PASS(sdf_topology,
                                       repetition_vector,
                                       initial_tokens)
            if schedule != []:
                sdf_pass = [sdf_actors[idx] for idx in schedule]
                return (
                    True,
                    SDFExecution(
                        sdf_actors,
                        sdf_channels,
                        sdf_topology,
                        repetition_vector,
                        sdf_pass
                    )
                )
        else:
            return (False, None)

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


@dataclass
class SDFToSlots(DecisionProblem, MinizincAble):

    sdf_subproblem: SDFExecution
    orderings: List[str] = field(default_factory=lambda: [])

    @classmethod
    def identify(cls, model, identified):
        """TODO: Docstring for identify.
        :returns: TODO

        """
        sdf_subproblem = next(
            (p for p in identified if isinstance(p, SDFExecution)),
            None)
        if sdf_subproblem:
            orderings = list(
                r['vertex_id'] for r in model.query_view('orderings')
            )
            if orderings:
                return (
                    True,
                    SDFToSlots(
                        sdf_subproblem,
                        orderings
                    )
                )
            else:
                return (True, None)
        else:
            return (False, None)

    def populate_mzn_model(self, model):
        self.sdf_subproblem.populate_mzn_model(model)
        model['static_orders'] = range(len(self.orderings))
        return model

    def get_mzn_model_name(self):
        return 'sdf_order_linear_dmodel.mzn'


@dataclass
class SDFToSlotMultiCore(DecisionProblem, MinizincAble):

    sdf_to_slots_sub: SDFToSlots
    cpus: List[str] = field(default_factory=lambda: [])
    bus: Optional[str] = None
    bus_slots: Optional[int] = None

    @classmethod
    def identify(cls, model, identified):
        """TODO: Docstring for identify.
        :returns: TODO

        """
        sdf_to_slot_sub = next(
            (p for p in identified if isinstance(p, SDFToSlots)),
            None)
        if sdf_to_slot_sub:
            cpus = list(
                r['vertex_id'] for r in
                model.query_view('tdma_mpsoc_processing_units')
            )
            bus = next((
                r['vertex_id'] for r in
                model.query_view('tdma_mpsoc_bus')
            ), None)
            bus_slots = next((
                r['slots'] for r in
                model.query_view('tdma_mpsoc_bus_slots')
            ), None)
            if cpus and bus:
                return (
                    True,
                    SDFToSlotMultiCore(
                        sdf_to_slot_sub,
                        cpus,
                        bus,
                        bus_slots
                    )
                )
            else:
                return (True, None)
        else:
            return (False, None)

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def populate_mzn_model(self, model):
        self.sdf_to_slot_sub.populate_mzn_model(model)
        return model
