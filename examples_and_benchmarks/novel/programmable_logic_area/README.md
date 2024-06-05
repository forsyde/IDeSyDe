
# Test cases for hardware-software co-design DSE

## TC1
- **Goal**: Make sure that an actor with only a hardware implementation maps to the FPGA, while another actor only maps to the CPU as it only specifies a software implementation.
- **Platform**: `mpsoc.fiodl`
- **Application**: `tc1.fiodl`

## TC2
- **Goal**: Make sure that applications map to hardware based on favorable hardware implementations in comparison to software implementations.
- **Platform**: `mpsoc.fiodl`
- **Application**: `tc2.fiodl`

## TC3
- **Goal**: Make sure that actor specifications, only mappable to hardware, do not yield DSE solutions if they exceed the resource limitations of the FPGA.
- **Platform**: `mpsoc.fiodl`
- **Application**: `tc3.fiodl`

## TC4
- **Goal**: Make sure that both actors map to either the FPGA (PL-side) or the CPUs (PS-side), although one actor is more favorable to be implemented in hardware, the other actor is more favorable to be implemented in software. This should be the case since communication between the PL-side and PS-side is heavily impaired.
- **Platform**: `mpsoc_impaired_ps_pl_communication.fiodl`
- **Application**: `tc4.fiodl` (same as `tc5.fiodl` but other name)

## TC5
- **Goal**: Same application specification as TC4 but with the normal platform specificaton.
- **Platform**: `mpsoc.fiodl`
- **Application**: `tc5.fiodl` (same as `tc4.fiodl` but other name)

## Realistic Application
Realistic raw 4K video stream processing application with a hardware-software co-design DSE problem.

### Both hardware and software implementation alternatives for the actors
- **Platform**: `mpsoc.fiodl`
- **Application**: `4k_streaming_hwsw.fiodl`

### Only software implementation alternatives for the actors
- **Platform**: `mpsoc.fiodl`
- **Application**: `4k_streaming_only_sw.fiodl`
