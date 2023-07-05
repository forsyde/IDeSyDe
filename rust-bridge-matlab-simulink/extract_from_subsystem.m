function [converted] = extract_from_subsystem(subsystem)
%EXTRACT_FROM_SUBSYSTEM extracts a simulink subsystem model for Massymo
% 
% This function converts a simulink model into a format usable by Massymo
%
% Supported blocks:
%  - Gain
%  - Relational Operator
%  - Delay
%  - Unit Delay
%  - Discrete Integrator
blocks = Simulink.findBlocks(subsystem);
converted.processes = {};
converted.processesSizes = containers.Map;
converted.delays = {};
converted.delaysSizes = containers.Map;
converted.sources = {};
converted.sourcesPeriods = containers.Map;
converted.sourcesSizes = containers.Map;
converted.constants = {};
converted.sinks = {};
converted.sinksDeadlines = containers.Map;
converted.sinksSizes = containers.Map;
converted.processesOperations = containers.Map;
converted.delaysOperations = containers.Map;
converted.links = {};
% converted.linksDsts = {};
% converted.linksDataSizes = {};
for blockNum=1:size(blocks)
    block = blocks(blockNum);
    name = string(getfullname(block));
    bt = get_param(block, "BlockType");
    if bt == "SubSystem" % recursing into another subsystems
        if Simulink.SubsystemReference.isSystemLocked(block)
            converted.processes{end+1} = name;
            converted.processesSizes(name) = 0;
        else
            child = extract_from_subsystem(load_system(block));
            for nSubTask = 1:size(child.processes)
                task = child.processes{nSubTask};
                if ismember(task, converted.processes) == false
                    converted.processes{end+1} = task;
                    converted.processesSizes(name) = child.processesSizes{nSubTask};
                end
            end
        end
    elseif bt == "Delay"
        converted.delays{end+1} = name;
        converted.delaysSizes(name) = str2double(get_param(block, "DelayLength")) * 64;
        thisOps.ansiC = {};
        thisOps.ansiC.f64copy = str2double(get_param(block, "DelayLength"));
        converted.delaysOperations(name) = thisOps;
    elseif bt == "UnitDelay"
        converted.delays{end+1} = name;
        converted.delaysSizes(name) = 64;
        thisOps.ansiC = {};
        thisOps.ansiC.f64copy = 1;
        converted.delaysOperations(name) = thisOps;
    elseif bt == "Ground"
        converted.constants{end+1} = name;
    elseif bt == "Inport"
        converted.sources{end+1} = name;
        converted.sourcesPeriods(name) = str2double(get_param(block, "SampleTime"));
        converted.sourcesSizes(name) = 64;
    elseif bt == "Outport"
        converted.sinks{end+1} = name;
        converted.sinksDeadlines(name) = str2double(get_param(block, "SampleTime"));
        converted.sinksSizes(name) = 64;
    else
        converted.processes{end+1} = name;
        thisOps.ansiC = {};
        if bt == "DiscreteIntegrator"
            converted.processesSizes(name) = 64;
            thisOps.ansiC.f64mul = 3;
            thisOps.ansiC.f64add = 1;
        elseif bt == "Gain"
            thisOps.ansiC.f64mul = 1;
            converted.processesSizes(name) = 0;
        elseif bt == "RelationalOperator"
            thisOps.ansiC.f64comp = 1;
            converted.processesSizes(name) = 0;
        end
        converted.processesOperations(name) = thisOps;
    end
end
% now go through the links and store them
lines = find_system(subsystem,'FindAll','On','type', 'line');
for linex = 1:size(lines)
    src = get_param(lines(linex), 'SrcBlockHandle');
    dst = get_param(lines(linex), 'DstBlockHandle');
    converted.links{end+1} = [getfullname(src), getfullname(dst), "", "", 64];
%     converted.linksDsts{end+1} = getfullname(dst);
%     converted.linksDataSizes{end+1} = 64;
end

end

