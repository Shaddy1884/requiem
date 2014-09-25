#include <windows.h>
#include <shellapi.h>
#include <shlobj.h>
#include <sys/stat.h>
#include "WindowsConfig.h"
#include "md5.h"

JNIEXPORT jboolean JNICALL Java_WindowsConfig_trashNative(JNIEnv *env, jclass c, jstring s) {
  // prepare argument from Java String.
  int len = env->GetStringLength(s);
  const jchar *t = env->GetStringChars(s, NULL);
  WCHAR *buf = (WCHAR*)malloc((len + 2) * sizeof(WCHAR));
  for (int i = 0; i < len; i++) buf[i] = t[i];
  buf[len] = 0;
  buf[len + 1] = 0;
  
  // call move-to-recycle-bin function.
  SHFILEOPSTRUCTW x;
  ZeroMemory(&x, sizeof(x));
  x.wFunc = FO_DELETE;
  x.pFrom = buf;
  x.fFlags = FOF_ALLOWUNDO | FOF_NOCONFIRMATION | FOF_SILENT;
  int err = SHFileOperationW(&x);
  
  env->ReleaseStringChars(s, t);
  free(buf);
  return err == 0;
}

static byte *read_file(const char *filename, int *filesize) {
  FILE *f = fopen(filename, "rb");
  fseek(f, 0, SEEK_END);
  *filesize = ftell(f);
  fseek(f, 0, SEEK_SET);
  byte *data = new byte[*filesize];
  fread(data, 1, *filesize, f);
  fclose(f);
  return data;
}

JNIEXPORT jbyteArray JNICALL Java_WindowsConfig_macAddressNative(JNIEnv *env, jclass c) {
  // build up a machine identifier in lieu of a MAC address
  byte tmp[16];
  HKEY hkey;
  md5_state_t ctx;

  md5_state_t main_ctx;
  md5_init(&main_ctx);
  md5_append(&main_ctx, (byte*) "cache-controlEthernet", 21);
  
  DWORD volume_serial_number;
  if (GetVolumeInformation("C:\\", NULL, 0, &volume_serial_number, NULL, NULL, NULL, 0)) {
    //print_buf("volume serial number", (byte*)&volume_serial_number, 4);
    md5_init(&ctx);
    md5_append(&ctx, (byte*)&volume_serial_number, 4); // endian issues?
    md5_finish(&ctx, tmp);
    //print_buf("v1", tmp, 4);
    md5_append(&main_ctx, tmp, 4);
  }
  
  if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, "HARDWARE\\DESCRIPTION\\System", 0, KEY_READ, &hkey) == ERROR_SUCCESS) {
    byte bios_version[1024];
    DWORD bios_version_size = 1024;
    if (RegQueryValueEx(hkey, "SystemBiosVersion", 0, NULL, (BYTE*)&bios_version, &bios_version_size) == ERROR_SUCCESS) {
      //print_buf("bios version", bios_version, bios_version_size);
      md5_init(&ctx);
      md5_append(&ctx, (byte*)bios_version, bios_version_size);
      md5_finish(&ctx, tmp);
      //print_buf("v2", tmp, 4);
      md5_append(&main_ctx, tmp, 4);
    }
    RegCloseKey(hkey);
  }

  if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", 0, KEY_READ, &hkey) == ERROR_SUCCESS) {
    byte processor_name[1024];
    DWORD processor_name_size = 1024;
    if (RegQueryValueEx(hkey, "ProcessorNameString", 0, NULL, (BYTE*)&processor_name, &processor_name_size) == ERROR_SUCCESS) {
      //print_buf("processor name", processor_name, processor_name_size);
      md5_init(&ctx);
      md5_append(&ctx, processor_name, processor_name_size);
      md5_finish(&ctx, tmp);
      //print_buf("v3", tmp, 4);
      md5_append(&main_ctx, tmp, 4);
    }
    RegCloseKey(hkey);
  }

  char product_id_filename[1024];
  char common_appdata[MAX_PATH];
  SHGetFolderPath(NULL, CSIDL_COMMON_APPDATA, NULL, 0, common_appdata);
  snprintf(product_id_filename, 1024, "%s\\Apple Computer\\iTunes\\SC Info\\SC Info.txt", common_appdata);
  struct stat stat_buf;
  if (!stat(product_id_filename, &stat_buf)) {
    int product_id_size;
    byte *product_id = read_file(product_id_filename, &product_id_size);
    //print_buf("product id", product_id, product_id_size);
    md5_init(&ctx);
    md5_append(&ctx, product_id, product_id_size);
    md5_finish(&ctx, tmp);
    //print_buf("v4", tmp, 4);
    md5_append(&main_ctx, tmp, 4);
    delete[] product_id;
  } else if (sizeof(void*) == 4) {
    // product ID file doesn't exist - ask registry
    // Note: we don't request this registry entry if we're running 64-bit.
    // This entry is not available for 32-bit processes running in a 64-bit OS
    // (who designed this crappy OS?) so the 64-bit process needs to emulate that
    // so 32 and 64 bit processes agree on the machine identifier.
    if (RegOpenKeyEx(HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", 0, KEY_READ, &hkey) == ERROR_SUCCESS) {
      byte product_id[1024];
      DWORD product_id_size = 1024;
      if (RegQueryValueEx(hkey, "ProductId", 0, NULL, (BYTE*)&product_id, &product_id_size) == ERROR_SUCCESS) {
        //print_buf("product id", product_id, product_id_size);
        md5_init(&ctx);
        md5_append(&ctx, product_id, product_id_size);
        md5_finish(&ctx, tmp);
        //print_buf("v5", tmp, 4);
        md5_append(&main_ctx, tmp, 4);
      }
      RegCloseKey(hkey);
    }
  }
  
  md5_finish(&main_ctx, tmp);
  
  jbyteArray macAddress = env->NewByteArray(6);
  env->SetByteArrayRegion(macAddress, 0, 6, (jbyte*)tmp);
  return macAddress;
}

JNIEXPORT jboolean JNICALL Java_WindowsConfig_isShiftDownNative(JNIEnv *env, jclass c) {
  return GetAsyncKeyState(VK_SHIFT) < 0;
}
