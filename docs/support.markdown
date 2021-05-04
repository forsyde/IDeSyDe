---
layout: default
title: Support
nav_order: 4
---

This page outlines the combinations of (design) models with explorers that can be used
to arrive at certain conlusions and results.

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
        <td>Abstract Time-Triggered Architecture</td>
        <td>worst case execution and communication times</td>
        <td>Location requirements</td>
        <td>Minizinc (any backend): <br>A time triggered schedule that optimized throughput for worst cases or "impossible to implement".</td>
        <td>
            <a href="https://github.com/forsyde/forsyde-io/blob/master/examples/sobel2mpsoc-reducedCores.forxml">Sobel + MPSoC, Reduced</a>
            <a href="https://github.com/forsyde/forsyde-io/blob/master/examples/sobel2mpsoc.forxml">Sobel + MPSoC</a>
            <a href="https://github.com/forsyde/forsyde-io/blob/master/examples/FlightInformationFunction.forxml">FlightInformationFunction SDF</a>
        </td>
    </tr>
</table>