cmake_minimum_required(VERSION 3.22)

if(POLICY CMP0177)
  cmake_policy(SET CMP0177 NEW)
endif()

project(remotehub-rdp LANGUAGES C VERSION 1.0.0)

set(MODULE_NAME remotehub-rdp)

if(NOT FREERDP_SOURCE_DIR)
  message(FATAL_ERROR "FREERDP_SOURCE_DIR must point at a FreeRDP 3.33.1 checkout")
endif()

include(GNUInstallDirs)
list(APPEND CMAKE_MODULE_PATH "${FREERDP_SOURCE_DIR}/client/Android/cmake")
list(APPEND CMAKE_MODULE_PATH "${FREERDP_SOURCE_DIR}/cmake")

include(AndroidHelpers)
detect_ndk_root(NDK_ROOT)
detect_ndk_host_platform(NDK_HOST_PLATFORM)
get_android_api_level(NDK_API_LEVEL)

set(WITH_FFMPEG OFF CACHE BOOL "" FORCE)
set(WITH_OPENH264 ON CACHE BOOL "" FORCE)
set(WITH_OPUS OFF CACHE BOOL "" FORCE)
set(WITH_PNG OFF CACHE BOOL "" FORCE)
set(WITH_WEBP OFF CACHE BOOL "" FORCE)
set(WITH_JPEG OFF CACHE BOOL "" FORCE)
set(WITH_CJSON OFF CACHE BOOL "" FORCE)
set(WITH_OPENSSL ON CACHE BOOL "" FORCE)
set(WITH_MEDIACODEC OFF CACHE BOOL "" FORCE)

include(ExternalDeps)

add_library(${MODULE_NAME} SHARED rdp_bridge.c)
add_dependencies(${MODULE_NAME} copyLibs)

find_library(log-lib log)
find_library(dl-lib dl)
find_library(jnigraphics-lib jnigraphics)

target_compile_options(${MODULE_NAME} PRIVATE -Wall -Wextra -Wno-unused-parameter -fvisibility=hidden)
target_link_libraries(${MODULE_NAME} PRIVATE freerdp3-lib freerdp-client3-lib winpr3-lib
                      ${log-lib} ${dl-lib} ${jnigraphics-lib})

install(TARGETS ${MODULE_NAME} LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR})
