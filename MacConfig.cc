#include <ApplicationServices/ApplicationServices.h>
#include "MacConfig.h"
JNIEXPORT jboolean JNICALL Java_MacConfig_isShiftDownNative(JNIEnv *env, jclass c) {
  CGEventFlags f = CGEventSourceFlagsState(kCGEventSourceStateCombinedSessionState);
  return (f & kCGEventFlagMaskShift) != 0;
}

JNIEXPORT jstring JNICALL Java_MacConfig_resolveAlias(JNIEnv *env, jclass c, jstring s) {
  jboolean isCopy;
  const char *alias = env->GetStringUTFChars(s, &isCopy);

  // make an FSRef from passed in path
  FSRef r;
  if (FSPathMakeRef((UInt8*)alias, &r, NULL)) {
    printf("can't make FSRef\n");
    return NULL;
  }

  // mark it as an alias file
  FSCatalogInfo catalogInfo;
  if (FSGetCatalogInfo(&r, kFSCatInfoFinderInfo, &catalogInfo, NULL, NULL, NULL)) {
    printf("can't get catalog info\n");
    return NULL;
  }
  ((FileInfo*)catalogInfo.finderInfo)->finderFlags |= kIsAlias;
  if (FSSetCatalogInfo(&r, kFSCatInfoFinderInfo, &catalogInfo)) {
    printf("can't set catalog info\n");
    return NULL;
  }
  
  // resolve it to the target file
  Boolean targetIsFolder;
  Boolean wasAliased;
  if (FSResolveAliasFile(&r, true, &targetIsFolder, &wasAliased)) {
    printf("can't resolve\n");
    return NULL;
  }
  
  // grab target file path & return it
  char path[1024];
  if (FSRefMakePath(&r, (UInt8*)path, 1024)) {
    printf("can't get path string\n");
    return NULL;
  }
  return env->NewStringUTF(path);
}
