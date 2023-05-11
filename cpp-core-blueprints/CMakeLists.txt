find_package(nlohmann_json CONFIG REQUIRED)
find_package(cxxopts CONFIG REQUIRED)

add_library(core STATIC)
set_target_properties(core PROPERTIES LINKER_LANGUAGE CXX)

target_link_libraries(main PRIVATE cxxopts::cxxopts)
target_link_libraries(core PRIVATE nlohmann_json::nlohmann_json)

target_include_directories(core PUBLIC "include")