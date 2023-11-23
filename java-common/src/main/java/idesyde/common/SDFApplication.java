package idesyde.common;

import com.fasterxml.jackson.annotation.JsonProperty;

import idesyde.core.DecisionModel;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Decision model for synchronous dataflow graphs.
 *
 * This decision model encodes a synchronous dataflow graphs without its
 * explicit topology
 * matrix, also known as balance matrix in some newer texts. This is achieved by
 * encoding
 * the graph as (A + C, E) where A is the set of actors, and C is the set of
 * channels. Every
 * edge in E connects an actor to a channel or a channel to an actor, i.e. e =
 * (a,c,m) or e
 * = (c,a.m) where m is the amount of token produced or consumed. For example,
 * if e = (a, c,
 * 2), then the edge e is the production of 2 tokens from the actor a to channel
 * c.
 *
 * This decision model is already analised, and provides the repetition vector
 * for the SDF
 * graphs contained as well as a schedule if these SDF graphs are consistent.
 */
public record SDFApplication(
                @JsonProperty("actor_minimum_throughputs") Map<String, Double> actorMinimumThroughputs,
                @JsonProperty("actors_identifiers") Set<String> actorsIdentifiers,
                @JsonProperty("chain_maximum_latency") Map<String, Map<String, Double>> chainMaximumLatency,
                @JsonProperty("channels_identifiers") Set<String> channelsIdentifiers,
                @JsonProperty("channel_token_sizes") Map<String, Long> channelTokenSizes,
                @JsonProperty("self_concurrent_actors") Set<String> selfConcurrentActors,
                @JsonProperty("topology_channel_names") List<Set<String>> topologyChannelNames,
                @JsonProperty("topology_consumption") List<Integer> topologyConsumption,
                @JsonProperty("topology_dsts") List<String> topologyDsts,
                @JsonProperty("topology_initial_tokens") List<Integer> topologyInitialTokens,
                @JsonProperty("topology_production") List<Integer> topologyProduction,
                @JsonProperty("topology_srcs") List<String> topologySrcs,
                @JsonProperty("topology_token_size_in_bits") List<Long> topologyTokenSizeInBits)
                implements DecisionModel {

        @Override
        public String category() {
                return "SDFApplication";
        }

        @Override
        public Set<String> part() {
                var partSet = new HashSet<String>();
                partSet.addAll(actorsIdentifiers);
                partSet.addAll(channelsIdentifiers);
                // for (var i = 0; i < topologySrcs.size(); i++) {
                // partSet.add("(%s, %s, %s)=%s:-%s:".formatted(
                // topologyProduction.get(i),
                // topologyConsumption.get(i),
                // topologyInitialTokens.get(i),
                // topologySrcs.get(i),
                // topologyDsts.get(i)));
                // }
                return partSet;
        }

        // @JsonProperty("actor_minimum_throughputs")
        // public Map<String, Double> getActorMinimumThroughputs() {
        // return actorMinimumThroughputs;
        // }

        // @JsonProperty("actor_minimum_throughputs")
        // public void setActorMinimumThroughputs(Map<String, Double> value) {
        // this.actorMinimumThroughputs = value;
        // }

        // @JsonProperty("actors_identifiers")
        // public Set<String> getActorsIdentifiers() {
        // return actorsIdentifiers;
        // }

        // @JsonProperty("actors_identifiers")
        // public void setActorsIdentifiers(Set<String> value) {
        // this.actorsIdentifiers = value;
        // }

        // @JsonProperty("chain_maximum_latency")
        // public Map<String, Map<String, Double>> getChainMaximumLatency() {
        // return chainMaximumLatency;
        // }

        // @JsonProperty("chain_maximum_latency")
        // public void setChainMaximumLatency(Map<String, Map<String, Double>> value) {
        // this.chainMaximumLatency = value;
        // }

        // @JsonProperty("channels_identifiers")
        // public Set<String> getChannelsIdentifiers() {
        // return channelsIdentifiers;
        // }

        // @JsonProperty("channels_identifiers")
        // public void setChannelsIdentifiers(Set<String> value) {
        // this.channelsIdentifiers = value;
        // }

        // @JsonProperty("self_concurrent_actors")
        // public Set<String> getSelfConcurrentActors() {
        // return selfConcurrentActors;
        // }

        // @JsonProperty("self_concurrent_actors")
        // public void setSelfConcurrentActors(Set<String> value) {
        // this.selfConcurrentActors = value;
        // }

        // @JsonProperty("topology_channel_names")
        // public List<List<String>> getTopologyChannelNames() {
        // return topologyChannelNames;
        // }

        // @JsonProperty("topology_channel_names")
        // public void setTopologyChannelNames(List<List<String>> value) {
        // this.topologyChannelNames = value;
        // }

        // @JsonProperty("topology_consumption")
        // public List<Long> getTopologyConsumption() {
        // return topologyConsumption;
        // }

        // @JsonProperty("topology_consumption")
        // public void setTopologyConsumption(List<Long> value) {
        // this.topologyConsumption = value;
        // }

        // @JsonProperty("topology_dsts")
        // public List<String> getTopologyDsts() {
        // return topologyDsts;
        // }

        // @JsonProperty("topology_dsts")
        // public void setTopologyDsts(List<String> value) {
        // this.topologyDsts = value;
        // }

        // @JsonProperty("topology_initial_tokens")
        // public List<Long> getTopologyInitialTokens() {
        // return topologyInitialTokens;
        // }

        // @JsonProperty("topology_initial_tokens")
        // public void setTopologyInitialTokens(List<Long> value) {
        // this.topologyInitialTokens = value;
        // }

        // @JsonProperty("topology_production")
        // public List<Long> getTopologyProduction() {
        // return topologyProduction;
        // }

        // @JsonProperty("topology_production")
        // public void setTopologyProduction(List<Long> value) {
        // this.topologyProduction = value;
        // }

        // @JsonProperty("topology_srcs")
        // public List<String> getTopologySrcs() {
        // return topologySrcs;
        // }

        // @JsonProperty("topology_srcs")
        // public void setTopologySrcs(List<String> value) {
        // this.topologySrcs = value;
        // }

        // @JsonProperty("topology_token_size_in_bits")
        // public List<Long> getTopologyTokenSizeInBits() {
        // return topologyTokenSizeInBits;
        // }

        // @JsonProperty("topology_token_size_in_bits")
        // public void setTopologyTokenSizeInBits(List<Long> value) {
        // this.topologyTokenSizeInBits = value;
        // }
}