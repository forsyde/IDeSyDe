set(HEADER_FILES
        include/core.hh include/headers.hh)

set(SOURCE_FILES )

add_library(core STATIC ${SOURCE_FILES} ${HEADER_FILES})

target_link_libraries(core PRIVATE nlohmann_json::nlohmann_json)
target_include_directories(core PUBLIC include)