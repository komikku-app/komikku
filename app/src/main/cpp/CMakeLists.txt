cmake_minimum_required(VERSION 3.22.1)
project("aiupscaler")

add_library(
        aiupscaler
        SHARED
        native-lib.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
        aiupscaler
        ${log-lib}
        ${jnigraphics-lib}
)
