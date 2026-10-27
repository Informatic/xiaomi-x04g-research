// Offline pure helper test: no readUidPin/openat invocation and no privileged files.
#include "../native/uid_pin.hpp"
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <initializer_list>

static unsigned checks = 0;
static void check(bool value) {
    ++checks;
    if (!value) { std::fprintf(stderr, "uid_check_%u\n", checks); std::exit(1); }
}

int main() {
    for (const char* text : {"10000", "19999", "12345\n"}) {
        uid_t value = 77;
        check(x04g::parseUidPin(text, std::strlen(text), value));
        check(value >= 10000 && value <= 19999);
    }
    for (const char* text : {"", "9999", "09999", "20000", "100000", "12345\r", "1234x", "12345\n\n", " 12345", "+12345"}) {
        uid_t value = 77;
        check(!x04g::parseUidPin(text, std::strlen(text), value));
        check(value == 0);
    }
    uid_t value = 77;
    check(!x04g::parseUidPin(nullptr, 5, value)); check(value == 0);
    struct stat good{};
    good.st_mode = S_IFREG | 0600; good.st_uid = 0; good.st_gid = 0; good.st_nlink = 1; good.st_size = 5;
    check(x04g::uidPinMetadata(good));
    good.st_size = 6; check(x04g::uidPinMetadata(good));
    for (int which = 0; which < 7; ++which) {
        auto bad = good;
        switch (which) {
            case 0: bad.st_uid = 10000; break;
            case 1: bad.st_gid = 10000; break;
            case 2: bad.st_nlink = 2; break;
            case 3: bad.st_mode = S_IFREG | 0620; break;
            case 4: bad.st_mode = S_IFREG | 0602; break;
            case 5: bad.st_mode = S_IFDIR | 0600; break;
            case 6: bad.st_size = 7; break;
        }
        check(!x04g::uidPinMetadata(bad));
    }
    std::printf("{\"pass\":true,\"checks\":%u,\"scope\":\"production UID parser and metadata; synthetic inputs; no files opened\"}\n", checks);
}
