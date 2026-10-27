// SPDX-License-Identifier: MIT
#include <cstdint>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <android/log.h>
#include "../third_party/zygisk.hpp"
#include "build_pins.hpp"
#include "uid_pin.hpp"

// No PLT/JNI hooks, ptrace, companion process, root shell, property or SELinux changes.
class CarouselDemoModule final : public zygisk::ModuleBase {
    zygisk::Api* api = nullptr;
    JNIEnv* env = nullptr;
    uint8_t* bytes = nullptr;
    size_t length = 0;
    bool target = false;
    uid_t expected_uid = 0;

    void releasePayload() { free(bytes); bytes = nullptr; length = 0; }

    static void report(const char* stage, const char* result) {
        __android_log_print(ANDROID_LOG_INFO, "X04GCarouselDemo", "stage=%s result=%s", stage, result);
    }
    bool ok(const void* value, const char* stage) {
        if (env->ExceptionCheck() || !value) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            report(stage, "failed"); return false;
        }
        return true;
    }
    bool hash_matches(jbyteArray payload) {
        jclass type = env->FindClass("java/security/MessageDigest");
        if (!ok(type, "digest_class")) return false;
        jmethodID get = env->GetStaticMethodID(type, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
        if (!ok(get, "digest_get")) return false;
        jstring name = env->NewStringUTF("SHA-256");
        if (!ok(name, "digest_name")) return false;
        jobject digest = env->CallStaticObjectMethod(type, get, name);
        if (!ok(digest, "digest_instance")) return false;
        jmethodID finish = env->GetMethodID(type, "digest", "([B)[B");
        if (!ok(finish, "digest_method")) return false;
        auto result = static_cast<jbyteArray>(env->CallObjectMethod(digest, finish, payload));
        if (!ok(result, "digest_result") || env->GetArrayLength(result) != 32) return false;
        jbyte actual[32]; env->GetByteArrayRegion(result, 0, 32, actual);
        if (env->ExceptionCheck()) { env->ExceptionClear(); return false; }
        uint8_t difference = 0;
        for (size_t i = 0; i < 32; ++i) difference |= static_cast<uint8_t>(actual[i]) ^ kDexSha256[i];
        return difference == 0;
    }
    void load() {
        jbyteArray payload = env->NewByteArray(static_cast<jsize>(length));
        if (!ok(payload, "dex_array")) return;
        env->SetByteArrayRegion(payload, 0, static_cast<jsize>(length), reinterpret_cast<const jbyte*>(bytes));
        if (env->ExceptionCheck()) { env->ExceptionClear(); report("dex_copy", "failed"); return; }
        if (!hash_matches(payload)) { report("dex_hash", "rejected"); return; }
        jclass bufferClass = env->FindClass("java/nio/ByteBuffer");
        if (!ok(bufferClass, "buffer_class")) return;
        jmethodID wrap = env->GetStaticMethodID(bufferClass, "wrap", "([B)Ljava/nio/ByteBuffer;");
        if (!ok(wrap, "buffer_wrap")) return;
        jobject buffer = env->CallStaticObjectMethod(bufferClass, wrap, payload);
        if (!ok(buffer, "buffer")) return;
        jclass base = env->FindClass("java/lang/ClassLoader");
        if (!ok(base, "loader_class")) return;
        jmethodID getSystem = env->GetStaticMethodID(base, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
        if (!ok(getSystem, "parent_method")) return;
        jobject parent = env->CallStaticObjectMethod(base, getSystem);
        if (!ok(parent, "parent")) return;
        jclass memory = env->FindClass("dalvik/system/InMemoryDexClassLoader");
        if (!ok(memory, "memory_loader_class")) return;
        jmethodID construct = env->GetMethodID(memory, "<init>", "(Ljava/nio/ByteBuffer;Ljava/lang/ClassLoader;)V");
        if (!ok(construct, "memory_loader_constructor")) return;
        jobject loader = env->NewObject(memory, construct, buffer, parent);
        if (!ok(loader, "memory_loader")) return;
        jmethodID loadClass = env->GetMethodID(base, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
        if (!ok(loadClass, "load_class_method")) return;
        jstring entryName = env->NewStringUTF("org.x04g.carouseldemo.Bootstrap");
        if (!ok(entryName, "entry_name")) return;
        auto entry = static_cast<jclass>(env->CallObjectMethod(loader, loadClass, entryName));
        if (!ok(entry, "entry_class")) return;
        jmethodID start = env->GetStaticMethodID(entry, "start", "(I)V");
        if (!ok(start, "entry_method")) return;
        env->CallStaticVoidMethod(entry, start, static_cast<jint>(expected_uid));
        if (env->ExceptionCheck()) { env->ExceptionClear(); report("entry", "failed"); }
        else report("entry", "scheduled"); // Not an assertion that Application or carousel exists.
    }
public:
    void onLoad(zygisk::Api* handle, JNIEnv* environment) override { api = handle; env = environment; }
    void preAppSpecialize(zygisk::AppSpecializeArgs* args) override {
        api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        // No installed files/UIDs are inspected outside this exact process selection.
        if (sizeof(void*) != 4 || !args->nice_name) {
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY); return;
        }
        const char* name = env->GetStringUTFChars(args->nice_name, nullptr);
        if (!ok(name, "process_name")) { api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY); return; }
        target = std::strcmp(name, "com.google.android.apps.mediashell") == 0;
        env->ReleaseStringUTFChars(args->nice_name, name);
        if (!target || (api->getFlags() & zygisk::PROCESS_ON_DENYLIST)) {
            target = false; api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY); return;
        }
        int directory = api->getModuleDir();
        if (directory < 0) { report("module_dir", "failed"); return; }
        if (!x04g::readUidPin(directory, expected_uid) || args->uid != static_cast<jint>(expected_uid)) {
            close(directory); target = false; report("uid_pin", "rejected"); return;
        }
        int fd = openat(directory, "payload.dex", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        close(directory);
        if (fd < 0) { report("dex_open", "failed"); return; }
        struct stat statbuf{};
        bool valid = fstat(fd, &statbuf) == 0 && S_ISREG(statbuf.st_mode) && statbuf.st_uid == 0
                && statbuf.st_nlink == 1 && !(statbuf.st_mode & 0022)
                && statbuf.st_size == static_cast<off_t>(kDexBytes) && kDexBytes > 0 && kDexBytes <= 4 * 1024 * 1024;
        if (valid) bytes = static_cast<uint8_t*>(malloc(kDexBytes));
        if (!valid || !bytes) { close(fd); report("dex_metadata", "rejected"); return; }
        while (length < kDexBytes) {
            ssize_t count = read(fd, bytes + length, kDexBytes - length);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) break;
            length += static_cast<size_t>(count);
        }
        close(fd);
        if (length != kDexBytes) { releasePayload(); length = 0; report("dex_read", "failed"); }
        api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY); // No native hooks or registered callbacks survive.
    }
    void postAppSpecialize(const zygisk::AppSpecializeArgs*) override {
        if (!target || !bytes || length != kDexBytes || expected_uid < 10000 || expected_uid > 19999 || getuid() != expected_uid) return;
        if (env->PushLocalFrame(64) != JNI_OK) {
            if (env->ExceptionCheck()) env->ExceptionClear(); releasePayload(); return;
        }
        load();
        env->PopLocalFrame(nullptr);
        // Heap ByteBuffer is copied/owned by the Java/ART loader, not backed by this freed native array.
        releasePayload(); length = 0;
    }
    void preServerSpecialize(zygisk::ServerSpecializeArgs*) override {
        api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }
};
REGISTER_ZYGISK_MODULE(CarouselDemoModule)
