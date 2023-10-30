---
layout: default
title: IDeSyDe - Usage
permalink: /usage
---


# Usage

## Requirements for running the demonstrators

Regardless if you are checking this repository from a Linux or Windows OS, you need at least Java 17 to run the I-modules and E-modules.
On Linux you can use the distrubition's package manager, e.g. `apt-get` or `dnf`, to install the JVM system wide.
On Windows you can likely find official installer on all major JVM distributions like [Oracle](https://www.oracle.com/java/technologies/downloads/).

We recommend using a versioning installer like [jabba](https://github.com/shyiko/jabba) and installing the lastest stable JVM possible.
The modules were tested with [Amazon Coretto](https://aws.amazon.com/corretto/?filtered-posts.sort-by=item.additionalFields.createdDate&filtered-posts.sort-order=desc) and the [Graal VM](https://www.graalvm.org/).

If you fail to meet the Java requirement, you might get an output like:

    [2023-05-22T14:01:17Z INFO ] Run directory is run
    [2023-05-22T14:01:17Z INFO ] Final output set to explored_and_integrated.fiodl
    [2023-05-22T14:01:28Z INFO ] Identified 0 decision model(s)
    [2023-05-22T14:01:28Z INFO ] Computed 0 dominant bidding(s)
    [2023-05-22T14:01:28Z INFO ] Starting exploration until completion.
    [2023-05-22T14:01:28Z INFO ] Finished exploration with 0 solution(s).

For the case studies where *we know* there should be identifications.
This is simply the orchestrator being fault tolerant and skipping problematic modules whenever possible.
Therefore, make sure that `java` is available in your machine and that it indeed 17 or higher. You can check this with:

    > java -version
    openjdk version "17.xxx" 2021-04-20
    ...

If you plan to run the simulink example, you must also have `matlab` callable in your PATH. This is typically done automatically by the MATLAB installer
on Windows, and there is good chance you know how to do it this already if you are running from Linux.
Otherwise, follow [this link for windows](https://se.mathworks.com/matlabcentral/answers/94933-how-do-i-edit-my-system-path-in-windows)
or [this link for Linux](https://unix.stackexchange.com/questions/244941/how-to-add-my-matlab-to-path) to get a good start.

Once you have the pre-requisites, download the [latest IDeSyDe release](https://github.com/forsyde/IDeSyDe/releases) but don't extract it anywhere yet.

> Be careful of OS-integrated syncronization mechanisms like OneDrive; for some reason, it can cause strange errors outside our control when are in folders managed by them.

## Fetching the demonstrators

The demonstrators can be fetched from [the `exampled_and_benchmarks` folder in IDeSyDe's repo](https://download-directory.github.io/?url=https://github.com/forsyde/IDeSyDe/tree/master/examples_and_benchmarks).
<!-- You could also try directly get many tests from , but let's re-use the information that already existed. -->
Choose a folder to contain the demonstrators and the data that they will generate in your computed. Keep in mind the last observation of the [previous section](#requirements-for-running-the-demonstrators).

Now, extract the release zip you fetched in the last step of the [previous section](#requirements-for-running-the-demonstrators) so that everything in contained into this folder.
You can rename the folder, naturally, but you cannot rename the `imodules` and `emodules` folder as of IDeSyDe 0.6+.

## Running the provided demonstrators

The orchestrator should **always** run at the top of this folder tree, which is also the IDeSyDe workspace.
If you move the orchestrator binary around or try to run it from another folder, it might not find the I-modules
and E-modules in their respective `imodules` and `emodules` folders.

Be very mindful of the `run path` that the orchestrator is using.
As the tool tries to be incremental due to the DSI techniques,
if you run multiple case studies in the same `run path` there is a risk that you will hit corner cases where the orchestrator did not clean the workspace properly and composedly identifies design models that do not exist anymore.
If you *only* add new inputs there is no problem; if you *change* inputs there might be a problem.

Use the `--run-path` option of the orchestrator to enable multiple run folders side-by-side to avoid these problems.

Finally, use the `-p` or `--parallel-jobs` option with a number to increase the parallelisation of non-exploration
procedures. This is mainly done from the orchestrator side, so make sure you have a machine with enough resources to
support multiple concurrent processes running. If you don't, there is little to no value on using the `-p` option.
Note that parallel exploration is depends on the capabilities of the explorer being called.
The choco solver currently uses no parallel techniques.

In the following examples, we give the Windows and Linux commands side-by-side.

> Kindly keep in mind that some of these demonstrators are actually hard problems to be proved optimally, so the `--x-max-solutions` can be handy to limit the amount of time spent looking for better solutions.
> For this same reason, the parameter `-x-total-time-out` can also put a **firm** limit on the total time elapsed by the explorer.
> The total time-out can overshoot a bit due to how the limit is realized by the [Choco Solver](https://choco-solver.org/docs/solving/limits/), but it won't be an overshoot of hours. The maximum observed so far was in the range of seconds.
> You can find other upper limits on the exploration procedure by calling IDeSyDe with `-h` or `--help`.

### 1) Avionics

You can run the Flight Function Information through,

    .\orchestrator.exe --run-path run_avionics_fif .\avionics\flight-information-function\case_study.fiodl # Windows

    ./orchestrator --run-path run_avionics_fif ./avionics/flight-information-function/case_study.fiodl # Linux

which should give you:

    [2023-05-23T12:55:28Z INFO ] Run directory is run_avionics_fif
    [2023-05-23T12:55:28Z INFO ] Final output set to explored_and_integrated.fiodl
    [2023-05-23T12:55:39Z INFO ] Identified 5 decision model(s)
    [2023-05-23T12:55:40Z INFO ] Computed 1 dominant bidding(s)
    [2023-05-23T12:55:40Z INFO ] Starting exploration until completion.
    [2023-05-23T12:55:45Z INFO ] Finished exploration with 1 solution(s).

This one solution is the single-core solution that satisfies all the timing requirements.
You can open the results `fiodl` file in the `reversed` folder to check its contents, namely the `run_avionics_fif\reversed\reversed_0_YyyYyYyIdentificationModule.fiodl` file:

    systemgraph {
        vertex "60Hz"
        [execution::PeriodicStimulus, visualization::Visualizable]
        (activated)
        {
            "offsetDenominator": 1_l,
            "periodDenominator": 1000000_l,
            "offsetNumerator": 0_l,
            "periodNumerator": 16666_l
        }
        vertex "30Hz"
        [execution::PeriodicStimulus, visualization::Visualizable]
    ...

You can find in this design model file the periodic tasks that are "LoopingTasks", i.e. that execute a block of code forever. For example, we have "FormatSpeedVectorCalc":

    vertex "FormatSpeedVectorCalc"
    [...execution::CommunicatingTask, execution::LoopingTask...] ...

which is part of an arc that starts at the task and points to the scheduler responsible for scheduling, as obtained from the exploration results:

    edge [decision::AbstractScheduling] from "FormatSpeedVectorCalc" port "schedulers" to "CPM1_Core1_Runtime.CMP1_Core1_FP_Runtime_Scheduler" 

you could then inspect this scheduler in the file to get further insight on what type of scheduler it is, although the name is arguably self-descriptive:

    vertex "CPM1_Core1_Runtime.CMP1_Core1_FP_Runtime_Scheduler" [...platform::PlatformElem, platform::runtime::FixedPriorityScheduler...]

Note that the information in this explored `fiodl` model was already contained in `avionics\flight-information-function\case_study.fiodl`, except for the edges that connect the tasks to the schedulers, indicating the results of the exploration.

if you would wish to see more information as the orchestrator goes through its steps, you can issue

    .\orchestrator.exe -v DEBUG --run-path run_avionics_fif .\avionics\flight-information-function\case_study.fiodl # Windows

    ./orchestrator -v DEBUG --run-path run_avionics_fif ./avionics/flight-information-function/case_study.fiodl # Linux

which gives the more detailed output:

    [2023-05-23T13:06:43Z INFO ] Run directory is run_avionics_fif
    [2023-05-23T13:06:43Z INFO ] Final output set to explored_and_integrated.fiodl
    [2023-05-23T13:06:43Z DEBUG] Copying input files
    [2023-05-23T13:06:43Z DEBUG] Registering external identification module with identifier C:\Users\XXX\gits\memocode-demonstrator-16\imodules\scala-bridge-devicetree.jar
    [2023-05-23T13:06:43Z DEBUG] Registering external identification module with identifier C:\Users\XXX\gits\memocode-demonstrator-16\imodules\scala-bridge-matlab.jar
    [2023-05-23T13:06:43Z DEBUG] Registering external identification module with identifier C:\Users\XXX\gits\memocode-demonstrator-16\imodules\scala-common.jar
    [2023-05-23T13:06:43Z DEBUG] Registering external identification module with identifier C:\Users\XXX\gits\memocode-demonstrator-16\imodules\scala-ourmdetool.jar
    [2023-05-23T13:06:43Z DEBUG] Registering external exploration module with identifier C:\Users\XXX\gits\memocode-demonstrator-16\emodules\scala-choco.jar
    [2023-05-23T13:06:51Z DEBUG] 5 total decision models identified at step 1
    [2023-05-23T13:06:53Z DEBUG] 5 total decision models identified at step 2
    [2023-05-23T13:06:53Z INFO ] Identified 5 decision model(s)
    [2023-05-23T13:06:55Z INFO ] Computed 1 dominant bidding(s)
    [2023-05-23T13:06:55Z DEBUG] Proceeding to explore PeriodicWorkloadToPartitionedSharedMultiCore with C:\Users\XXX\gits\memocode-demonstrator-16\emodules\scala-choco.jar
    [2023-05-23T13:06:55Z INFO ] Starting exploration until completion.
    [2023-05-23T13:06:57Z DEBUG] Found a new solution. Total count is 1.
    [2023-05-23T13:06:59Z DEBUG] Reverse identified the design model YyyYyYyDesignModel at run_avionics_fif\reversed
    [2023-05-23T13:06:59Z INFO ] Finished exploration with 1 solution(s).

### 2) Simulink and device tree

Before starting this case study, make sure the [requirements](#requirements-for-running-the-demonstrators) are satisfied.
Otherwise, you'll likely get 0 explorable decision models as Matlab may not be callable from your PATH.

Let's inspect the input files to understand how to interpret the exploration results.
The `yaml` file is the "OS Description" in the sense that it binds together the local views of the [DeviceTree](https://www.devicetree.org/) files `tile0.dts` and `tile1.dts`:

    oses:
        os0:
            name: FreeRTOS0
            host: tile0cpu
            affinity:
                - tile0cpu
            policy: 
                - FP
        os1:
            name: FreeRTOS1
            host: tile1cpu
            affinity:
                - tile1cpu
            policy: 
                - FP

In this case, it states that there are two runtimes in this platform, aptly name `FreeRTOS(0|1)` which are hosted in the CPUSs `tile0cpu` and `tile1cpu` respectively.
The policy that these runtimes can follow is simply Fixed-Priority, shorted to `FP`.
This acronym is based on the [LITMUS-RT](https://www.litmus-rt.org/tutor16/manual.html) scheduling policies nomeclature.

Now, let's inspect of the [DeviceTree](https://www.devicetree.org/) files, `tile0.dts` in this case:

    bus-frequency = <50000000>
    bus-concurrency = <1>
    bus-flit = <32>
    bus-clock-per-flit = <32>
    cpus {
        tile0cpu: cpu@0 {
            clock-frequency = <50000000>
            ops-per-cycle {
                default {
                    f64add = <1 6>
                    f64mul = <1 1000>
                    f64copy = <1 4>
                    f64comp = <1 4>
                }
                proc {
                    all = <1 10>
                }
            }
        }
    }
    gbus: bus@0x800A0 {
        compatible = "simple-bus"
        clock-frequency = <50000000>
        bus-concurrency = <1>
        bus-flit = <32>
        bus-clock-per-flit = <32>
    }
    memory@0x00000 {
        clock-frequency = <50000000>
        device-type = "memory"
        reg = <0x0 0x800000>
    }

Where the entries `compatible` and `chassis` are omitted because they play no role for our case study.
The `bus-*` properties in the root of this file represent the performance characteristics of the "main bus" for this local HW description.
As mentioned in the paper, this is because every `dts` file represents a local view of the HW;
specifically, whatever is gonna be controlled by one runtime.
This also means that the OS Description `affinity` and the number of cpus in a `dts` file should match, but this is not enforced now for the sake of simplicity.

There are two important features in the `dts` files that we remark in case the reader wishes to specify their own: using global labels and the `ops-per-cycle` property.

A global label is like the name before `:` in the `dts` specification.
In `tile0.dts`, these are `tile0cpu` and `gbus`.
Using the global labels is what enables IDeSyDe to cross-reference different parts of the OS and HW descriptions to build a linked and coherent platform model.
For instance, without the `tile0cpu` label in `tile0.dts`, the `tile0cpu` affinity and host references in the `yaml` file would refer to nothing.
They are also what enables building a connected HW model out of each `dts` file.
For example, we can quickly inspect `tile1.dts` to find:

    gbus: bus@0x800A0 {
        compatible = "simple-bus"
        clock-frequency = <50000000>
        bus-concurrency = <1>
        bus-flit = <32>
        bus-clock-per-flit = <32>
    }

That is, the same global bus!
Therefore, the local platform views defined by `tile0.dts` and `tile1.dts` are connected through a bus identified by `gbus`.

The property `ops-per-cycle` is key to determining the execution time of processes into the CPU in question.
It consists of different *modes* that are assumed to be constant during the system's operation, which can provide different *operations*.
In the `tile0.dts` there are two modes: the `default` and the `proc` mode.
The `default` mode can supply four operations, e.g. `f64mul` (multiplication of a 64-bit floating-point number) with a 1/1000 opertion-per-clock ratio. In other words, it takes 1000 clock cycles to perform one `f64mul` operation.
It is **very important** that the supplied operation names *match* the required operation names from the functionality in case you plan to make your own input models.
The proof-of-concept Simulink identification rule already writes the Simulink model required operations in terms of the `default` mode shown, so it should not be a problem if you "copy-paste-modify".
We retake this observation in the [SDF use case](#sdf-applications).

To run the test, you can supply the required inputs file via:

    .\orchestrator.exe --run-path run_simudt .\simulink_devicetree\runtimes.yaml .\simulink_devicetree\test_model.slx .\simulink_devicetree\tile0.dts .\simulink_devicetree\tile1.dts # Windows

    ./orchestrator --run-path run_simudt ./simulink_devicetree/runtimes.yaml ./simulink_devicetree/test_model.slx ./simulink_devicetree/tile0.dts ./simulink_devicetree/tile1.dts # Linux

Note that this test might take a good amount of time to run due to the huge overhead that starting Matlab/Simulink brings: Matlab takes a lot of time to process every request to load `slx` file in its memory. This case study can take up to 6 minutes due to this external sluggishness. We highly recommend using the `-p` parallel capabilities to run a Matlab session in parallel with the Scala modules.

After all stages are finished, we can check the `run_simudt\reversed` to find a `fiodl` file `reversed_0_YyyYyYyIdentificationModule.fidl` with only the *minimal* information required to represent the results of the exploration, i.e. the process nodes and the parts of the platform they map to.
For instance,

    vertex "os0"
    [platform::runtime::AbstractScheduler, visualization::GreyBox]
    (contained)
    {}
    ...
    vertex "test_model/Sum1"
    [decision::MemoryMapped, decision::Scheduled, visualization::Visualizable]
    (allocationHosts, mappingHosts, schedulers)
    {}
    ...
    edge [decision::AbstractScheduling] from "test_model/Sum1" port "schedulers" to "os0" 

As mentioned in the companion paper, this avoids creating a full-fledged M2M transformation from Simulink to OurMDETool.
Naturally, in the future new reverse identification rules can be created so that Simulink models are back-annotated with the results presented in the `fiodl` file, instead of creating the `fiodl` file ditto.

### Extra) Composing and combining

Since composability is a key elements of DSI and IDeSyDe, it is only natural to expect that combining the previous two case studies is possible.
In fact, it is!
The reasonable combination, in this case, is the `case_study.fiodl` of the [avionics case study](#1-avionics) with
*only* the Simulink model of the [previous case study](#2-simulink-and-device-tree).
This is because the avionics `fiodl` file already contains all the information for the DSE: platform and functionality. Therefore, adding the DeviceTree specification to this combination would imply that two disconnected platforms exist side-by-side.
The suggested combination, more interestingly, requires the functionalities to share the avionics platform.

To run this combination, we only now have to pass the input files accordingly and ask for *1 solution*:

     .\orchestrator.exe --x-max-solutions 1 --run-path run_combined .\avionics\flight-information-function\case_study.fiodl .\simulink_devicetree\test_model.slx # Windows

     ./orchestrator --x-max-solutions 1 --run-path run_combined ./avionics/flight-information-function/case_study.fiodl ./simulink_devicetree/test_model.slx # Linux

The reason for asking for only 1 solution is that this problem instance is not easy.
One solution that satisfies all the timing requirements of both the avionics use case and the Simulink specification can be found rather quickly;
after that, it takes a very long time to prove that this solution is the optimal, or find another that uses less cores. You check this yourself by directly doing:

    .\orchestrator.exe -v DEBUG --run-path run_combined .\avionics\flight-information-function\case_study.fiodl .\simulink_devicetree\test_model.slx # Windows

     ./orchestrator -v DEBUG --run-path run_combined ./avionics/flight-information-function/case_study.fiodl ./simulink_devicetree/test_model.slx # Linux

And waiting for a second solution to appear.

In any case, if we inspect the `run_combined\reversed` folder, we can find a `fiodl` file that contains elements from both case studies such as:

    vertex "AltitudeCalc"
    [decision::MemoryMapped, decision::Scheduled, execution::CommunicatingTask, execution::LoopingTask, visualization::GreyBox, visualization::Visualizable]
    (activated, activators, allocationHosts, contained, initSequence, loopSequence, mappingHosts, schedulers)
    ...
    vertex "test_model/Gain"
    [decision::MemoryMapped, decision::Scheduled, visualization::Visualizable]
    (allocationHosts, mappingHosts, schedulers)
    ...
    edge [decision::AbstractScheduling] from "test_model/Gain" port "schedulers" to "CPM1_Core1_Runtime.CMP1_Core1_FP_Runtime_Scheduler" 
    ...
    edge [decision::AbstractScheduling] from "AltitudeCalc" port "schedulers" to "CPM1_Core1_Runtime.CMP1_Core1_FP_Runtime_Scheduler" 

which means that both models have been mapped together to the same platform, respecting their timing properties!

We note that you could naturally make two `fiodl` files out of `case_study.fiodl`, one containing the functionality and another containing the platform. If you do so, you can then use only the functionality with the DeviceTree platform specification.

### 3) Synchronous data flow (SDF)

The functionality in this case study is given by [SDF3 files](https://www.es.ele.tue.nl/sdf3/manuals/xml/sdf/).
You can open any `sdf.xml` or `hsdf.xml` file in the `sdf` folder to inspect how they look like.
For example, here is `sdf\small_explainable\a_sobel.hsdf.xml`:

    <?xml version="1.0"?>
    <sdf3 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="1.0" type="sdf" xsi:noNamespaceSchemaLocation="http://www.es.ele.tue.nl/sdf3/xsd/sdf3-sdf.xsd">
    <applicationGraph name="a_sobel">
        <sdf name="a_sobel" type="SOBEL">
            <actor name="get_pixel" type="getPixel">
                <port name="p0_0" type="out" rate="1"/>
                <port name="p0_1" type="out" rate="1"/>
                <port name="p0_2" type="out" rate="1"/>
                <port name="p0_3" type="out" rate="1"/>
                ...
            </actor>
        </sdf>
        <sdfProperties>
            <actorProperties actor="get_pixel">
                <processor type="proc" default="true">
                <executionTime time="320"/>
                <memory>
                    <stateSize max="2"/>
                </memory>
                </processor>
                ...
            </actorProperties>
        </sdfProperties>
        ...
    <applicationGraph>

In this file, we have the definitions for the SDF topology, as you can notice for the `get_pixel` actor being shown; but we also have a "default" characterization of each actor (and channel).
For example, it says in the example that `get_pixel` takes 320 units of time in a `proc` machine from start to finish.

With this we return to the discussion for the [operations per cycle initiated in the DeviceTree use case](#2-simulink-and-device-tree).
The processor type in this file is translated to an *implementation* of a the actor, which is the functional equivalent of the platform *mode*.
However, the actual operation time that is extracted from this specification is *all*.
Therefore, `get_pixel`, according to this specification, requires 320 units of "all" in its "proc" implementation.
If you now double check the [DeviceTree section](#2-simulink-and-device-tree), you'll see that there is a "mode" that provides 1/10 "all" operations per clock cycle, i.e. 1 "all" operation every 10 clock cycles.
This case study is the reason such abstract representation exist, as SDF3 does not allow for a finer-grain specification of the operations which are demanded from the platform the actor is being implemented for.

In any case, we can proceed as expected try to you the use SDF use cases.
Note that there are already a handful of combinations cleanly put in folder for the sake of reviewing,
but you can combine the input SDFs at your leisure.

The simplest one is:

    .\orchestrator.exe --run-path run_sdf_small .\sdf\small_explainable\a_sobel.hsdf.xml .\sdf\small_explainable\bus_small_platform.fiodl # Windows

    ./orchestrator --run-path run_sdf_small ./sdf/small_explainable/a_sobel.hsdf.xml ./sdf/small_explainable/bus_small_platform.fiodl # Linux

Then, as usual, you can check the resulting `fiodl` files in the `reversed` folder, like the very first solution found:

    systemgraph {
        vertex "get_pixel"
        [decision::MemoryMapped, decision::Scheduled, decision::results::AnalyzedActor, impl::InstrumentedExecutable, moc::sdf::SDFActor, visualization::Visualizable]
        (allocationHosts, combFunctions, mappingHosts, p0_0, p0_1, p0_2, p0_3, p0_4, p0_5, p1_0, p1_1, p1_2, p1_3, p1_4, p1_5, schedulers)
        {
            "throughputInSecsNumerator": 2832558839419957_l,
            "throughputInSecsDenominator": 2199023255552_l,
            "production": {
                "p0_2": 1_i,
                "p1_1": 1_i,
                "p0_1": 1_i,
                "p1_0": 1_i,
                "p0_0": 1_i,
                "p1_5": 1_i,
                "p0_5": 1_i,
                "p1_4": 1_i,
                "p0_4": 1_i,
                "p1_3": 1_i,
                "p0_3": 1_i,
                "p1_2": 1_i
            },
            ...
        }
        ...
        vertex "micro_blaze_os0"
        [decision::Allocated, decision::platform::runtime::AllocatedSingleSlotSCS, platform::runtime::AbstractScheduler, platform::runtime::StaticCyclicScheduler, visualization::Visualizable]
        (allocationHosts)
        {
            "entries": [
                "get_pixel",
                "gy",
                "gx",
                "abs"
            ]
        }
    ...
    }

where the scheduler `micro_blaze_os0` shows the list schedule computed for this solution.

Here's a list of the commands to run the other SDF tests of [Experiment IV in this paper](https://dl.acm.org/doi/10.1145/3133210) for the sake of easiness:

(1) RASTA and JPEG

    .\orchestrator.exe --run-path run_RaJp .\sdf\RaJp\c_rasta.hsdf.xml .\sdf\small_explainable\bus_small_with_hwacc.fiodl .\sdf\RaJp\d_jpegEnc1.sdf.xml # Windows

    ./orchestrator --run-path run_RaJp ./sdf/RaJp/c_rasta.hsdf.xml ./sdf/small_explainable/bus_small_with_hwacc.fiodl ./sdf/RaJp/d_jpegEnc1.sdf.xml # Linux

(2) Sobel, RASTA and JPEG

    .\orchestrator.exe --run-path run_SoRaJp .\sdf\SoRaJp\c_rasta.hsdf.xml .\sdf\small_explainable\bus_small_with_hwacc.fiodl .\sdf\SoRaJp\a_sobel.hsdf.xml .\sdf\SoRaJp\d_jpegEnc1.sdf.xml # Windows

    ./orchestrator --run-path run_SoRaJp ./sdf/SoRaJp/c_rasta.hsdf.xml  ./sdf/small_explainable/bus_small_with_hwacc.fiodl ./sdf/SoRaJp/a_sobel.hsdf.xml ./sdf/SoRaJp/d_jpegEnc1.sdf.xml # Linux

(3) Sobel, SuSAN and JPEG

    .\orchestrator.exe --run-path run_SoSuJp .\sdf\SoSuJp\b_susan.hsdf.xml .\sdf\small_explainable\bus_small_with_hwacc.fiodl .\sdf\SoSuJp\a_sobel.hsdf.xml .\sdf\SoSuJp\d_jpegEnc1.sdf.xml # Windows

    ./orchestrator --run-path run_SoSuJp ./sdf/SoSuJp/b_susan.hsdf.xml  ./sdf/small_explainable/bus_small_with_hwacc.fiodl ./sdf/SoSuJp/a_sobel.hsdf.xml ./sdf/SoSuJp/d_jpegEnc1.sdf.xml # Linux

(4) Sobel, SuSAN and RASTA

    .\orchestrator.exe --run-path run_SoSuRa .\sdf\SoSuRa\b_susan.hsdf.xml .\sdf\small_explainable\bus_small_with_hwacc.fiodl .\sdf\SoSuRa\a_sobel.hsdf.xml .\sdf\SoSuRa\c_rasta.hsdf.xml # Windows

    ./orchestrator --run-path run_SoSuRa ./sdf/SoSuRa/b_susan.hsdf.xml  ./sdf/small_explainable/bus_small_with_hwacc.fiodl ./sdf/SoSuRa/a_sobel.hsdf.xml ./sdf/SoSuRa/c_rasta.hsdf.xml # Linux

(5) SuSAN, RASTA and JPEG

    .\orchestrator.exe --run-path run_SuRaJp .\sdf\SuRaJp\b_susan.hsdf.xml .\sdf\small_explainable\bus_small_with_hwacc.fiodl .\sdf\SuRaJp\a_sobel.hsdf.xml .\sdf\SuRaJp\d_jpegEnc1.sdf.xml # Windows

    ./orchestrator --run-path run_SuRaJp ./sdf/SuRaJp/b_susan.hsdf.xml  ./sdf/small_explainable/bus_small_with_hwacc.fiodl ./sdf/SuRaJp/a_sobel.hsdf.xml ./sdf/SuRaJp/d_jpegEnc1.sdf.xml # Linux

(6) Sobel, SuSAN, RASTA and JPEG

    .\orchestrator.exe --run-path run_SoSuRaJp .\sdf\SoSuRaJp\a_sobel.hsdf.xml .\sdf\SoSuRaJp\b_susan.hsdf.xml .\sdf\small_explainable\bus_small_with_hwacc.fiodl .\sdf\SoSuRaJp\a_sobel.hsdf.xml .\sdf\SoSuRaJp\d_jpegEnc1.sdf.xml # Windows

    ./orchestrator --run-path run_SoSuRaJp ./sdf/SoSuRaJp/a_sobel.hsdf.xml ./sdf/SoSuRaJp/b_susan.hsdf.xml  ./sdf/small_explainable/bus_small_with_hwacc.fiodl ./sdf/SoSuRaJp/a_sobel.hsdf.xml ./sdf/SoSuRaJp/d_jpegEnc1.sdf.xml # Linux

And, to showcase the composability of the approach and the tool, we should not forget the composing the SDF applications with the DeviceTree is possible! Try it out:

    .\orchestrator.exe --run-path run_combi_sdf_dt .\sdf\small_explainable\a_sobel.hsdf.xml .\simulink_devicetree\runtimes.yaml .\simulink_devicetree\tile0.dts .\simulink_devicetree\tile1.dts # Windows

    ./orchestrator --run-path run_combi_sdf_dt ./sdf/small_explainable/a_sobel.hsdf.xml ./simulink_devicetree/runtimes.yaml ./simulink_devicetree/tile0.dts ./simulink_devicetree/tile1.dts # Linux

The resulting `fiodl` file will contain some elements of the DeviceTree specification, at least enough to express the mapped and scheduled solution, e.g. `reversed_0...fiodl`:

    vertex "os0"
    [decision::platform::runtime::AllocatedSingleSlotSCS, platform::runtime::AbstractScheduler]
    ()
    {
        "entries": [
            "get_pixel",
            "gy",
            "gx"
        ]
    }
    ...
    vertex "os1"
    [decision::platform::runtime::AllocatedSingleSlotSCS, platform::runtime::AbstractScheduler]
    ()
    {
        "entries": [
            "abs"
        ]
    }

Where the self-timed list schedules for each "OS" (assumed to be bare-metal in actuality) is shown.

## Creating new design models

It is possible to create new design models so that IDeSyDe can identify its design space and solve it.
The demonstrators provided already contain the necessary information that one could replicate to create new design models,
but here is some extra information that can be given while maintaining anonymosity.

### SDF applications

Because of the OurMDETools framework, IDeSyDe directly consume [SDF3](https://www.es.ele.tue.nl/sdf3/manuals/xml/sdf/) XML specification files.
This applies especially to the application graphs. One must be careful when specifying the computational requirements of each actor, since
the identification rules in IDeSyDe must be able to match them with the computational provisions in the platform decision models available.

SDF applications can also be specified directly as `fiodl` files of the OurMDETools framework, but these are more general than SDF3, so we opt
to use SDF3 files directly for the sake of comprehension.

### Periodic Workload

The periodic workload design model is always given in `fiodl` files.
The abstraction follows a separation between triggering and data propagation.
That is, the activation between different processes follows a rather intuitive multi-rate synchronous ideas, like:

    Task1 -> Task2

Implies that every time Task1 finishes, Task2 must start and finish sometime in the future.
The notable difference here is the possibility of using upsampling and downsampling,

    Task1 -> Upsample by 10 -> Task2
or
    Task1 -> Downsample by 10 -> Task2

Now every 10th Task2 executes *after* Task1, or, Task2 executes *after* every 10th execution of Task1.
Note the after, which respects the direction of the activation flow between the processes.

The data propagation is decoupled with this, though still living inside the same "graph" design model:

    Task1 -> Data -> Task2

Now Task1 writes to Data and Task2 reads from Data, but it is not specified in which order, priority or even
the different rates of reading and write. Naturally, it is a waste to overwrite the data element without
reading it; keep in mind that this is a *data propagation graph*, which means that Data can come and go
from multiple producers and consumers, regardless of semantic coherence.

    Task1 -> Data -> Task2
    Task3 ---^  |----->Task4
                |---->Task5

The same graph remark idea applies to the activation discussion, where the name of the activation flow is called *trigger graph*.

In summary, if one would like to have the classic Liu & Layland's independent periodic task design model, it is enough
to not make any triggering connections. Likewise, the tasks can propagate zero data, and still activate each other.

The `flight-information-function` contains a design model without any triggering between the tasks, with only data propagation;
The `radar-aesa-function` contains a design model with data propagation *and* triggering; which can be used as the template for new models.

### Platform

The platform used here is quite coarse-grain, but good enough to represent architectures at a system-level (according to [Gajski](https://www.bokus.com/bok/9781441905031/embedded-system-design/)).
You can have processing elements, memory elements and communication elements. They must be connected so that
every processing element reaches (directly or through a network of communication elements) a memory element.
If the memory elements are not private, the platform is identified to be a "tiled" architecture, and can be used for SDF solutions;
otherwise, it is simply a shared memory architecture.

Basically all `fiodl` files contain an example of how the platform is given.

### KGT files

Because of the OurMDETools framework, `kgt` files of the [KIELER](https://www.rtsys.informatik.uni-kiel.de/en/archive/kieler/welcome-to-the-kieler-project)
framework can be generated that enables one to see a bit better the `fiodl` files.
For the sake of blind-reviewing, the tool which performs this conversion is unfortunately not included in this repository, but some of the demonstrators
given are the results of the tool.

You can visualize it by opening any of the files with the [KIELER extensions for visual code](https://marketplace.visualstudio.com/items?itemName=kieler.keith-vscode), which will then open a diagram view of the KGT file.
