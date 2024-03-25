package general;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import idesyde.common.AperiodicAsynchronousDataflowToPartitionedTiledMulticore;
import idesyde.core.DecisionModel;
import idesyde.core.Explorer;
import idesyde.metaheuristics.JeneticsExplorer;

class GeneralDSETests {

    final ObjectMapper objectMapper = DecisionModel.objectMapper;

    @Test
    void testAADTPMExplorer() throws StreamReadException, DatabindException, IOException {
        final Explorer.Configuration config = new Explorer.Configuration();
        config.maximumSolutions = 1L;
        config.targetObjectives = Set.of("invThroughput(CS_0)");
        final JeneticsExplorer explorer = new JeneticsExplorer();
        InputStream is = getClass().getResourceAsStream("body_final_19_AperiodicAsynchronousDataflowToPartitionedTiledMulticore_Orchestratror.json");
        AperiodicAsynchronousDataflowToPartitionedTiledMulticore aadtpm = objectMapper.readValue(is, AperiodicAsynchronousDataflowToPartitionedTiledMulticore.class);
        explorer.explore(aadtpm, Set.of(), config).forEach(sol -> System.out.println(sol));

    }
}
