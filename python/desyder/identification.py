import abc
import importlib.resources as resources
from typing import Any, List

import minizinc
import numpy as np
import sympy

import desyder.math as mathutil
import desyder.sdf as sdfapi
from forsyde.io.python import ForSyDeModel


class MinizincAble(abc.ABC):

    @abc.abstractmethod
    def get_minizinc_model(self) -> minizinc.Model:
        return minizinc.Model()

class DecisionProblem(abc.ABC):

    """Docstring for DecisionProblem. """

    def __init__(self):
        """TODO: to be defined. """
        self.is_identified = False
        self.at_fix_point = False

    def identify(self,
                 model: ForSyDeModel,
                 identified: List["DecisionProblem"]
                 ) -> List["DecisionProblem"]:
        if not self.at_fix_point:
            self.is_identified = self._identify(model, identified)
        return self.is_identified

    @abc.abstractmethod
    def _identify(self,
                  model: ForSyDeModel,
                  identified: List["DecisionProblem"]
                  ) -> List["DecisionProblem"]:
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

    def _identify(self,
                  model: ForSyDeModel,
                  identified: List[DecisionProblem]) -> bool:
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
        # necessity check for SDF consistency (null space not empty)
        # later need to implement PASS maybe
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

    def get_minizinc_model(self):
        model = minizinc.Model()
        model_txt = resources.read_text(
            'desyder.minizinc',
            'sdf_linear_dmodel.mzn'
        )
        model.add_string(model_txt)
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


class SDFToSlots(DecisionProblem, MinizincAble):

    def __init__(self):
        DecisionProblem.__init__(self)
        self.slots = []

    def identify(self,
                 model: ForSyDeModel,
                 identified: List[DecisionProblem]) -> bool:
        """TODO: Docstring for identify.
        :returns: TODO

        """
        return False


class SDFToSlotMultiCore(DecisionProblem, MinizincAble):

    """Docstring for SDFToSlotMultiCore. """

    def __init__(self):
        """TODO: to be defined. """
        DecisionProblem.__init__(self)
        self.cores = set()
        self.fabrics = set()

    def identify(self, model: ForSyDeModel) -> bool:
        """TODO: Docstring for identify.
        :returns: TODO

        """
        return False


# class SporadicTaskToFixedPriorityScheduler(DecisionProblem):
# 
#     def __init__(self, model):
#         '''
#         The model is the original model to be identified and identified are
#         the current already identified subproblems
#         '''
#         super().__init__(model)
#         self.comb_nodes = set()
#         self.mandatory_task_nodes = set()
#         self.optional_task_nodes = set()
#         self.mandatory_schedulers = set()
#         self.optional_schedulers = set()
#         self.comb_task_edges = set()
#         self.task_sched_edges = set()
# 
#     def _collect_sets(self, identified = set()):
#         combAndTasks = None
#         for prob in identified:
#             if isinstance(prob, DEComb_SporadicTasks):
#                 combAndTasks = prob
#                 continue
#         if combAndTasks:
#             self.comb_nodes = combAndTasks.comb_nodes
#             self.mandatory_task_nodes = combAndTasks.mandatory_task_nodes
#             self.comb_task_edges = combAndTasks.comb_task_edges
#             if self.model.refinement:
#                 for bind in self.model.refinement.bindingsGraph.nodes:
#                     if bind.definition.category ==\
#                             forref.BindingCategory.PreemptiveFixedPriorityScheduler:
#                         self.mandatory_schedulers.add(bind)
#             for edge in self.model.refinement.bindingsGraph.edges:
#                 if edge.toNode in self.mandatory_task_nodes\
#                         .union(self.mandatory_schedulers) and\
#                     edge.fromNode in self.mandatory_task_nodes\
#                         .union(self.mandatory_schedulers):
#                     self.task_sched_edges.add(edge)
# 
#     def execute(self, identified = set()):
#         self._collect_sets(identified)
#         if self.mandatory_task_nodes and self.comb_nodes and\
#                 self.mandatory_schedulers:
#             return Comb_Task_Scheduler(
#                 comb_nodes = self.comb_nodes,
#                 mandatory_task_nodes = self.mandatory_task_nodes,
#                 mandatory_schedulers = self.mandatory_schedulers,
#                 comb_task_edges = self.comb_task_edges,
#                 task_sched_edges = self.task_sched_edges
#             )
#         else:
#             return None

# class FixedPSchedulerToCoresRule(DecisionProblem):
# 
#     def __init__(self, model):
#         '''
#         The model is the original model to be identified and identified are
#         the current already identified subproblems
#         '''
#         super().__init__(model)
#         self.comb_nodes = set()
#         self.mandatory_task_nodes = set()
#         self.optional_task_nodes = set()
#         self.mandatory_schedulers = set()
#         self.optional_schedulers = set()
#         self.cores = set()
#         self.comb_task_edges = set()
#         self.task_sched_edges = set()
#         self.sched_core_edges = set()
# 
#     def _collect_sets(self, identified = set()):
#         combAndTasksAndScheds = None
#         for prob in identified:
#             if isinstance(prob, Comb_Task_Scheduler):
#                 combAndTasksAndScheds = prob
#                 continue
#         if combAndTasksAndScheds:
#             self.comb_nodes = combAndTasksAndScheds.comb_nodes
#             self.mandatory_task_nodes = combAndTasksAndScheds.mandatory_task_nodes
#             self.mandatory_schedulers = combAndTasksAndScheds.mandatory_schedulers
#             self.comb_task_edges = combAndTasksAndScheds.comb_task_edges
#             self.task_sched_edges = combAndTasksAndScheds.task_sched_edges
#             for p in self.model.platforms:
#                 for n in p.hwNetlist.nodes:
#                     if isinstance(n.definition, forplat.Computation):
#                         self.cores.add(n)
#             for edge in self.model.refinement.bindingsGraph.edges:
#                 if edge.toNode in self.mandatory_schedulers\
#                         .union(self.cores) and\
#                     edge.fromNode in self.mandatory_schedulers\
#                         .union(self.cores):
#                     self.sched_core_edges.add(edge)
# 
#     def execute(self, identified = set()):
#         self._collect_sets(identified)
#         if self.mandatory_task_nodes and self.comb_nodes and\
#                 self.mandatory_schedulers and self.cores:
#             return Comb_Task_Scheduler_Core(
#                 comb_nodes = self.comb_nodes,
#                 mandatory_task_nodes = self.mandatory_task_nodes,
#                 mandatory_schedulers = self.mandatory_schedulers,
#                 cores = self.cores,
#                 comb_task_edges = self.comb_task_edges,
#                 task_sched_edges = self.task_sched_edges,
#                 sched_core_edges = self.sched_core_edges
#             )
#         else:
#             return None
# 
# 
# class Identifier:
#     '''
#     Class to invoke all rules in a iterative way for a model.
# 
#     Everytime a new rule is added, don't forget to add it to the rule set.
#     '''
# 
#     _rules_classes = [
#         DECombToSporadicTaskRule,
#         SporadicTaskToFixedPrioritySchedulerRule,
#         FixedPSchedulerToCoresRule
#     ]
# 
#     def __init__(self, model):
#         self.model = model
#         self.flat_model = pre.ModelFlattener(model).flatten()
# 
#     def identify(self):
#         rules = set(rule(self.flat_model) for rule in self.__class__._rules_classes)
#         identified = set()
#         # for loop is necessary in favor of while because all rules
#         # may fail
#         for i in range(len(rules)):
#             for rule in rules.copy():
#                 problem = rule.execute(identified)
#                 if problem:
#                     identified.add(problem)
#                     rules.discard(rule)
#         return set(i for i in identified if i.is_proper_identification())
# 
