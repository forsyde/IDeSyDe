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
converted.processes_sizes = containers.Map;
converted.delays = {};
converted.delays_sizes = containers.Map;
converted.sources = {};
converted.sources_periods = containers.Map;
converted.sources_periods_numen = containers.Map;
converted.sources_periods_denom = containers.Map;
converted.sources_sizes = containers.Map;
converted.constants = {};
converted.sinks = {};
converted.sinks_deadlines = containers.Map;
converted.sinks_deadlines_numen = containers.Map;
converted.sinks_deadlines_denom = containers.Map;
converted.sinks_sizes = containers.Map;
converted.processes_operations = containers.Map;
converted.delays_operations = containers.Map;
converted.links_src = {};
converted.links_dst = {};
converted.links_src_port = {};
converted.links_dst_port = {};
converted.links_size = {};
% converted.linksDsts = {};
% converted.linksDataSizes = {};
for blockNum=1:size(blocks)
    block = blocks(blockNum);
    name = string(getfullname(block));
    bt = get_param(block, "BlockType");
    if bt == "SubSystem" % recursing into another subsystems
        if Simulink.SubsystemReference.isSystemLocked(block)
            converted.processes{end+1} = name;
            converted.processes_sizes(name) = 0;
        else
            child = extract_from_subsystem(load_system(block));
            for nSubTask = 1:size(child.processes)
                task = child.processes{nSubTask};
                if ismember(task, converted.processes) == false
                    converted.processes{end+1} = task;
                    converted.processes_sizes(name) = child.processes_sizes{nSubTask};
                end
            end
        end
    elseif bt == "Delay"
        converted.delays{end+1} = name;
        converted.delays_sizes(name) = str2double(get_param(block, "DelayLength")) * 64;
        thisOps.ansiC = {};
        thisOps.ansiC.f64copy = str2double(get_param(block, "DelayLength"));
        converted.delays_operations(name) = thisOps;
    elseif bt == "UnitDelay"
        converted.delays{end+1} = name;
        converted.delays_sizes(name) = 64;
        thisOps.ansiC = {};
        thisOps.ansiC.f64copy = 1;
        converted.delays_operations(name) = thisOps;
    elseif bt == "Ground"
        converted.constants{end+1} = name;
    elseif bt == "Inport"
        converted.sources{end+1} = name;
        converted.sources_periods(name) = str2double(get_param(block, "SampleTime"));
        [n, d] = rat(converted.sources_periods(name));
        converted.sources_periods_numen(name) = n;
        converted.sources_periods_denom(name) = d;
        converted.sources_sizes(name) = 64;
    elseif bt == "Outport"
        converted.sinks{end+1} = name;
        converted.sinks_deadlines(name) = str2double(get_param(block, "SampleTime"));
        [n, d] = rat(converted.sinks_deadlines(name));
        converted.sinks_deadlines_numen(name) = n;
        converted.sinks_deadlines_denom(name) = d;
        converted.sinks_sizes(name) = 64;
    else
        converted.processes{end+1} = name;
        thisOps.ansiC = {};
        if bt == "DiscreteIntegrator"
            converted.processes_sizes(name) = 64;
            thisOps.ansiC.f64mul = 3;
            thisOps.ansiC.f64add = 1;
        elseif bt == "Gain"
            thisOps.ansiC.f64mul = 1;
            converted.processes_sizes(name) = 0;
        elseif bt == "RelationalOperator"
            thisOps.ansiC.f64comp = 1;
            converted.processes_sizes(name) = 0;
        end
        converted.processes_operations(name) = thisOps;
    end

end
% now go through the links and store them
lines = find_system(subsystem,'FindAll','On','type', 'line');
for linex = 1:size(lines)
    src = get_param(lines(linex), 'SrcBlockHandle');
    dst = get_param(lines(linex), 'DstBlockHandle');
    src_port = get_param(lines(linex), 'SrcPortHandle');
    dst_port = get_param(lines(linex), 'DstPortHandle');
    converted.links_src{end+1} = getfullname(src);
    converted.links_dst{end+1} = getfullname(dst);
    converted.links_src_port{end+1} = getfullname(src_port);
    converted.links_dst_port{end+1} = getfullname(dst_port);
    converted.links_size{end+1} = 64;
%     converted.linksDsts{end+1} = getfullname(dst);
%     converted.linksDataSizes{end+1} = 64;
end

end

