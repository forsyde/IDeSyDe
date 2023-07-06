*** Settings ***
Library     ./TestRobot.py


*** Variables ***
${ShouldCompile}    ${False}
@{casesPaths}       @{EMPTY}


*** Test Cases ***
Test each benchmark for up to one or no solution
    IF    ${ShouldCompile}    TestRobot.Set Up
    ${casesPaths} =    TestRobot.Get Test Cases
    FOR    ${casePath}    IN    @{casesPaths}
        TestRobot.Test Solution    ${casePath}    ${False}
    END
    TestRobot.Tear Down
