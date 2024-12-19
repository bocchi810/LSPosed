#include <jni.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>
#include <android/log.h>
#include <cstdio>  // 包含 cstdio 头文件以声明 snprintf
#include <sys/mman.h>  // 包含 sys/mman.h 头文件以声明 PROT_READ, PROT_WRITE, PROT_EXEC

#define LOG_TAG "XposedHook"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

typedef ssize_t (*real_read_t)(int fd, void *buf, size_t count);

static real_read_t original_read = nullptr;

ssize_t hooked_read(int fd, void *buf, size_t count) {
    // 获取调用者的包名或其他标识符
    char path[PATH_MAX];
    snprintf(path, sizeof(path), "/proc/self/fd/%d", fd);
    char link_target[PATH_MAX];
    ssize_t len = readlink(path, link_target, sizeof(link_target));
    if (len != -1) {
        link_target[len] = '\0';
        LOGD("File descriptor %d points to: %s", fd, link_target);

        // 检查路径是否包含 odex 文件
        if (strstr(link_target, ".odex") != nullptr) {
            // 获取调用者的包名
            pid_t pid = getpid();
            char maps_path[PATH_MAX];
            snprintf(maps_path, sizeof(maps_path), "/proc/%d/cmdline", pid);
            FILE* fp = fopen(maps_path, "r");
            if (fp != nullptr) {
                char cmdline[PATH_MAX];
                fread(cmdline, 1, sizeof(cmdline), fp);
                fclose(fp);
                cmdline[sizeof(cmdline) - 1] = '\0';

                // 检查调用者是否是虚拟机
                if (strcmp(cmdline, "com.android.art") != 0 && strncmp(cmdline, "com.genymotion", 12) != 0) {
                    LOGD("Hiding --inline-max-code-units parameter from non-VM process: %s", cmdline);

                    // 读取原始数据
                    ssize_t result = original_read(fd, buf, count);
                    if (result > 0) {
                        // 查找并隐藏 --inline-max-code-units 参数
                        char* start = static_cast<char*>(buf);
                        char* end = start + result;
                        char search_str[] = "--inline-max-code-units=";
                        size_t search_len = strlen(search_str);

                        for (char* pos = start; pos <= end - search_len; ++pos) {
                            if (strncmp(pos, search_str, search_len) == 0) {
                                // 找到参数，将其隐藏
                                char* value_start = pos + search_len;
                                char* newline_pos = strchr(value_start, '\n');
                                if (newline_pos) {
                                    size_t value_len = newline_pos - value_start;
                                    memmove(value_start, newline_pos + 1, result - (value_start - start) - value_len - 1);
                                    result -= value_len + 1;
                                    break;
                                }
                            }
                        }
                    }
                    return result;
                }
            }
        }
    }

    // 调用原始的 read 函数
    return original_read(fd, buf, count);
}

__attribute__((constructor))
void init() {
    void* handle = dlopen("libc.so", RTLD_LAZY);
    if (handle == nullptr) {
        LOGD("Failed to open libc.so");
        return;
    }

    original_read = reinterpret_cast<real_read_t>(dlsym(handle, "read"));
    if (original_read == nullptr) {
        LOGD("Failed to find symbol read");
        dlclose(handle);
        return;
    }

    // 替换 read 函数为我们的 hook 函数
    Dl_info info;
    if (dladdr(reinterpret_cast<void*>(original_read), &info)) {
        if (info.dli_saddr) {
            // 使用 mprotect 修改内存权限
            void* page_start = reinterpret_cast<void*>(
                    (reinterpret_cast<uintptr_t>(info.dli_saddr) / sysconf(_SC_PAGESIZE)) * sysconf(_SC_PAGESIZE)
            );
            if (mprotect(page_start, sysconf(_SC_PAGESIZE), PROT_READ | PROT_WRITE | PROT_EXEC) == 0) {
                *(reinterpret_cast<real_read_t*>(info.dli_saddr)) = hooked_read;
                mprotect(page_start, sysconf(_SC_PAGESIZE), PROT_READ | PROT_EXEC);
            } else {
                LOGD("Failed to change memory protection");
            }
        } else {
            LOGD("Failed to get address of original_read");
        }
    } else {
        LOGD("Failed to get DL info for original_read");
    }

    dlclose(handle);
}
