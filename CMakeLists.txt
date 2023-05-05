cmake_minimum_required(VERSION 3.20)
project(IDeSyDe)

set(CMAKE_CXX_STANDARD 17)

find_package(nlohmann_json CONFIG REQUIRED)

# add_executable(example example.cpp)
# target_link_libraries(example PRIVATE nlohmann_json::nlohmann_json)

add_subdirectory(cpp-core)
