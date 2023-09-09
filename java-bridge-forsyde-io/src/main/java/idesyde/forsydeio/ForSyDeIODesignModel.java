package idesyde.forsydeio;

import forsyde.io.bridge.sdf3.drivers.SDF3Driver;
import forsyde.io.core.EdgeInfo;
import forsyde.io.core.ModelHandler;
import forsyde.io.core.SystemGraph;
import forsyde.io.core.Vertex;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import forsyde.io.lib.LibForSyDeModelHandler;
import idesyde.core.DesignModel;
import idesyde.core.headers.DesignModelHeader;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ForSyDeIODesignModel(
        SystemGraph systemGraph) implements DesignModel {

    @Override
    public DesignModelHeader header() {
        return new DesignModelHeader(
                "ForSyDeIODesignModel",
                Stream.concat(systemGraph.vertexSet().stream().map(Vertex::getIdentifier),
                        systemGraph.edgeSet().stream().map(EdgeInfo::toIDString)).collect(Collectors.toSet()),
                Set.of());
    }

    @Override
    public Optional<String> bodyAsString() {
        try {
            return Optional.of(modelHandler.printModel(systemGraph, "fiodl"));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public static ModelHandler modelHandler = LibForSyDeModelHandler.registerLibForSyDe(new ModelHandler())
            .registerDriver(new SDF3Driver());
}
