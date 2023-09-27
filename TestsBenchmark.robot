*** Settings ***
Library     ./IDeSyDeLibrary.py


*** Variables ***
${ShouldCompile}    ${False}


*** Test Cases ***
Test for examples_and_benchmarks/CODES_ISSS_2023/RaJp
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/RaJp
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/CODES_ISSS_2023/SoRaJp
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SoRaJp
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/CODES_ISSS_2023/SoSuJp
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SoSuJp
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/CODES_ISSS_2023/SoSuRa
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SoSuRa
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/CODES_ISSS_2023/SoSuRaJp
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SoSuRaJp
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/CODES_ISSS_2023/SuRaJp
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SuRaJp
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/combination-50-1_5625
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/combination-50-1_5625
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/combination-55-3_125
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/combination-55-3_125
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/flight-information-function
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/flight-information-function-50
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function-50
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/flight-information-function-55
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function-55
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/flight-information-function-58_75
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/DASC2023/flight-information-function-58_75
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/flight-information-function-62_5
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/DASC2023/flight-information-function-62_5
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/flight-information-function-75
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function-75
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-12_5
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-12_5
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-25
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-25
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-3_125
    ${NumFound} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-3_125
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-50
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-50
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-6_625
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-6_625
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario2
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario2
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/novel/sobel_and_easy_avionics
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/novel/sobel_and_easy_avionics
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/PANORAMA/flight-information-function
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/PANORAMA/flight-information-function
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/PANORAMA/radar-aesa-function
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/PANORAMA/radar-aesa-function
    Should Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/small_and_explainable/sobel_and_2core_devicetree
    ${NumFound} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/small_and_explainable/sobel_and_2core_devicetree
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/small_and_explainable/sobel_to_bus_multicore
    ${NumFound} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/small_and_explainable/sobel_to_bus_multicore
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/small_and_explainable/yuhan_zhang_thesis
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/small_and_explainable/yuhan_zhang_thesis
    Should Not Be Equal As Integers    ${NumFound}    0

Test for examples_and_benchmarks/EARLY_BIRD/corrected_summer_project_2023
    [Tags]    slow
    ${NumFound} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/EARLY_BIRD/corrected_summer_project_2023
    Should Not Be Equal As Integers    ${NumFound}    0
