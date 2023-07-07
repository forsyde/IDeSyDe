*** Settings ***
Library     ./IDeSyDeLibrary.py


*** Variables ***
${ShouldCompile}    ${False}


*** Test Cases ***
Test for examples_and_benchmarks/CODES_ISSS_2023/RaJp
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/RaJp
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/CODES_ISSS_2023/SoRaJp
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SoRaJp
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/CODES_ISSS_2023/SoSuJp
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SoSuJp
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/CODES_ISSS_2023/SoSuRa
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SoSuRa
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/CODES_ISSS_2023/SoSuRaJp
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SoSuRaJp
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/CODES_ISSS_2023/SuRaJp
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/CODES_ISSS_2023/SuRaJp
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/combination-50-1_5625
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/combination-50-1_5625
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/combination-55-3_125
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/combination-55-3_125
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/flight-information-function
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/flight-information-function-50
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function-50
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/flight-information-function-55
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function-55
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/flight-information-function-58_75
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function-58_75
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/flight-information-function-62_5
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function-62_5
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/flight-information-function-75
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/flight-information-function-75
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-12_5
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-12_5
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-25
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-25
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-3_125
    ${Explored} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-3_125
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-50
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-50
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-6_625
    [Tags]    slow
    ${Explored} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario1-6_625
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/DASC2023/radar-aesa-function-scenario2
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/DASC2023/radar-aesa-function-scenario2
    Should Be Empty    ${Explored}

Test for examples_and_benchmarks/novel/sobel_and_easy_avionics
    [Tags]    slow
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/novel/sobel_and_easy_avionics
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/PANORAMA/flight-information-function
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/PANORAMA/flight-information-function
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/PANORAMA/radar-aesa-function
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/PANORAMA/radar-aesa-function
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/small_and_explainable/sobel_and_2core_devicetree
    ${Explored} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/small_and_explainable/sobel_and_2core_devicetree
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/small_and_explainable/sobel_to_bus_multicore
    ${Explored} =    IDeSyDeLibrary.Try Explore
    ...    examples_and_benchmarks/small_and_explainable/sobel_to_bus_multicore
    Should Not Be Empty    ${Explored}

Test for examples_and_benchmarks/small_and_explainable/yuhan_zhang_thesis
    [Tags]    slow
    ${Explored} =    IDeSyDeLibrary.Try Explore    examples_and_benchmarks/small_and_explainable/yuhan_zhang_thesis
    Should Not Be Empty    ${Explored}
