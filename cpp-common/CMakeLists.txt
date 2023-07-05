add_library(common STATIC)
set_target_properties(common PROPERTIES LINKER_LANGUAGE CXX)

target_link_libraries(common PRIVATE core)

target_include_directories(common PUBLIC "include")