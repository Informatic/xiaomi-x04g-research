# Stock launcher carousel demo

[Español](README.es.md)

This example adds **one counter page immediately after the original clock** in
the Xiaomi X04G stock carousel. The other stock pages retain their relative
order. It uses Android Views and a Zygisk module; it does not replace HOME,
re-sign the launcher, or distribute an OEM APK, extracted fonts or firmware.

The clock must already have working root, USB ADB and an API 4-compatible Zygisk
environment. This example does not unlock the bootloader, patch or flash boot,
configure root access, or open a network shell.

Use only one owner of the stock pager. The installer rejects the known older
`x04g_home_bootstrap_direct01` extension, including a disabled copy. This example
does not migrate that extension or its configuration.

## Compatibility

The reflection adapter deliberately supports one observed launcher build. It
should reject a different build rather than guess its private fields.

| Item | Expected value |
| --- | --- |
| Device | Xiaomi Mi Smart Clock X04G |
| Android | 10 / API 29 |
| Target process ABI | 32-bit ARM / `armeabi-v7a` |
| Reference display | 800×480 |
| OEM package | `com.google.assistant.launcher` |
| Shared process | `com.google.android.apps.mediashell` |
| Activity | `com.google.assistant.surfaces.clock.launcher.AssistantLauncherActivity` |
| OEM APK | `/product/app/com.google.assistant.launcher/app.apk` |
| APK size | 33126600 bytes |
| APK SHA256 | `1e5b52f356ef7c078619dc369cccf704060aca484754cef454fa921598ed8c21` |
| Launcher version | 111706 / `11.51.6.416171605` |
| UID | Discovered during installation; never copy a UID from another device |

The installer checks the device/API/ABI, exact OEM APK hash and version code,
then writes the discovered UID to `uid.pin`. The loader uses that pin to target
the matching process. The public Zygisk API 4 header is pinned to upstream commit
`7bb941ac8edfcffd1d23761e401c45ca95409dc1` and SHA256
`fc6523882c0f8659b4fcd1e04ffb07d3a948090e5a8c08817af6e2b506a078d0`.
These are compatibility identifiers, not a claim that another X04G firmware is
equivalent.

## Build locally

Use Python 3, a JDK 17 or newer with `javac`, Android SDK platform 29, recent
build-tools with **D8/R8 8.x or newer** and `core-lambda-stubs.jar`, and an Android
NDK containing the ARM32 compiler. API 29 is the target platform, not a reason to
use the old SDK 29 D8 compiler: that older compiler failed on this example.
`core-lambda-stubs.jar` is also needed for `javac` to compile Java 8 lambdas
against the API 29 boot classpath. No toolchain is bundled.

The host build used Linux/WSL, JDK 21, Android platform 29, D8/R8 8.3.37 and NDK r20b.
The native target is `armv7a-linux-androideabi29`, without a bundled C++ runtime.
If that NDK lacks `llvm-readelf`, the builder can use `readelf` from `PATH`.
Tool discovery for other host systems does not establish a successful build on
those systems.

From this directory, validate the tool paths without building. `--check` does
not execute a compiler; D8 version compatibility is checked during a build:

```sh
python3 build.py --sdk /path/to/Android/Sdk --ndk /path/to/ndk --jdk /path/to/jdk --check
```

Build the module with an explicit invocation:

```sh
python3 build.py --sdk /path/to/Android/Sdk --ndk /path/to/ndk --jdk /path/to/jdk
```

For a nonstandard SDK layout, `--android-jar` selects the API 29 platform JAR and
`--d8` selects a D8/R8 JAR. Normally `core-lambda-stubs.jar` is found beside the
SDK build-tools `lib/` directory containing D8. If you use a standalone D8/R8 JAR
outside that layout, pass the stubs explicitly, for example:

```sh
python3 build.py \
  --sdk /path/to/Android/Sdk \
  --ndk /path/to/ndk \
  --jdk /path/to/jdk \
  --android-jar /path/to/Android/Sdk/platforms/android-29/android.jar \
  --d8 /path/to/r8lib-8.3.37.jar \
  --core-lambda-stubs /path/to/Android/Sdk/build-tools/VERSION/core-lambda-stubs.jar
```

Replace `VERSION` with an installed build-tools version. `--out` selects the
output directory; the default is `build/`. The output is:

```text
build/x04g-carousel-demo-disabled.zip
```

`build/build-report.json` records the build inputs and output digest.

The ZIP contains the standalone DEX and ARM32 bridge. There is **no companion
APK to install**. It includes `disable` and `skip_mount`: installation does not
opt in to loading the module. Keep `skip_mount`; activation removes only
`disable`.

## Install, inspect, then activate

Run the following commands in a **POSIX shell**, such as Linux or WSL. They use
**USB ADB** (`-d`), and the existing root setup must allow `su`. The nested quotes
preserve each multiword `su -c` command on Android. These commands do not enable
network ADB or a network root shell.

```sh
adb -d shell su -c id
adb -d push build/x04g-carousel-demo-disabled.zip /data/local/tmp/x04g-carousel-demo-disabled.zip
adb -d shell 'su -c "magisk --install-module /data/local/tmp/x04g-carousel-demo-disabled.zip"'
adb -d reboot
```

This first reboot applies the module **disabled**. The [Magisk 26.1 installer](https://github.com/topjohnwu/Magisk/blob/v26.1/scripts/util_functions.sh#L680-L740)
stages a boot-mode installation in `/data/adb/modules_update/`, so `uid.pin` is not necessarily in
the live module directory immediately after installation. Once Android and USB
ADB return, inspect the installed module before activating it:

```sh
adb -d shell pm list packages -U com.google.assistant.launcher
adb -d shell 'su -c "cat /data/adb/modules/x04g_carousel_demo/uid.pin"'
adb -d shell 'su -c "ls -l /data/adb/modules/x04g_carousel_demo/disable /data/adb/modules/x04g_carousel_demo/skip_mount"'
```

The package UID and `uid.pin` must agree. If installation rejects the firmware
or the values disagree, leave the module disabled; changing a pin is not a
compatibility fix.

Opt in explicitly, then reboot a second time to activate the module:

```sh
adb -d shell 'su -c "rm /data/adb/modules/x04g_carousel_demo/disable"'
adb -d reboot
```

After normal startup, swipe from the original clock to the new page and press
its counter button. Check that the original pages and menus remain available.
The counter is memory-only. The demo does not start camera, audio, network or RF
work, and leaves the OEM automatic return to the clock in charge.

## Disable

With USB ADB available, mark the module disabled and reboot:

```sh
adb -d shell 'su -c "touch /data/adb/modules/x04g_carousel_demo/disable"'
adb -d reboot
```

This removes the runtime extension on the next process start. It does not
restore or flash firmware and is not a recovery procedure for an unavailable
ADB/root environment.

## How it fits into the stock launcher

The native bridge selects the pinned ARM32 process and loads the example DEX.
Java waits for the Android Application/main looper before checking the stock
APK and attaching on the main thread. `OemCarousel` locates the existing pager,
adapter and fragment registry through firmware-specific reflection. It adds
one owned fragment/model and orders that model after the clock, preserving the
identity and relative order of foreign models.

The example uses its own page ID and standard Android widgets. It does not
replace the stock adapter with a separate launcher. Private class/field names,
fragment ownership and lifecycle transitions are part of the compatibility
boundary, not a supported OEM extension API.

To reuse a UI from your own app, replace the body of
`DemoSession.createView(Activity)` in `java/org/x04g/carouseldemo/DemoSession.java`:

```java
FrameLayout page = new FrameLayout(activity);
page.addView(new MyDashboardView(activity),
        new FrameLayout.LayoutParams(-1, -1));
return page;
```

Provide `MyDashboardView` in this example's Java source tree. Also remove or
replace the counter-specific field and lines in `selected()` and `close()`;
otherwise those methods would still expect the old counter button. Keep the
visibility and closed-state handling for your replacement View. Installing an
unrelated APK alone does not add its Activity to the stock carousel. This code
runs in the OEM process/UID, so a separate app's permissions, storage and
Keystore identity do not automatically transfer. If you later add work beyond
the counter, start it only while the page is actually visible and release it
when hidden or closed. Use your own or appropriately licensed UI resources.

## Validation scope

The two-page implementation from which this example was reduced was observed
on physical X04G hardware with this launcher. **This reduced single-page module
has not itself been installed or accepted on a physical clock.** Its host build,
package checks and pure ordering fixtures are a separate result; see
[tests/README.md](tests/README.md). A successful build does not establish real
touch, Magisk/Zygisk loading or OEM lifecycle behaviour.

Activity recreation is not accepted by this contribution. The larger ancestor
exposed a possible stale-session/cleanup problem after an Activity transition;
the startup wait is not a demonstrated fix for that issue. Firmware updates,
automatic idle transitions and removal/recreation need their own device tests.
No long-term stability, complete recovery or preservation of every stock
function is claimed.

## License and third-party material

The original code and documentation **in this example directory** are provided
under [MIT](LICENSE). The Zygisk header retains its own notice; see
[NOTICE.md](NOTICE.md). This does not change the licensing of the rest of this
repository. No OEM assets or toolchain binaries are included.

For the exact completed checks and remaining device work, see [VALIDATION.md](VALIDATION.md).
