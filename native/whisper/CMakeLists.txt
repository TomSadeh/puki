# libpuki_whisper.so: whisper.cpp plus a small JNI wrapper, for the device brain's speech
# recognition. Built by build.py (which fetches whisper.cpp at a pinned commit).
cmake_minimum_required(VERSION 3.14)
project(puki_whisper C CXX)

set(WHISPER_DIR "" CACHE PATH "whisper.cpp source checkout")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
add_subdirectory(${WHISPER_DIR} whisper)

add_library(puki_whisper SHARED
    puki_whisper.cpp
    ${WHISPER_DIR}/examples/common.cpp
    ${WHISPER_DIR}/examples/common-ggml.cpp)
target_include_directories(puki_whisper PRIVATE
    ${WHISPER_DIR}/examples ${WHISPER_DIR}/include ${WHISPER_DIR}/ggml/include)
target_link_libraries(puki_whisper PRIVATE whisper ggml log)
target_link_options(puki_whisper PRIVATE -Wl,--gc-sections -s)
