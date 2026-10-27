// SPDX-License-Identifier: MIT
#pragma once
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstddef>

namespace x04g {
inline bool parseUidPin(const char* data, size_t length, uid_t& value) {
    value = 0;
    if (!data || (length != 5 && length != 6) || (length == 6 && data[5] != '\n')) return false;
    unsigned parsed = 0;
    for (size_t i = 0; i < 5; ++i) {
        if (data[i] < '0' || data[i] > '9') return false;
        parsed = parsed * 10 + static_cast<unsigned>(data[i] - '0');
    }
    if (parsed < 10000 || parsed > 19999) return false;
    value = static_cast<uid_t>(parsed);
    return true;
}

inline bool uidPinMetadata(const struct stat& info) {
    return S_ISREG(info.st_mode) && info.st_uid == 0 && info.st_gid == 0 && info.st_nlink == 1
            && !(info.st_mode & 0022) && (info.st_size == 5 || info.st_size == 6);
}

inline bool readUidPin(int directory, uid_t& value) {
    value = 0;
    if (directory < 0) return false;
    int fd = openat(directory, "uid.pin", O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    struct stat before{}, after{};
    bool valid = fstat(fd, &before) == 0 && uidPinMetadata(before);
    char data[6]{};
    size_t done = 0;
    if (valid) {
        while (done < static_cast<size_t>(before.st_size)) {
            ssize_t count = read(fd, data + done, static_cast<size_t>(before.st_size) - done);
            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) break;
            done += static_cast<size_t>(count);
        }
        char extra = 0;
        ssize_t end;
        do { end = read(fd, &extra, 1); } while (end < 0 && errno == EINTR);
        valid = done == static_cast<size_t>(before.st_size) && end == 0
                && fstat(fd, &after) == 0 && uidPinMetadata(after)
                && before.st_dev == after.st_dev && before.st_ino == after.st_ino
                && before.st_size == after.st_size && before.st_mode == after.st_mode;
    }
    close(fd);
    return valid && parseUidPin(data, done, value);
}
}
