package idesyde.forsydeio;

import forsyde.io.bridge.sdf3.drivers.SDF3Driver;
import forsyde.io.core.EdgeInfo;
import forsyde.io.core.ModelHandler;
import forsyde.io.core.SystemGraph;
import forsyde.io.core.Vertex;
import forsyde.io.lib.hierarchy.ForSyDeHierarchy;
import forsyde.io.lib.LibForSyDeModelHandler;
import idesyde.core.DesignModel;
import idesyde.core.OpaqueDesignModel;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This design model wraps ForSyDe IO system graphs in order to make it usable
 * in the DSI conceptual framework
 * and IDeSyDe framework.
 * 
 * @param systemGraph the ForSyDe IO system graph wrapped.
 */
public record ForSyDeIODesignModel(
        SystemGraph systemGraph) implements DesignModel {

    @Override
    public Optional<String> asString() {
        try {
            return Optional.of(modelHandler.printModel(systemGraph, "fiodl"));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public String format() {
        return "fiodl";
    }

    @Override
    public Set<String> elements() {
        return systemGraph.vertexSet().stream().map(Vertex::getIdentifier).collect(Collectors.toSet());
        // return
        // Stream.concat(systemGraph.vertexSet().stream().map(Vertex::getIdentifier),
        // systemGraph().edgeSet().stream().map(EdgeInfo::toIDString)).collect(Collectors.toSet());
    }

    public static Optional<ForSyDeIODesignModel> fromOpaque(OpaqueDesignModel opaque) {
        if (modelHandler.canLoadModel(opaque.format())) {
            return opaque.asString().flatMap(body -> {
                try {
                    return Optional.of(modelHandler.readModel(body, opaque.format()));
                } catch (Exception e) {
                    e.printStackTrace();
                    return Optional.empty();
                }
            }).map(ForSyDeIODesignModel::new);
        } else {
            return Optional.empty();
        }
    }

    public static Optional<ForSyDeIODesignModel> tryFrom(DesignModel model) {
        if (model instanceof OpaqueDesignModel opaque) {
            return fromOpaque(opaque);
        } else if (model instanceof ForSyDeIODesignModel forSyDeIODesignModel) {
            return Optional.of(forSyDeIODesignModel);
        } else {
            return Optional.empty();
        }
    }

    public static ModelHandler modelHandler = LibForSyDeModelHandler.registerLibForSyDe(new ModelHandler())
            .registerDriver(new SDF3Driver());
}
