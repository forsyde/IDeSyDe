import desyde.preprocessing as pre

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
            for rule in rules.copy():
                problem = rule.execute(identified)
                if problem:
                    identified.add(problem)
                    rules.discard(rule)
        return set(i for i in identified if i.is_proper_identification())


