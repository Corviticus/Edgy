# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /opt/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keep class org.opencv.** { *; }

-dontwarn android.support.v4.**
-dontwarn android.support.v7.**

-keep class android.support.v7.** { *; }
-keep interface android.support.v7.** { *; }
