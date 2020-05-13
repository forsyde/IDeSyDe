import desyde.preprocessing as pre

class IdentifiableProblem:
    '''
    Base class (interface) for all identifiable problems
    '''

    def is_coverage(self):
        '''
        Returns true if it completely covers a path from the application to the
        platform and false otherwise
        '''
        return False

    def solve(self):
        '''
        Solve this problem.
        '''
        raise NotImplemented("Problem has no means to be solved")

class DECombToSporadicTaskProblem(IdentifiableProblem):

    def __init__(self, combs, tasks):
        self.combs = combs
        self.tasks = tasks


class IdentificationRule:
    '''
    Base class (interface) for all identification rules to be implemented.
    '''

    _registered = []


    def __init__(self, model, identified = []):
        '''
        The model is the original model to be identified and identified are
        the current already identified subproblems
        '''
        self.model = model
        self.model_expanded = pre.ModelFlattener(model).flatten()
        self.identified = identified

    def _collect_sets(self):
        '''
        Collection routines are used to filter the model for any elements of interest
        '''
        raise NotImplemented("This rule did not implement its collection routine")

    def execute(self):
        '''
        Produce the new identified subproblem, or None if failed
        '''
        return None


class DECombToSporadicTaskRule(IdentificationRule):

    def __init__(self, model, identified = []):
        '''
        The model is the original model to be identified and identified are
        the current already identified subproblems
        '''
        super().__init__(model, identified)
        self.comb_nodes = set()
        self.task_nodes = set()

    def _collect_sets(self):
        pass
