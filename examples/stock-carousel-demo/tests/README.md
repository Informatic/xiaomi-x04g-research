# Host verification scope

These checks are offline and never contact ADB, the clock, cameras or the network.
They do not establish compatibility with an untested launcher build. Run from the
example directory:

```sh
python3 -B -m unittest discover -s tests -v
```

The ten self-contained tests import the real builder, exercise DEX normalization,
module packaging and read-only preflight. An eleventh test validates the real
`build/x04g-carousel-demo-disabled.zip` when present; it is explicitly skipped if
the package has not been built. No test builds the module as a side effect.

The package acceptance checks inspect the actual module ZIP, not a synthetic ZIP:

- installation remains disabled until the documented explicit opt-in;
- every payload reference and declared digest resolves to the shipped file;
- ZIP paths are relative, unique and confined to the module;
- no extracted OEM APK/JAR/DEX, firmware image, account data or local credentials
  are distributed;
- build preflight validates its inputs without opening a listener, invoking ADB,
  writing to an existing output or building until an explicit build action;
- DEX normalisation preserves the bytecode and updates the DEX signature and
  checksum in the specified order;
- the ordering fixture exercises the exact `CarouselOrder` production helper
  with synthetic models rather than a rewritten algorithm.

The Java fixture has 74 assertions, including objects that compare equal but
have different identities, duplicate foreign entries, unchanged input and invalid
clock/demo identities. With a local JDK:

```sh
mkdir -p tests/host_classes
javac -d tests/host_classes java/org/x04g/carouseldemo/CarouselOrder.java tests/CarouselOrderFixture.java
java -cp tests/host_classes org.x04g.carouseldemo.CarouselOrderFixture
```

`UidPinFixture.cpp` exercises the production UID parser and metadata boundary with
synthetic inputs, without calling `readUidPin()` or opening privileged files.
It requires a POSIX host C++ compiler and development headers; Android-target NDK
compilation alone cannot execute this host test:

```sh
mkdir -p tests/host_bin
c++ -std=c++17 tests/UidPinFixture.cpp -o tests/host_bin/uid_pin_fixture
tests/host_bin/uid_pin_fixture
```

Generated `tests/host_classes/`, `tests/__pycache__/` and `tests/host_bin/` are local
build outputs and must not be committed.

The real OEM carousel, lifecycle callbacks, touches and Magisk/Zygisk behaviour
require a separate device test on the matching launcher build. A successful host
build or fixture run must not be presented as that device validation.
