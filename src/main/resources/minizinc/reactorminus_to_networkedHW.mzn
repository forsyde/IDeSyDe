include "globals.mzn";

% objectives
int: objPercentage;

% model parameters
int: hyperPeriod;
int: nReactors;
int: nReactions;
int: nChannels;
% int: nJobs;
% int: nJobChannels;

set of int: Reactors = 1..nReactors;
set of int: Reactions = 1..nReactions;
set of int: Channels = 1..nChannels;
% set of int: Jobs = 1..nJobs;
% set of int: JobChannels = 1..nJobChannels;


% platform elements
int: nPlatformElems;
set of int: PlatformElems = 1..nPlatformElems;
array[PlatformElems] of bool: isFixedPriorityElem;
array[PlatformElems] of bool: isTimeTriggeredElem;
array[PlatformElems] of bool: isRoundRobinElem;
array[PlatformElems] of bool: isProcessingElem;
array[PlatformElems] of bool: isCommunicationElem;
array[PlatformElems] of bool: isMemoryElem;
array[PlatformElems] of int: platformElemsSymmetryGroups;

% inferred sets
set of int: TimeTriggeredElems = {p | p in PlatformElems where isTimeTriggeredElem[p]};
set of int: FixedPriorityElems = {p | p in PlatformElems where isFixedPriorityElem[p]};
set of int: RoundRobinElems = {p | p in PlatformElems where isRoundRobinElem[p]};
set of int: ProcessingElems = {p | p in PlatformElems where isProcessingElem[p]};
set of int: CommunicationElems = {p | p in PlatformElems where isCommunicationElem[p]};
set of int: MemoryElems = {p | p in PlatformElems where isMemoryElem[p]};


% goal elements
int: nReactionChains;
set of int: ReactionChains = 1..nReactionChains;

% reactors
array[Reactors] of int: reactorSize;
% reactions
array[Reactions] of Reactors: containingReactor;
array[Reactions] of int: reactionDataSize;
array[Reactions] of int: reactionLatestRelease;
array[Reactions] of int: reactionRelativeDeadline;
array[Reactions, Reactions] of int: reactionMinimumForwardLatency;
array[Reactions, Reactions] of bool: reactionPriorityLTEQ; % jobHigherPriority[j, jj] <-> j >> jj <-> j has higher prio than jj
array[Reactions, Reactions] of bool: reactionInterferes;
array[Reactions, PlatformElems] of int: reactionWcet;
array[Reactions, PlatformElems] of int: reactionUtilization;
array[Reactions, PlatformElems] of bool: reactionCanBeExecuted;
% channels 
array[Channels] of int: channelSize;
array[Channels] of Reactions: channelSrc;
array[Channels] of Reactions: channelDst;

% for platform
% array[PlatformElems] of int: roundRobinElemsMinSlice;
array[PlatformElems] of int: roundRobinElemsMaxSlices;
array[PlatformElems, PlatformElems, PlatformElems] of int: platformPaths;
array[PlatformElems, PlatformElems] of int: allocatedBandwidth;
array[PlatformElems] of int: memoryMaximumCapacity;
% goal elements
array[ReactionChains] of Reactions: reactionChainStart;
array[ReactionChains] of Reactions: reactionChainEnd;
array[ReactionChains] of int: reactionChainFixedLatency;

% additional sets
set of int: IndependentReactions = {
  rr | rr in Reactions where not(exists(r in Reactions) (reactionMinimumForwardLatency[r, rr] >= 0))
};
set of int: DependentReactions = {r | r in Reactions where not(r in IndependentReactions)};

% main decision variables
array[Reactors] of var MemoryElems: reactorMapping;
array[Channels] of var MemoryElems: channelMapping;
array[Reactions] of var ProcessingElems: reactionExecution;
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionResponseTime;
array[Reactions, PlatformElems] of var 0..max(roundRobinElemsMaxSlices): reactionRRSlices;
%% tighten bonds of reactionExecution
constraint forall(j in Reactions, p in ProcessingElems where reactionCanBeExecuted[j, p] = false) (
  reactionExecution[j] != p
);
%% tighten bound of jobStartingTime:
constraint forall(r in Reactions, p in ProcessingElems) (
    0 <= reactionResponseTime[r, p] /\ reactionResponseTime[r, p] <= reactionRelativeDeadline[r] 
);
constraint forall(r in Reactions, p in PlatformElems where not(p in ProcessingElems)) (
  reactionResponseTime[r, p] = 0
);
%% tighten bound of reactionRRSlices:
constraint forall(r in Reactions, p in RoundRobinElems) (
   0 <= reactionRRSlices[r, p] /\ reactionRRSlices[r, p] <= roundRobinElemsMaxSlices[p]
  % reactionRRSlices[r, p] <= min([reactionWcet[r, p], roundRobinElemsMaxSlices[p]])
);
constraint forall(r in Reactions, p in PlatformElems where not(p in RoundRobinElems)) (
  reactionRRSlices[r, p] = 0
  % reactionRRSlices[r, p] <= min([reactionWcet[r, p], roundRobinElemsMaxSlices[p]])
);

% auxiliary decision variables
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionInstDuration;
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionDataDuration;
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionExeDuration;
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionOutDuration;
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionDuration;
array[PlatformElems] of var 0..max(roundRobinElemsMaxSlices): roundRobinPeriod;
%% tighten the bounds for reactionInstDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionInstDuration[r, pe]
);
constraint forall(r in Reactions, pe in ProcessingElems) (
  reactionInstDuration[r, pe] <=
  max([
    sum(ce in CommunicationElems where 1 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (reactionDataSize[r] / allocatedBandwidth[ce, pe])
    | mem in MemoryElems
  ])
);

%% tighten the bounds for reactionDataDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionDataDuration[r, pe]
);
constraint forall(r in Reactions, pe in ProcessingElems) (
  reactionDataDuration[r, pe] <=
  max(mem in MemoryElems) (
    sum(c in Channels, ce in CommunicationElems where channelDst[c] = r /\ 0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  )
);
%% tighten the bounds for reactionExeDuration
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionExeDuration[r, pe] /\ reactionExeDuration[r, pe] <= reactionWcet[r, pe]
);
%% tighten the bounds for reactionOutDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionOutDuration[r, pe]
);
constraint forall(r in Reactions, pe in ProcessingElems) (
  reactionOutDuration[r, pe] <=
  max(mem in MemoryElems) (
    sum(c in Channels, ce in CommunicationElems where channelSrc[c] = r /\ 0 < platformPaths[pe, mem, ce] /\ platformPaths[pe, mem, ce] < platformPaths[pe, mem, mem]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  )
);
%% make the equality and tighten the bounds for reactionDuration
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionDuration[r, pe] /\ reactionDuration[r, pe] <= reactionRelativeDeadline[r] %min(j in Jobs where originalReaction[j] = r) (jobDeadline[j] - jobRelease[j])
);
constraint forall(r in Reactions, p in PlatformElems where not(p in ProcessingElems)) (
  reactionDuration[r, p] = 0 /\
  reactionInstDuration[r, p] = 0 /\
  reactionDataDuration[r, p] = 0 /\
  reactionExeDuration[r, p] = 0 /\
  reactionOutDuration[r, p] = 0
);

%% tighten the bounds for roundRobinPeriod
constraint forall(pe in RoundRobinElems) (
  0 <= roundRobinPeriod[pe]
  /\ roundRobinPeriod[pe] <= roundRobinElemsMaxSlices[pe]
);
constraint forall(pe in PlatformElems where not(pe in RoundRobinElems)) (
  roundRobinPeriod[pe] = 0
);


% objectives
int: minUtilization = sum(j in Reactions) (
  min([reactionUtilization[j, p] | p in ProcessingElems])
);
int: maxUtilization = sum(j in Reactions) (
  max([reactionUtilization[j, p] | p in ProcessingElems])
);
var minUtilization..maxUtilization: totalUtilization;
int: minTotalLatencies = sum(l in ReactionChains) (reactionChainFixedLatency[l]);
int: maxTotalLatencies = sum(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  reactionRelativeDeadline[lst] + reactionLatestRelease[lst]
);
array[ReactionChains] of var min(reactionChainFixedLatency)..hyperPeriod: latencies;
var minTotalLatencies..maxTotalLatencies: totalLatencies;
%% tighten the bounds
constraint forall(l in ReactionChains) (
  reactionChainFixedLatency[l] <= latencies[l]
);
constraint forall(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  latencies[l] <= reactionRelativeDeadline[lst] + reactionLatestRelease[lst]
);


% baseline constraints
%% memory contraints
constraint bin_packing_capa(
  memoryMaximumCapacity,
  reactorMapping ++ channelMapping,
  reactorSize ++ channelSize
);
constraint forall(r in Reactions, pe in ProcessingElems) (
  if (reactionExecution[r] = pe) then
    reactionExeDuration[r, pe] = reactionWcet[r, pe]
  else
    reactionExeDuration[r, pe] = 0
  endif
);
%% inferred ones
% constraint forall(r in Reactions, p in ProcessingElems) (
%   (reactionExecution[r] = p) * reactionWcet[r, p] / 10.0 <= reactionResponseTime[r, p]
% );
constraint forall(r in Reactions, p in ProcessingElems) (
  if (reactionExecution[r] = p) then
    reactionResponseTime[r, p] <= reactionRelativeDeadline[r]
  else
    reactionResponseTime[r, p] = 0
  endif
);

% data movements constraints
% instruction
constraint forall(r in Reactions, mem in MemoryElems, pe in ProcessingElems) (
  let {int: a = containingReactor[r]} in
  if (reactionExecution[r] = pe /\ reactorMapping[a] = mem) then
      reactionInstDuration[r, pe] = sum(ce in CommunicationElems where 
        0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
        (reactionDataSize[r] / allocatedBandwidth[ce, pe])
  else
    reactionInstDuration[r, pe] = 0
  endif
);
%% data
constraint forall(r in Reactions, mem in MemoryElems, pe in ProcessingElems) (
  let {int: a = containingReactor[r]} in
  if (reactionExecution[r] = pe  /\ reactorMapping[a] = mem ) then
    reactionDataDuration[r, pe] =  sum(c in Channels, ce in CommunicationElems where 
      channelDst[c] = r /\ 0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  else 
    reactionDataDuration[r, pe] = 0
  endif
);
%% write
constraint forall(r in Reactions, mem in MemoryElems, pe in ProcessingElems) (
  let {Reactors: a = containingReactor[r]} in
  if (reactionExecution[r] = pe /\ reactorMapping[a] = mem) then
    reactionOutDuration[r, pe] =  sum(c in Channels, ce in CommunicationElems where 
      channelSrc[c] = r /\ 0 < platformPaths[pe, mem, ce] /\ platformPaths[pe, mem, ce] < platformPaths[pe, mem, mem]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  else 
    reactionOutDuration[r, pe] = 0
  endif
);


% fixed-priority constraints
constraint forall(j in Reactions, p in FixedPriorityElems) (
  reactionDuration[j, p] = reactionInstDuration[j, p] + reactionDataDuration[j, p] + reactionExeDuration[j, p] + reactionOutDuration[j, p]
);
constraint forall(r in IndependentReactions, p in FixedPriorityElems) (
  if (reactionExecution[r] = p) then
    reactionDuration[r, p] + 
    sum(rr in Reactions where reactionPriorityLTEQ[r, rr] /\ reactionInterferes[rr, r] /\ reactionExecution[rr] = p /\ rr != r) (reactionDuration[rr, p]) 
    = reactionResponseTime[r, p]
  else
   reactionResponseTime[r, p] = 0
  endif
);
constraint forall(r in Reactions, rr in Reactions, pp in FixedPriorityElems, p in ProcessingElems where reactionMinimumForwardLatency[r, rr] >= 0 /\ r != rr) (
  if (reactionExecution[rr] = pp /\ reactionExecution[r] = p) then
  reactionResponseTime[rr, pp] + reactionMinimumForwardLatency[r, rr] >= reactionDuration[rr, pp] + reactionResponseTime[r, p]
  + sum(rrr in Reactions where reactionPriorityLTEQ[rr, rrr] /\ reactionInterferes[rrr, rr] /\ reactionExecution[rrr] = pp /\ rrr != rr /\ rrr != r) 
      (reactionDuration[rrr, pp])
  endif
);

% round-robin constraints

%% minimum time slice allocation
constraint forall(r in Reactions, p in RoundRobinElems) (
  if (reactionExecution[r] = p) then
   0 <= reactionRRSlices[r, p]
  else
   reactionRRSlices[r, p] = 0
  endif
);
constraint forall(p in RoundRobinElems) (
  sum(reactionRRSlices[.., p]) <= roundRobinElemsMaxSlices[p]
);
constraint forall(r in Reactions, p in RoundRobinElems) (
  if (reactionExecution[r] = p) then
    reactionDuration[r, p] * reactionRRSlices[r, p] = 
    roundRobinElemsMaxSlices[p] * (reactionInstDuration[r, p] + reactionDataDuration[r, p] + reactionExeDuration[r, p] + reactionOutDuration[r, p])
  else
    reactionDuration[r, p] = 0
  endif
);

%% with dependencies
constraint forall(r in IndependentReactions, p in RoundRobinElems) (
  if (reactionExecution[r] = p) then
    reactionDuration[r, p] = reactionResponseTime[r, p]
  else 
    reactionResponseTime[r, p] = 0
  endif
);
constraint forall(r in Reactions, rr in Reactions, pp in RoundRobinElems, p in ProcessingElems where reactionMinimumForwardLatency[r, rr] >= 0 /\ r != rr) (
  if (reactionExecution[rr] = pp /\ reactionExecution[r] = p) then
    reactionResponseTime[rr, pp] + reactionMinimumForwardLatency[r, rr] >= reactionDuration[rr, pp] + reactionResponseTime[r, p]
  endif
);


% objectives
constraint totalUtilization = sum(p in ProcessingElems, r in Reactions where reactionExecution[r] = p) (reactionUtilization[r, p]);

constraint forall(l in ReactionChains, p, pp in ProcessingElems) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  if (reactionExecution[lst] = p /\ reactionExecution[fst] = pp) then
  latencies[l] = reactionResponseTime[lst, p] - (reactionResponseTime[fst, pp] - reactionDuration[fst, pp]) + reactionChainFixedLatency[l]
  endif
);
constraint totalLatencies = sum(l in ReactionChains) (latencies[l]);

% symmetry breaking
constraint symmetry_breaking_constraint(forall(p, pp in ProcessingElems where platformElemsSymmetryGroups[p] > 0 /\ platformElemsSymmetryGroups[pp] > 0 /\ platformElemsSymmetryGroups[p] = platformElemsSymmetryGroups[pp] /\ p < pp) (
  sum(r in Reactions where reactionExecution[r] = p) (reactionUtilization[r, p]) >=
  sum(r in Reactions where reactionExecution[r] = pp) (reactionUtilization[r, pp])
));

constraint symmetry_breaking_constraint(forall(p, pp in ProcessingElems where platformElemsSymmetryGroups[p] > 0 /\ platformElemsSymmetryGroups[pp] > 0 /\ platformElemsSymmetryGroups[p] = platformElemsSymmetryGroups[pp] /\ p < pp) (
  sum(r in Reactions where reactionExecution[r] = p) (reactionUtilization[r, p]) =
  sum(r in Reactions where reactionExecution[r] = pp) (reactionUtilization[r, pp]) ->
  count(r in Reactions) (reactionExecution[r] = p) >= count(r in Reactions) (reactionExecution[r] = pp)
));

solve
  :: warm_start(reactionExecution, [arg_min(p in ProcessingElems where reactionCanBeExecuted[r, p]) (reactionWcet[r, p]) | r in Reactions])
  :: int_search(reactionResponseTime,  first_fail, indomain_min)
  :: int_search(reactionRRSlices, first_fail, indomain_max)
  % :: int_search(roundRobinPeriod, first_fail, indomain_min)
  :: int_search(reactionExecution, first_fail, indomain_min)
  :: int_search(reactionUtilization, first_fail, indomain_min)
  :: int_search(latencies, first_fail, indomain_min)
  :: restart_luby(length(Reactions) * length(ProcessingElems))
  minimize 100 * totalUtilization * minTotalLatencies + objPercentage * totalLatencies * minUtilization;

% output [
%   "reactorMapping = ", show(reactorMapping), "\n", 
%   "channelMapping = ", show(channelMapping), "\n", 
%   "reactionExecution = ", show(reactionExecution), "\n", 
%   "reactionResponseTime = ", show2d(reactionResponseTime), "\n", 
%   "reactionRRSlices = ", show2d(reactionRRSlices), "\n", 
%   "reactionDuration = ", show2d(reactionDuration), "\n", 
%   "reactionExeDuration = ", show2d(reactionExeDuration), "\n", 
%   "reactionInstDuration = ", show2d(reactionInstDuration), "\n", 
%   "reactionDataDuration = ", show2d(reactionDataDuration), "\n", 
%   "reactionOutDuration = ", show2d(reactionOutDuration), "\n", 
%   "totalUtilization = ", show(totalUtilization), "\n",
%   "latencies = ", show(latencies)];
