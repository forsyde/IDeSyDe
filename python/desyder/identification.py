import abc
import importlib.resources as resources
from dataclasses import dataclass, field
from typing import List, Union, Tuple, Set, Optional, Dict

import numpy as np
import sympy
from minizinc import Model as MznModel
from minizinc import Instance as MznInstance
from minizinc import Result as MznResult
from forsyde.io.python import ForSyDeModel, Vertex

import desyder.math as mathutil
import desyder.sdf as sdfapi


class MinizincAble(abc.ABC):

    @abc.abstractmethod
    def populate_mzn_model(
            self,
            model: Union[MznModel, MznInstance]
    ) -> Union[MznModel, MznInstance]:
        return model

    @abc.abstractmethod
    def get_mzn_model_name(self) -> str:
        return ""

    @abc.abstractmethod
    def rebuild_forsyde_model(
        self,
        result: MznResult,
        original_model: ForSyDeModel
    ) -> ForSyDeModel:
        return original_model

    def build_mzn_model(self, mzn=MznModel()):
        model_txt = resources.read_text(
            'desyder.minizinc',
            self.get_mzn_model_name()
        )
        mzn.add_string(model_txt)
        self.populate_mzn_model(mzn)
        return mzn


class DecisionModel(abc.ABC):

    """
    Docstring for DecisionModel.
    """

    @abc.abstractclassmethod
    def identify(
            cls,
            model: ForSyDeModel,
            subproblems: List["DecisionModel"]
    ) -> Tuple[bool, Optional["DecisionModel"]]:
        """TODO: Docstring for identify.

        :db: TODO
        :returns: TODO

        """
        return (True, None)

    def __getitem__(self, key):
        '''
        A dict like interface is implemented for decision models
        for convenience, which recursively checks any other partial
        identifications that the model may have.
        '''
        key = str(key)
        if key in self.__dict__:
            return self.__dict__[key]
        for k in self.__dict__:

            o = self.__dict__[k]
            if isinstance(o, DecisionModel) and key in o:
                return o[key]
        return KeyError

    def __contains__(self, key):
        key = str(key)
        if key in self.__dict__:
            return True
        for k in self.__dict__:
            o = self.__dict__[k]
            if isinstance(o, DecisionModel) and key in o:
                return True
        return False


@dataclass
class SDFExecution(DecisionModel):

    """
    This decision model captures all SDF actors and channels in
    the design model and can only be identified if the 'Global' SDF
    application (the union of all disjoint SDFs) is consistent, i.e.
    it has a PASS.

    After identification this decision model provides the global
    SDF topology and the PASS with all elements included.
    """

    sdf_actors: List[Vertex] = field(default_factory=lambda: [])
    sdf_channels: List[Vertex] = field(default_factory=lambda: [])
    sdf_topology: np.ndarray = np.zeros((0, 0))
    sdf_repetition_vector: np.ndarray = np.zeros((0))
    sdf_pass: List[str] = field(default_factory=lambda: [])

    @classmethod
    def identify(cls, model, identified):
        sdf_actors = list(a for a in model.query_vertexes('sdf_actors'))
        sdf_channels = list(c for c in model.query_vertexes('sdf_channels'))
        sdf_topology = np.zeros(
            (len(sdf_channels), len(sdf_actors)),
            dtype=int
        )
        for row in model.query_view('sdf_topology'):
            a_index = next(idx for (idx, v) in enumerate(sdf_actors)
                           if v.identifier == row['actor_id'])
            c_index = next(idx for (idx, v) in enumerate(sdf_channels)
                           if v.identifier == row['channel_id'])
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


@dataclass
class SDFToOrders(DecisionModel, MinizincAble):

    sdf_exec_sub: SDFExecution
    orderings: List[Vertex] = field(default_factory=lambda: [])

    @classmethod
    def identify(cls, model, identified):
        sdf_exec_sub = next(
            (p for p in identified if isinstance(p, SDFExecution)),
            None)
        if sdf_exec_sub:
            orderings = list(o for o in model.query_vertexes('orderings'))
            if orderings:
                return (
                    True,
                    SDFToOrders(
                        sdf_exec_sub,
                        orderings
                    )
                )
            else:
                return (True, None)
        else:
            return (False, None)

    def populate_mzn_model(self, mzn):
        mzn['sdf_actors'] = range(1, len(self.sdf_actors)+1)
        mzn['sdf_channels'] = range(1, len(self.sdf_channels)+1)
        mzn['max_steps'] = len(self.sdf_pass)
        cloned_firings = np.array([
            self.repetition_vector.transpose()
            for i in range(len(self.sdf_channels))
        ])
        mzn['max_tokens'] = np.amax(
            cloned_firings * np.absolute(self.sdf_topology)
        )
        mzn['activations'] = self.repetition_vector
        mzn['static_orders'] = range(len(self.orderings))
        return mzn

    def get_mzn_model_name(self):
        return 'sdf_order_linear_dmodel.mzn'

    def rebuild_forsyde_model(self, results, original_model):
        print(results['send'])
        print(results['mapped'])
        return ForSyDeModel()


@dataclass
class SDFToMultiCore(DecisionModel, MinizincAble):

    sdf_orders_sub: SDFToOrders
    cpus: List[Vertex] = field(default_factory=lambda: [])
    bus: Optional[Vertex] = None

    @classmethod
    def identify(cls, model, identified):
        sdf_to_slot_sub = next(
            (p for p in identified if isinstance(p, SDFToOrders)),
            None)
        if sdf_to_slot_sub:
            cpus = list(
                c for c in model.query_vertexes('tdma_mpsoc_processing_units')
            )
            busses = [b for b in model.query_vertexes('tdma_mpsoc_bus')]
            if len(cpus) + len(busses) == len(sdf_to_slot_sub.orderings)\
                    and len(busses) == 1:
                return (
                    True,
                    SDFToMultiCore(
                        sdf_to_slot_sub,
                        cpus,
                        busses[0],
                    )
                )
            else:
                return (True, None)
        else:
            return (False, None)

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel.mzn"

    def populate_mzn_model(self, mzn):
        '''
        models pieces that are filled:

        % model parameters-
        int: max_bus_slots;
        int: max_tokens;
        int: max_steps;

        set of int: sdf_actors; % not flattened
        set of int: sdf_channels;
        set of int: processing_units;

        array[sdf_channels] of int: initial_tokens;
        array[sdf_actors] of int: activations;
        array[sdf_channels, sdf_actors] of int: sdf_topology;
        array[sdf_actors, processing_units] of int: wcet;
        % this numbers are 'per channel token'
        array[sdf_channels, processing_units, processing_units] of int: wcct;
        array[sdf_channels, processing_units] of int: send_overhead;
        array[sdf_channels, processing_units] of int: read_overhead;
        '''
        mzn['max_bus_slots'] = self.bus.properties['slots']
        mzn['max_steps'] = len(self['sdf_actors'])
        cloned_firings = np.array([
            self.repetition_vector.transpose()
            for i in range(len(self['sdf_channels']))
        ])
        max_tokens = np.amax(
            cloned_firings * np.absolute(self['sdf_topology'])
        )
        mzn['max_tokens'] = max_tokens
        mzn['sdf_actors'] = range(1, len(self['sdf_actors'])+1)
        mzn['sdf_channels'] = range(1, len(self['sdf_channels'])+1)
        mzn['processing_units'] = range(1, len(self.processing_units)+1)
        # TODO: The semantics of prefixes must be captures and put here!
        # for the moment, this always assumes zero starting tokens
        mzn['initial_tokens'] = [0 for c in self['sdf_channels']]
        mzn['activations'] = self['repetition_vector']
        mzn['sdf_topology'] = self['sdf_topology']
        # almost unitary assumption
        mzn['wcet'] = max_tokens * np.ones((
            len(self['sdf_actors']),
            len(self.processing_units)
        ))
        mzn['wcct'] = np.ones((
            len(self['sdf_channels']),
            len(self.processing_units),
            len(self.processing_units)
        ))
        mzn['send_overhead'] = np.zeros((
            len(self['sdf_channels']),
            len(self.processing_units)
        ))
        mzn['read_overhead'] = np.zeros((
            len(self['sdf_channels']),
            len(self.processing_units)
        ))
        return mzn

    def rebuild_forsyde_model(self, results, original_model):
        '''
        rebuild from the following variables:

        % variables
        array[sdf_channels, processing_units, steps0] of var 0..max_tokens: buffer;
        array[sdf_channels, processing_units, processing_units, bus_slots, steps0] of var 0..max_tokens: send;
        array[sdf_actors, processing_units, steps] of var 0..max(activations): mapped_actors;
        array[processing_units, steps0] of var int: cpu_time;
        array[steps0] of var int: bus_slots_used;
        '''
        return ForSyDeModel()


@dataclass
class SDFToMultiCoreCharacterized(DecisionModel, MinizincAble):

    sdf_to_mpsoc_sub: SDFToMultiCore
    wcet: np.ndarray = np.array((0, 0), dtype=int)
    wcct: np.ndarray = np.array((0, 0, 0), dtype=int)
    send_overhead: np.ndarray = np.array((0, 0), dtype=int)
    read_overhead: np.ndarray = np.array((0, 0), dtype=int)

    def __init__(self):
        pass

    def identify(model, idenfitied):
        return (True, None)

    def get_mzn_model_name(self):
        return "sdf_mpsoc_linear_dmodel"

    def populate_mzn_model(mzn):
        return mzn
