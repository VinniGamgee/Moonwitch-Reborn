// SPDX-License-Identifier: GPL-3.0-or-later
#include <mutex>
#include <string>
#include <jni.h>
#include "common/android/android_common.h"

#ifdef MOONWITCH_PGO_GENERATE
extern "C" void __llvm_profile_set_filename(const char*);
extern "C" int __llvm_profile_write_file(void);
namespace {
std::mutex profile_mutex;
std::string profile_filename;
}
#endif

extern "C" JNIEXPORT jboolean JNICALL
Java_org_yuzu_yuzu_1emu_NativeLibrary_isPgoTraining(JNIEnv*, jobject) {
#ifdef MOONWITCH_PGO_GENERATE
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_yuzu_yuzu_1emu_NativeLibrary_configurePgo(JNIEnv* env, jobject, jstring filename) {
#ifdef MOONWITCH_PGO_GENERATE
    std::scoped_lock lock{profile_mutex};
    if (profile_filename.empty()) {
        profile_filename = Common::Android::GetJString(env, filename);
        __llvm_profile_set_filename(profile_filename.c_str());
    }
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_yuzu_yuzu_1emu_NativeLibrary_writePgoProfile(JNIEnv*, jobject) {
#ifdef MOONWITCH_PGO_GENERATE
    std::scoped_lock lock{profile_mutex};
    return !profile_filename.empty() && __llvm_profile_write_file() == 0;
#else
    return JNI_FALSE;
#endif
}
