set(HEADER_FILES
        include/core.hh include/headers.hh)

set(SOURCE_FILES )


add_library(core STATIC ${SOURCE_FILES} ${HEADER_FILES})
set_target_properties(core PROPERTIES LINKER_LANGUAGE CXX)

target_link_libraries(core PRIVATE nlohmann_json nlohmann_json::nlohmann_json)