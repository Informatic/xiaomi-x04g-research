# Validation of the reduced example

Recorded on 2026-10-27. This is a host build and source contribution, not a
claim of successful installation on a physical clock.

## Completed

- The actual Java sources compiled against Android API29 and were converted
  to a canonical DEX with D8/R8 8.3.37.
- The native loader compiled as little-endian ELF32 ARM for API29. Its exported
  Zygisk entry, dynamic dependencies and non-executable-stack checks passed.
- The ZIP passed CRC and exact-entry checks. It contains `disable`,
  `skip_mount`, the payload/loader and their provenance, installer, metadata,
  license and notices. It contains no device-specific `uid.pin`.
- All 11 Python unit tests passed, including inspection of that actual ZIP,
  checksum rejection, known legacy D8 normalization and read-only preflight.
- The production `CarouselOrder` helper passed 74 Java checks covering identity,
  original order, unchanged inputs, duplicate foreign models and invalid inputs.
- No extracted OEM resources, signing keys or camera/RF configuration are
  required by the build or included in the contribution.

The complete module build ran on Linux/WSL with JDK21, Android API29,
D8/R8 8.3.37 and NDKr20b. The current public Zygisk template recommends NDKr21
or newer; the older toolchain used here establishes compilation only. The Java
ordering fixture and Python tests also ran on the Windows host. This does not
establish a complete native-module build on Windows or macOS.

`build/build-report.json`, generated locally and excluded from Git, records the
tool/source digests, D8 and javac versions and resulting ZIP digest. Build
outputs are intentionally not committed. See [tests/README.md](tests/README.md)
for reproducible test commands.

## Not established by these checks

The reduced single-page module was not installed on a physical clock. The larger
two-page implementation from which its adapter was derived had separate physical
observations; those results are not transferred to this module.

Actual Magisk installation, UID file ownership/context, firmware verification,
injection, gestures, activity recreation, stock menus/alarms, long-running memory
behaviour and removal still need testing on the matching firmware. The installer
was inspected but not executed against a device during this contribution.

The optional C++ UID-parser fixture is supplied for a POSIX development host;
it was not executed in this environment because its host development headers
were unavailable. The real Android ARM loader was compiled successfully.
