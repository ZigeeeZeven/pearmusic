cmake_minimum_required(VERSION 3.22.1)

project("pearmusic-native")

add_library(
        pearmusic-native
        SHARED
        native-lib.cpp)

# Find Oboe
find_package(oboe REQUIRED CONFIG)

target_link_libraries(
        pearmusic-native
        log
        android
        oboe::oboe)
