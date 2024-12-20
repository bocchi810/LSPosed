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

#if defined(__LP64__)
#define LP_SELECT(lp32, lp64) lp64
#else
#define LP_SELECT(lp32, lp64) lp32
#endif

#define ID_VEC(is64, is_debug) (((is64) << 1) | (is_debug))

const char kSockName[] = "5291374ceda0aef7c5d86cd2a4f6a3ac\0";

typedef bool (*dex2oat_t)(int argc, char **argv);
static dex2oat_t original_dex2oat = NULL;

static ssize_t xrecvmsg(int sockfd, struct msghdr *msg) {
    int flags = MSG_WAITALL;
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
            .iov_base = (void *)&cnt,
            .iov_len = sizeof(cnt),
    };
    struct msghdr msg = {
            .msg_iov = &iov, .msg_iovlen = 1, .msg_control = cmsgbuf, .msg_controllen = bufsz
    };

    xrecvmsg(sockfd, &msg);
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
    // 构建新的参数列表，过滤掉 --inline-max-code-units=0 和其他不想记录的参数
    char *new_argv[argc + 1];
    int new_argc = 0;

    for (int i = 0; i < argc; ++i) {
        // 这里可以添加更多需要排除的参数
        if (strstr(argv[i], "--inline-max-code-units=0") == NULL) {
            new_argv[new_argc++] = argv[i];
        } else {
            LOGD("Excluding parameter %s from dex2oat arguments", argv[i]);
        }
    }

    // 添加一个 NULL 终止符
    new_argv[new_argc] = NULL;

    // 调用原始的 dex2oat 函数，传递修改后的参数列表
    if (!original_dex2oat) {
        original_dex2oat = (dex2oat_t)dlsym(RTLD_NEXT, "dex2oat");
        if (!original_dex2oat) {
            LOGE("Failed to find original dex2oat: %s", dlerror());
            return false;
        }
    }

    return original_dex2oat(new_argc, new_argv);
}

// Main function for the executable
int main(int argc, char **argv) {
    LOGD("dex2oat wrapper ppid=%d", getppid());

    // 连接到套接字并进行通信的逻辑...
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

    // 修改环境变量的逻辑保持不变...
    if (getenv("LD_LIBRARY_PATH") == NULL) {
#if defined(__LP64__)
        const char *libenv =
            "LD_LIBRARY_PATH=/apex/com.android.art/lib64:/apex/com.android.os.statsd/lib64";
#else
        const char *libenv =
                "LD_LIBRARY_PATH=/apex/com.android.art/lib:/apex/com.android.os.statsd/lib";
#endif
        putenv((char *)libenv);
    }

    // Hook dex2oat
    void* handle = dlopen("libart.so", RTLD_NOW);
    if (!handle) {
        LOGE("Failed to open libart.so: %s", dlerror());
        return 1;
    }

    original_dex2oat = (dex2oat_t)dlsym(handle, "dex2oat");
    if (!original_dex2oat) {
        LOGE("Failed to find dex2oat: %s", dlerror());
        dlclose(handle);
        return 1;
    }

    LOGD("dex2oat address found at %p", original_dex2oat);

    // 使用 execve 执行原 dex2oat 程序，但使用 hook 函数处理参数
    // 注意：这里我们不直接替换函数指针，而是通过 hook 函数处理参数后调用原始函数
    return hooked_dex2oat(argc, argv) ? 0 : 1;
}