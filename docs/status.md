<!-- ---
layout: default
title: Support
nav_order: 4
parent: Usage
--- -->

# Current status

This page outlines the combinations of (design) models with explorers that are part
of the distributed tool.

## Applications

* @scaladoc[Synchronous dataflow graphs](idesyde.identification.common.models.sdf.SDFApplication)
* @scaladoc[Communicating periodic tasks](idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload)

<table>
    <tr>
        <th>Application model</th>
        <th>Platform model</th>
        <th>Properties considered</th>
        <th>Constraints accepted</th>
        <th>Explorer(s) and their results</th>
        <th>Examples</th>
    </tr>
    <tr>
        <td>(H)SDF</td>
        <td>Tile-Based Architecture</td>
        <td>worst case execution and communication times</td>
        <td>--</td>
        <td>Choco (built-in): <br>A statically self-timed schedule optimized for worst case throughput or "impossible to implement".</td>
        <td>
            <a href="https://github.com/forsyde/IDeSyDe/tree/master/tests/models/sdf3">SDF3 models used as input after conversion</a>
            <a href="https://github.com/forsyde/IDeSyDe/blob/master/tests/src/test/scala/sdf/SDFOnTileNoCUseCaseWithSolution.scala">Tests cases with SDF and platforms built on memory</a>
        </td>
    </tr>
    <tr>
        <td>PRELUDE-like task model</td>
        <td>Explicit-memory Architecture with partitioned fixed priority preemptive schedulers</td>
        <td>worst case execution and communication times</td>
        <td>--</td>
        <td>Choco (built-in): <br>A mapping optimized for lest number of used cores or "impossible to implement".</td>
        <td>
            <a href="https://github.com/forsyde/panorama-kth-demonstrator">PANORAMA ITEA3 project demonstrator with AMALTHEA being converted as input</a>
        </td>
    </tr>
</table>