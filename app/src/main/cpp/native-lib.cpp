#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_derelictvesseldev_pi_1client_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Testing";
    return env->NewStringUTF(hello.c_str());
}