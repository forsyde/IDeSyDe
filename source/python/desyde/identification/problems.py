import importlib.resources as libres

import minizinc as mzn

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
        raise NotImplementedError("Problem {0} has no means to be solved".format(self))

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

    def solve(self):
        model_str = libres.read_text('desyde.zinc', 'comb_task_scheduler_core.mzn')
        model = mzn.Model()
        model.add_string(model_str)

        # fill in parameters
        model['runnables'] = self.comb_nodes
        model['mandatory_tasks'] = self.mandatory_task_nodes
        model['mandatory_schedulers'] = self.mandatory_schedulers
        model['cores'] = self.cores

        # generate post-processed sets
        admissible_runnable_partition = [set() for r in self.comb_nodes]
        for r in self.comb_nodes:
            admissible = set(t for t in self.mandatory_task_nodes
                             for e in self.comb_task_edges if
                             e.fromNode == r and e.toNode == t)
            print(admissible)
        model['admissible_runnable_partition'] = admissible_runnable_partition
        print(model_str)