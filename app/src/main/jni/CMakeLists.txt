
cmake_minimum_required(VERSION 3.6)

set(pathToProject /media/bobcat/DATA/Software_Projects/Edgy)
set(OpenCV_DIR /opt/OpenCV-android-sdk/sdk/native/jni/)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=gnu++11")

find_package(OpenCV REQUIRED)
message(STATUS "$$$ opencv found: ${OpenCV_LIBS}")
include_directories(${CMAKE_CURRENT_SOURCE_DIR} ${OpenCV_DIR}/include/)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds it for you.
# Gradle automatically packages shared libraries with your APK.
add_library(
        # Sets the name of the library.
        ImageProcessing

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        # Associated headers in the same location as their source
        # file are automatically included.
        ImageProcessing.cpp)


add_library( lib_opencv SHARED IMPORTED )
set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION ${pathToProject}/app/src/main/jniLibs/${ANDROID_ABI}/libopencv_java4.so)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in the
# build script, prebuilt third-party libraries, or system libraries.
target_link_libraries(
        # Specifies the target library
        ImageProcessing

        # OpenCV lib
        lib_opencv

        android)
