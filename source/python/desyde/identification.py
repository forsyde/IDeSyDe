import desyde.preprocessing as pre

import ForSyDe.Model.Application as forapp
import ForSyDe.Model.Platform as forplat
import ForSyDe.Model.Refinement as forref

class IdentifiableProblem:
    '''
    Base class (interface) for all identifiable problems
    '''

    def is_proper_identification(self):
        '''
        Returns true if it completely covers a path from the application to the
        platform and false otherwise
        '''
        return False

    def identifies(self, comp):
        '''
        Returns true if solutions of this problem identifies component `comp`, as in giving
        it meaning after the solution step.
        '''
        return False

    def solve(self):
        '''
        Solve this problem.
        '''
        raise NotImplemented("Problem {0} has no means to be solved".format(self))

class DEComb_SporadicTasks(IdentifiableProblem):

    def __init__(self, 
                 comb_nodes, 
                 mandatory_task_nodes,
                 comb_task_edges):
        self.comb_nodes = comb_nodes
        self.mandatory_task_nodes = mandatory_task_nodes
        self.comb_task_edges = comb_task_edges

class Comb_Task_Scheduler(IdentifiableProblem):

    def __init__(self,
                 comb_nodes, 
                 mandatory_task_nodes,
                 mandatory_schedulers,
                 comb_task_edges,
                 task_sched_edges):
        self.comb_nodes = comb_nodes
        self.mandatory_task_nodes = mandatory_task_nodes
        self.mandatory_schedulers = mandatory_schedulers
        self.comb_task_edges = comb_task_edges
        self.task_sched_edges = task_sched_edges
        

class Comb_Task_Scheduler_Core(IdentifiableProblem):

    def __init__(self,
                 comb_nodes, 
                 mandatory_task_nodes,
                 mandatory_schedulers,
                 cores,
                 comb_task_edges,
                 task_sched_edges,
                 sched_core_edges):
        self.comb_nodes = comb_nodes
        self.mandatory_task_nodes = mandatory_task_nodes
        self.mandatory_schedulers = mandatory_schedulers
        self.cores = cores
        self.comb_task_edges = comb_task_edges
        self.task_sched_edges = task_sched_edges
        self.sched_core_edges = sched_core_edges

    def is_proper_identification(self):
        return True

class IdentificationRule:
    '''
    Base class (interface) for all identification rules to be implemented.
    Always assume the model passed is flat.
    '''

    def __init__(self, model):
        '''
        The model is the original model to be identified and identified are
        the current already identified subproblems
        '''
        self.model = model

    def _collect_sets(self, identified = set()):
        '''
        Collection routines are used to filter the model for any elements of interest
        '''
        raise NotImplemented("This rule did not implement its collection routine")

    def execute(self, identified = set()):
        '''
        Produce the new identified subproblem, or None if failed
        '''
        return None


class DECombToSporadicTaskRule(IdentificationRule):

    def __init__(self, model):
        '''
        The model is the original model to be identified and identified are
        the current already identified subproblems
        '''
        super().__init__(model)
        self.comb_nodes = set()
        self.mandatory_task_nodes = set()
        self.optional_task_nodes = set()
        self.comb_task_edges = set()

    def _collect_sets(self, identified = set()):
        for app in self.model.applications:
            for node in app.processNetlist.nodes:
                if isinstance(node.definition, forapp.Process):
                    if node.definition.constructor.moc == forapp.MoC.DE:
                        self.comb_nodes.add(node)
        if self.model.refinement:
            for bind in self.model.refinement.bindingsGraph.nodes:
                if bind.definition.category == forref.BindingCategory.SporadicTask:
                    self.mandatory_task_nodes.add(bind)
        for edge in self.model.refinement.bindingsGraph.edges:
            if edge.toNode in self.comb_nodes.union(self.mandatory_task_nodes) and\
                edge.fromNode in self.comb_nodes.union(self.mandatory_task_nodes):
                self.comb_task_edges.add(edge)

    def execute(self, identified = set()):
        self._collect_sets(identified)
        if self.mandatory_task_nodes and self.comb_nodes:
            return DEComb_SporadicTasks(
                comb_nodes = self.comb_nodes,
                mandatory_task_nodes = self.mandatory_task_nodes,
                comb_task_edges = self.comb_task_edges
            )
        else:
            return None

class SporadicTaskToFixedPrioritySchedulerRule(IdentificationRule):

    def __init__(self, model):
        '''
        The model is the original model to be identified and identified are
        the current already identified subproblems
        '''
        super().__init__(model)
        self.comb_nodes = set()
        self.mandatory_task_nodes = set()
        self.optional_task_nodes = set()
        self.mandatory_schedulers = set()
        self.optional_schedulers = set()
        self.comb_task_edges = set()
        self.task_sched_edges = set()

    def _collect_sets(self, identified = set()):
        combAndTasks = None
        for prob in identified:
            if isinstance(prob, DEComb_SporadicTasks):
                combAndTasks = prob
                continue
        if combAndTasks:
            self.comb_nodes = combAndTasks.comb_nodes
            self.mandatory_task_nodes = combAndTasks.mandatory_task_nodes
            self.comb_task_edges = combAndTasks.comb_task_edges
            if self.model.refinement:
                for bind in self.model.refinement.bindingsGraph.nodes:
                    if bind.definition.category ==\
                            forref.BindingCategory.PreemptiveFixedPriorityScheduler:
                        self.mandatory_schedulers.add(bind)
            for edge in self.model.refinement.bindingsGraph.edges:
                if edge.toNode in self.mandatory_task_nodes\
                        .union(self.mandatory_schedulers) and\
                    edge.fromNode in self.mandatory_task_nodes\
                        .union(self.mandatory_schedulers):
                    self.task_sched_edges.add(edge)

    def execute(self, identified = set()):
        self._collect_sets(identified)
        if self.mandatory_task_nodes and self.comb_nodes and\
                self.mandatory_schedulers:
            return Comb_Task_Scheduler(
                comb_nodes = self.comb_nodes,
                mandatory_task_nodes = self.mandatory_task_nodes,
                mandatory_schedulers = self.mandatory_schedulers,
                comb_task_edges = self.comb_task_edges,
                task_sched_edges = self.task_sched_edges
            )
        else:
            return None

class FixedPSchedulerToCoresRule(IdentificationRule):

    def __init__(self, model):
        '''
        The model is the original model to be identified and identified are
        the current already identified subproblems
        '''
        super().__init__(model)
        self.comb_nodes = set()
        self.mandatory_task_nodes = set()
        self.optional_task_nodes = set()
        self.mandatory_schedulers = set()
        self.optional_schedulers = set()
        self.cores = set()
        self.comb_task_edges = set()
        self.task_sched_edges = set()
        self.sched_core_edges = set()

    def _collect_sets(self, identified = set()):
        combAndTasksAndScheds = None
        for prob in identified:
            if isinstance(prob, Comb_Task_Scheduler):
                combAndTasksAndScheds = prob
                continue
        if combAndTasksAndScheds:
            self.comb_nodes = combAndTasksAndScheds.comb_nodes
            self.mandatory_task_nodes = combAndTasksAndScheds.mandatory_task_nodes
            self.mandatory_schedulers = combAndTasksAndScheds.mandatory_schedulers
            self.comb_task_edges = combAndTasksAndScheds.comb_task_edges
            self.task_sched_edges = combAndTasksAndScheds.task_sched_edges
            for p in self.model.platforms:
                for n in p.hwNetlist.nodes:
                    if isinstance(n.definition, forplat.Computation):
                        self.cores.add(n)
            for edge in self.model.refinement.bindingsGraph.edges:
                if edge.toNode in self.mandatory_schedulers\
                        .union(self.cores) and\
                    edge.fromNode in self.mandatory_schedulers\
                        .union(self.cores):
                    self.sched_core_edges.add(edge)

    def execute(self, identified = set()):
        self._collect_sets(identified)
        if self.mandatory_task_nodes and self.comb_nodes and\
                self.mandatory_schedulers and self.cores:
            return Comb_Task_Scheduler_Core(
                comb_nodes = self.comb_nodes,
                mandatory_task_nodes = self.mandatory_task_nodes,
                mandatory_schedulers = self.mandatory_schedulers,
                cores = self.cores,
                comb_task_edges = self.comb_task_edges,
                task_sched_edges = self.task_sched_edges,
                sched_core_edges = self.sched_core_edges
            )
        else:
            return None


class Identifier:
    '''
    Class to invoke all rules in a iterative way for a model.

    Everytime a new rule is added, don't forget to add it to the rule set.
    '''

    _rules_classes = [
        DECombToSporadicTaskRule,
        SporadicTaskToFixedPrioritySchedulerRule,
        FixedPSchedulerToCoresRule
    ]

    def __init__(self, model):
        self.model = model
        self.flat_model = pre.ModelFlattener(model).flatten()

    def identify(self):
        rules = set(rule(self.flat_model) for rule in self.__class__._rules_classes)
        identified = set()
        # for loop is necessary in favor of while because all rules
        # may fail
        for i in range(len(rules)):
            print(identified)
            for rule in rules.copy():
                problem = rule.execute(identified)
                if problem:
                    identified.add(problem)
                    rules.discard(rule)
        return set(i for i in identified if i.is_proper_identification())

