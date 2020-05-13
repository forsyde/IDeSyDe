import copy

from ForSyDe.Model.IO import ForSyDeIO
from ForSyDe.Model.Common import Graph
from ForSyDe.Model.Application import Application, Process, Constructor, Function,\
    Implementation
from ForSyDe.Model.Platform import Platform, Computation, Communication, Storage

class ModelFlattener:

    def __init__(self, model):
        self.model = model
        self.flattened = None

    def _copy_application(self, application, sufix):
        '''
        This is an internal function that copies an Application into a new element
        as much as possible so that it does not recurse into another nested applications
        and create another unnecessary copies. Needs to be paired with a bigger loop
        so that edges connecting to other elements can be properly scoped
        '''
        app_copy = Application()
        app_copy.processNetlist = Graph()
        # copy the nodes deeply unless they are nested elements
        app_copy.processNetlist.nodes = [
            copy.copy(n) if isinstance(n, Application) else \
            copy.deepcopy(n) for n in application.processNetlist.nodes
        ]
        # scope the ids for the application elements
        for n in app_copy.processNetlist.nodes:
            if n.identifier in map(lambda x: x.identifier, application.exported):
                app_copy.exported.append(n)
            n.identifier += sufix
            if not isinstance(n.definition, Application):
                n.definition.identifier += sufix
            if isinstance(n.definition, Process):
                app_copy.processes.append(n.definition)
            elif isinstance(n.definition, Constructor):
                app_copy.constructors.append(n.definition)
            elif isinstance(n.definition, Function):
                app_copy.functions.append(n.definition)
            elif isinstance(n.definition, Implementation):
                app_copy.implementations.append(n.definition)
        # simply copy all the edges
        app_copy.processNetlist.edges = [
            copy.copy(e) for e in application.processNetlist.edges
        ]
        # scope the edges ids and properly reference the newly created nodes in them
        for e in app_copy.processNetlist.edges:
            e.identifier += sufix
            for n in app_copy.processNetlist.nodes:
                if n.identifier == e.toNode.identifier + sufix:
                    e.toNode = n
                if n.identifier == e.fromNode.identifier + sufix:
                    e.fromNode = n
        # finally make sure that the copy id is set OK
        app_copy.processNetlist.identifier = application.processNetlist.identifier + sufix
        app_copy.identifier = application.identifier + sufix
        return app_copy

    def _copy_platform(self, platform, sufix):
        '''
        This is an internal function that copies a platform into a new element
        as much as possible so that it does not recurse into another nested platforms
        and create another unnecessary copies. Needs to be paired with a bigger loop
        so that edges connecting to other elements can be properly scoped
        '''
        plat_copy = Platform()
        plat_copy.hwNetlist = Graph()
        # copy the nodes deeply unless they are nested elements
        plat_copy.hwNetlist.nodes = [
            copy.copy(n) if isinstance(n, Platform) else \
            copy.deepcopy(n) for n in platform.hwNetlist.nodes
        ]
        # scope the ids for the platform elements
        for n in plat_copy.hwNetlist.nodes:
            if n.identifier in map(lambda x: x.identifier, platform.exported):
                plat_copy.exported.append(n)
            n.identifier += sufix
            if not isinstance(n.definition, Platform):
                n.definition.identifier += sufix
            if isinstance(n.definition, Computation):
                plat_copy.computators.append(n.definition)
            elif isinstance(n.definition, Communication):
                plat_copy.communicators.append(n.definition)
            elif isinstance(n.definition, Storage):
                plat_copy.storages.append(n.definition)
        plat_copy.hwNetlist.edges = [
            copy.copy(e) for e in platform.hwNetlist.edges
        ]
        # scope the edges ids and properly reference the newly created nodes in them
        for e in plat_copy.hwNetlist.edges:
            e.identifier += sufix
            for n in plat_copy.hwNetlist.nodes:
                if n.identifier == e.toNode.identifier + sufix:
                    e.toNode = n
                if n.identifier == e.fromNode.identifier + sufix:
                    e.fromNode = n
        # finally make sure that the copy id is set OK
        plat_copy.hwNetlist.identifier = platform.hwNetlist.identifier + sufix
        plat_copy.identifier = platform.identifier + sufix
        return plat_copy

    def _flatten_application(self):
        # count how many clones must be created for every application
        count = {m:1 for m in self.model.applications}
        for i in range(len(self.model.applications)):
            for p in self.model.applications:
                contrib = dict()
                for pp in self.model.applications:
                    if p != pp:
                        contrib[pp] = sum(1 for n in pp.processNetlist.nodes if n.definition == p)
                count[p] = sum(count[pp]*contrib[pp] for pp in contrib)
                # fall back to 1 in 0 since the application must be top
                count[p] = 1 if count[p] == 0 else count[p]
        # create the copy sets
        copies = {m : set() for m in self.model.applications} 
        # save the suffixes to be used later for rescuing the copies
        suffixes = dict()
        for m in self.model.applications:
            for i in range(count[m]):
                # copy enough information as to not mess with the orignal model
                plat_copy = self._copy_application(m, '--' + str(i))
                suffixes[plat_copy] = '--' + str(i)
                copies[m].add(plat_copy)
        for m in copies:
            # reset imports for for the copies
            for p in copies[m]:
                p.imported = []
            self.flattened.applications += list(copies[m])
        # singleton elements can be safely removed
        copies = {m : copies[m] for m in copies if len(copies[m]) > 1}
        # idx stands for index
        copies_idx = {m.identifier : m for m in copies}
        # connect the nodes to the flattened definitions
        for m in self.flattened.applications:
            for n in m.processNetlist.nodes:
                if n.definition.identifier in copies_idx:
                    copy_set = copies[copies_idx[n.definition.identifier]]
                    new_def = copy_set.pop()
                    m.imported.append(new_def)
                    n.definition = new_def
        # reconnect the edges with their exported nodes by using
        # the saved suffixes
        for m in self.flattened.applications:
            for e in m.processNetlist.edges:
                # this edge connects to an exported edge, thus must be
                # processed
                if e.toExported and isinstance(e.toNode.definition, Application):
                    # go through the exported nodes in the newly copied application
                    for n in e.toNode.definition.exported:
                        # find the one that macthes the right scoping
                        if e.toExported.identifier + \
                                suffixes[e.toNode.definition] ==\
                                n.identifier:
                            # assign it
                            e.toExported = n
                # same as before, but for 'from' nodes
                if e.fromExported and isinstance(e.fromNode.definition, Application):
                    for n in e.fromNode.definition.exported:
                        if e.fromExported.identifier + \
                                suffixes[e.fromNode.definition] ==\
                                n.identifier:
                            e.fromExported = n

    def _flatten_platform(self):
        # count how many clones must be created for every platform
        count = {m:1 for m in self.model.platforms}
        for i in range(len(self.model.platforms)):
            for p in self.model.platforms:
                contrib = dict()
                for pp in self.model.platforms:
                    if p != pp:
                        contrib[pp] = sum(1 for n in pp.hwNetlist.nodes if n.definition == p)
                count[p] = sum(count[pp]*contrib[pp] for pp in contrib)
                # fall back to 1 in 0 since the platform must be top
                count[p] = 1 if count[p] == 0 else count[p]
        # create the copy sets
        copies = {m : set() for m in self.model.platforms} 
        # save the suffixes to be used later for rescuing the copies
        suffixes = dict()
        for m in self.model.platforms:
            for i in range(count[m]):
                # copy enough information as to not mess with the orignal model
                plat_copy = self._copy_platform(m, '--' + str(i))
                suffixes[plat_copy] = '--' + str(i)
                copies[m].add(plat_copy)
        for m in copies:
            # reset imports for for the copies
            for p in copies[m]:
                p.imported = []
            self.flattened.platforms += list(copies[m])
        # singleton elements can be safely removed
        copies = {m : copies[m] for m in copies if len(copies[m]) > 1}
        # idx stands for index
        copies_idx = {m.identifier : m for m in copies}
        # connect the nodes to the flattened definitions
        for m in self.flattened.platforms:
            for n in m.hwNetlist.nodes:
                if n.definition.identifier in copies_idx:
                    copy_set = copies[copies_idx[n.definition.identifier]]
                    new_def = copy_set.pop()
                    m.imported.append(new_def)
                    n.definition = new_def
        # reconnect the edges with their exported nodes by using
        # the saved suffixes
        for m in self.flattened.platforms:
            for e in m.hwNetlist.edges:
                # this edge connects to an exported edge, thus must be
                # processed
                if e.toExported and isinstance(e.toNode.definition, Platform):
                    # go through the exported nodes in the newly copied platform
                    for n in e.toNode.definition.exported:
                        # find the one that macthes the right scoping
                        if e.toExported.identifier + \
                                suffixes[e.toNode.definition] ==\
                                n.identifier:
                            # assign it
                            e.toExported = n
                # same as before, but for 'from' nodes
                if e.fromExported and isinstance(e.fromNode.definition, Platform):
                    for n in e.fromNode.definition.exported:
                        if e.fromExported.identifier + \
                                suffixes[e.fromNode.definition] ==\
                                n.identifier:
                            e.fromExported = n

    def flatten(self):
        if not self.flattened:
            # creat the new flattened element
            self.flattened = ForSyDeIO()
            # use the helper functions for flattening unflattened nested elements
            self._flatten_platform()
            self._flatten_application()
        return self.flattened
