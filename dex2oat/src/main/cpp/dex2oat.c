/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2022 LSPosed Contributors
 */

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <stdbool.h>  // Include this to use bool in C
#include <dlfcn.h>    // Include this for dynamic linking functions

#include "logging.h"
#include "dobby.h"

#if defined(__LP64__)
#define LP_SELECT(lp32, lp64) lp64
#else
#define LP_SELECT(lp32, lp64) lp32
#endif

#define ID_VEC(is64, is_debug) (((is64) << 1) | (is_debug))

const char kSockName[] = "5291374ceda0aef7c5d86cd2a4f6a3ac\0";

// Function prototype for the dex2oat function we want to hook
typedef bool (*dex2oat_t)(int argc, char **argv);
dex2oat_t original_dex2oat;

static ssize_t xrecvmsg(int sockfd, struct msghdr *msg) {
    int flags = MSG_WAITALL;  // Use MSG_WAITALL directly
    int rec = recvmsg(sockfd, msg, flags);
    if (rec < 0) {
        PLOGE("recvmsg");
    }
    return rec;
}

static void *recv_fds(int sockfd) {
    const size_t bufsz = 16;
    const int cnt = 1;
    char cmsgbuf[CMSG_SPACE(sizeof(int) * cnt)];

    struct iovec iov = {
            .iov_base = (void *)&cnt,  // Cast away constness
            .iov_len = sizeof(cnt),
    };
    struct msghdr msg = {
            .msg_iov = &iov, .msg_iovlen = 1, .msg_control = cmsgbuf, .msg_controllen = bufsz
    };

    xrecvmsg(sockfd, &msg);  // Call xrecvmsg without flags
    struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);

    if (msg.msg_controllen != bufsz || cmsg == NULL ||
        cmsg->cmsg_len != CMSG_LEN(sizeof(int) * cnt) || cmsg->cmsg_level != SOL_SOCKET ||
        cmsg->cmsg_type != SCM_RIGHTS) {
        return NULL;
    }

    return CMSG_DATA(cmsg);
}

static int recv_fd(int sockfd) {
    void *data = recv_fds(sockfd);
    if (data == NULL) return -1;

    int result;
    memcpy(&result, data, sizeof(int));
    return result;
}

static int read_int(int fd) {
    int val;
    if (read(fd, &val, sizeof(val)) != sizeof(val)) return -1;
    return val;
}

static void write_int(int fd, int val) {
    if (fd < 0) return;
    write(fd, &val, sizeof(val));
}

// Hooked dex2oat function to modify parameters
bool hooked_dex2oat(int argc, char **argv) {
    (void)argc;  // 标记 argc 为未使用

    // 构建新的参数列表，过滤掉 --inline-max-code-units=0
    const char *new_argv[argc + 1];
    int new_argc = 0;
    for (int i = 0; i < argc; i++) {
        // 过滤掉特定的字符串
        if (strstr(argv[i], "--inline-max-code-units=0") == NULL) {
            new_argv[new_argc++] = argv[i];
        } else {
            // 过滤参数，并记录日志
            LOGD("Excluding --inline-max-code-units=0 from dex2oat arguments");
        }
    }
    new_argv[new_argc] = NULL;

    // 调用原始的 dex2oat 函数，传递修改后的参数列表
    return original_dex2oat(new_argc, (char **)new_argv);
}

int main(int argc, char **argv) {
    LOGD("dex2oat wrapper ppid=%d", getppid());
    struct sockaddr_un sock = {};
    sock.sun_family = AF_UNIX;
    strlcpy(sock.sun_path + 1, kSockName, sizeof(sock.sun_path) - 1);
    int sock_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    socklen_t len = (socklen_t)(sizeof(sa_family_t) + strlen(sock.sun_path + 1) + 1);
    if (connect(sock_fd, (struct sockaddr *)&sock, len)) {
        PLOGE("failed to connect to %s", sock.sun_path + 1);
        return 1;
    }
    write_int(sock_fd, ID_VEC(LP_SELECT(0, 1), strstr(argv[0], "dex2oatd") != NULL));
    int stock_fd = recv_fd(sock_fd);
    read_int(sock_fd);
    close(sock_fd);
    LOGD("sock: %s %d", sock.sun_path + 1, stock_fd);

    // Find the address of dex2oat in the shared library
    void* handle = dlopen("libart.so", RTLD_NOW);
    if (!handle) {
        LOGE("Failed to open libart.so: %s", dlerror());
        return 1;
    }

    void* dex2oat_addr = dlsym(handle, "dex2oat");
    if (!dex2oat_addr) {
        LOGE("Failed to find dex2oat: %s", dlerror());
        dlclose(handle);
        return 1;
    }

    LOGD("dex2oat address found at %p", dex2oat_addr);

    // Hook the dex2oat function to modify its parameters
    if (DobbyHook(dex2oat_addr, (void*)hooked_dex2oat, (void**)&original_dex2oat) == 0) { // 0 indicates success in Dobby
        LOGD("Hooked dex2oat successfully");
    } else {
        LOGE("Failed to hook dex2oat");
        dlclose(handle);
        return 1;
    }

    if (getenv("LD_LIBRARY_PATH") == NULL) {
#if defined(__LP64__)
        const char *libenv =
            "LD_LIBRARY_PATH=/apex/com.android.art/lib64:/apex/com.android.os.statsd/lib64:/data/adb/modules/zygisk_lsposed/lib64";
#else
        const char *libenv =
                "LD_LIBRARY_PATH=/apex/com.android.art/lib:/apex/com.android.os.statsd/lib:/data/adb/modules/zygisk_lsposed/lib";
#endif
        putenv((char *)libenv);
    }
    fexecve(stock_fd, argv, environ);
    PLOGE("fexecve failed");
    dlclose(handle);
    return 2;
}