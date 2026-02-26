#include <jni.h>
#include "nitrolocationtrackingOnLoad.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
  return margelo::nitro::nitrolocationtracking::initialize(vm);
}
