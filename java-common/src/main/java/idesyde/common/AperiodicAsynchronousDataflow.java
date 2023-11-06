package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import idesyde.core.DecisionModel;
import idesyde.core.headers.DecisionModelHeader;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This decision model abstract asynchronous dataflow models that can be
 * described by a
 * repeating job-graph of this asynchronous processes. Two illustratives
 * dataflow models
 * fitting this category are synchronous dataflow models (despite the name) and
 * cyclo-static
 * dataflow models.
 *
 * Assumptions: 1. the job graph is always ready to be executed; or, the model
 * is
 * aperiodic.
 *
 * 2. executing the job graph as presented guarantees that the dataflow
 * processes are live
 * (never deadlocked).
 *
 * 3. The job graph ois weakly connected. If you wish to have multiple
 * "applications", you
 * should generate one decision model for each application.
 */
public record AperiodicAsynchronousDataflow(
        @JsonProperty("buffer_max_size_in_bits") Map<String, Long> bufferMaxSizeInBits,
        Set<String> buffers,
        @JsonProperty("job_graph_name") List<String> jobGraphName,
        @JsonProperty("job_graph_instance") List<Long> jobGraphInstance,
        @JsonProperty("job_graph_dst_name") List<String> jobGraphDstName,
        @JsonProperty("job_graph_dst_instance") List<Long> jobGraphDstInstance,
        @JsonProperty("job_graph_is_strong_precedence") List<Boolean> jobGraphIsStrongPrecedence,
        @JsonProperty("job_graph_src_name") List<String> jobGraphSrcName,
        @JsonProperty("job_graph_src_instance") List<Long> jobGraphSrcInstance,
        @JsonProperty("process_get_from_buffer_in_bits") Map<String, Map<String, Long>> processGetFromBufferInBits,
        @JsonProperty("process_minimum_throughput") Map<String, Double> processMinimumThroughput,
        @JsonProperty("process_path_maximum_latency") Map<String, Map<String, Double>> processPathMaximumLatency,
        @JsonProperty("process_put_in_buffer_in_bits") Map<String, Map<String, Long>> processPutInBufferInBits,
        @JsonProperty("buffer_token_size_in_bits") Map<String, Long> bufferTokenSizeInbits,
        Set<String> processes) implements DecisionModel {

    public record Job(String process, long instance) {
    }

    @Override
    public DecisionModelHeader header() {
        var s = new HashSet<String>();
        s.addAll(processes);
        s.addAll(buffers);
        return new DecisionModelHeader(
                "AperiodicAsynchronousDataflow",
                s,
                Optional.empty());
    }

    public Set<Job> jobsOfProcesses() {
        var jobs = new HashSet<Job>();
        for (int i = 0; i < jobGraphName.size(); i++) {
            jobs.add(new Job(jobGraphName.get(i), jobGraphInstance.get(i)));
        }
        // for (int i = 0; i < jobGraphDstName.size(); i++) {
        // jobs.add(new Job(jobGraphDstName.get(i), jobGraphDstInstance.get(i)));
        // }
        return jobs;
    }

    public Map<Job, Set<Job>> jobSucessors() {
        var jobs = jobsOfProcesses();
        Map<Job, Set<Job>> sucessorMap = jobs.stream().collect(Collectors.toMap(j -> j, j -> new HashSet<Job>()));
        for (var j : jobs) {
            jobSucessorsRecursion(sucessorMap, j);
        }
        return sucessorMap;
    }

    private void jobSucessorsRecursion(Map<Job, Set<Job>> sucessors, Job job) {
        for (int i = 0; i < jobGraphSrcName.size(); i++) {
            if (jobGraphSrcName.get(i).equals(job.process())
                    && jobGraphSrcInstance.get(i).equals(job.instance())) {
                var dst = new Job(jobGraphDstName.get(i), jobGraphDstInstance.get(i));
                if (!sucessors.get(job).contains(dst)) {
                    jobSucessorsRecursion(sucessors, dst);
                    sucessors.get(job).addAll(sucessors.get(dst));
                    sucessors.get(job).add(dst);
                }
            }
        }
    }

    public boolean isSucessor(Job predecessor, Job potentialSucessor) {
        // first check if is not an immediate sucessor
        // for (int i = 0; i < jobGraphSrcName.size(); i++) {
        // if (jobGraphSrcName.get(i).equals(predecessor.process())
        // && jobGraphSrcInstance.get(i).equals(predecessor.instance())) {
        // if (jobGraphDstName.get(i).equals(potentialSucessor.process())
        // && jobGraphDstInstance.get(i).equals(potentialSucessor.instance())) {
        // return true;
        // }
        // }
        // }
        // // if this is not true, now we recurse the graph to if there is any
        // conneciton
        // for (int i = 0; i < jobGraphSrcName.size(); i++) {
        // if (jobGraphSrcName.get(i).equals(predecessor.process())
        // && jobGraphSrcInstance.get(i).equals(predecessor.instance())) {
        // var nextJob = new Job(jobGraphDstName.get(i), jobGraphSrcInstance.get(i));
        // if (isSucessor(nextJob, potentialSucessor)) {
        // return true;
        // }
        // }
        // }
        return isSucessor(predecessor.process(), predecessor.instance(), potentialSucessor.process(),
                potentialSucessor.instance());
    }

    public boolean isSucessor(String predecessorName, long predecessorInstance, String potentialSucessorName,
            long potentialSucessorInstance) {
        // first check if is not an immediate sucessor
        for (int i = 0; i < jobGraphSrcName.size(); i++) {
            if (jobGraphSrcName.get(i).equals(predecessorName)
                    && jobGraphSrcInstance.get(i).equals(predecessorInstance)) {
                if (jobGraphDstName.get(i).equals(potentialSucessorName)
                        && jobGraphDstInstance.get(i).equals(potentialSucessorInstance)) {
                    return true;
                }
            }
        }
        // if this is not true, now we recurse the graph to if there is any conneciton
        for (int i = 0; i < jobGraphSrcName.size(); i++) {
            if (jobGraphSrcName.get(i).equals(predecessorName)
                    && jobGraphSrcInstance.get(i).equals(predecessorInstance)) {
                if (isSucessor(jobGraphDstName.get(i), jobGraphSrcInstance.get(i), potentialSucessorName,
                        potentialSucessorInstance)) {
                    return true;
                }
            }
        }
        return false;
    }

}
