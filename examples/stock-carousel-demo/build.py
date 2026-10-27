#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Build a disabled X04G carousel demo locally. Never contacts or changes a device."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import struct
import subprocess
import tempfile
import zipfile
import zlib

BASE = Path(__file__).resolve().parent
HEADER_SHA256 = "fc6523882c0f8659b4fcd1e04ffb07d3a948090e5a8c08817af6e2b506a078d0"
ZIP_ENTRIES = frozenset({"module.prop", "customize.sh", "payload.dex",
                         "zygisk/armeabi-v7a.so", "disable", "skip_mount", "provenance.json", "LICENSE", "NOTICE.md"})


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalize_dex(data: bytes) -> bytes:
    """Canonicalize our freshly generated DEX, including one known legacy D8 bug."""
    if len(data) < 112 or data[:8] not in (b"dex\n035\0", b"dex\n037\0", b"dex\n038\0", b"dex\n039\0"):
        raise ValueError("unsupported DEX header")
    if struct.unpack_from("<III", data, 32) != (len(data), 112, 0x12345678):
        raise ValueError("invalid DEX size, header size or endianness")
    if struct.unpack_from("<I", data, 8)[0] != zlib.adler32(data[12:]) & 0xFFFFFFFF:
        raise ValueError("invalid DEX checksum")
    canonical = hashlib.sha1(data[32:]).digest()
    if data[12:32] not in (canonical, hashlib.sha1(data[32:-52]).digest()):
        raise ValueError("unrecognized DEX signature")
    result = bytearray(data)
    result[12:32] = canonical
    struct.pack_into("<I", result, 8, zlib.adler32(result[12:]) & 0xFFFFFFFF)
    return bytes(result)


def dex_classes(data: bytes) -> set[str]:
    """Read actual class definitions, rather than searching descriptor references."""
    normalize_dex(data)
    strings, strings_at, types, types_at = struct.unpack_from("<IIII", data, 56)
    count, classes_at = struct.unpack_from("<II", data, 96)
    if strings_at + strings * 4 > len(data) or types_at + types * 4 > len(data) or classes_at + count * 32 > len(data):
        raise ValueError("DEX table outside file")
    defined = set()
    for index in range(count):
        type_index = struct.unpack_from("<I", data, classes_at + 32 * index)[0]
        if type_index >= types:
            raise ValueError("invalid DEX type")
        string_index = struct.unpack_from("<I", data, types_at + 4 * type_index)[0]
        if string_index >= strings:
            raise ValueError("invalid DEX string")
        offset = struct.unpack_from("<I", data, strings_at + 4 * string_index)[0]
        for _ in range(5):
            if offset >= len(data):
                raise ValueError("invalid DEX string offset")
            byte = data[offset]
            offset += 1
            if byte < 128:
                break
        else:
            raise ValueError("invalid DEX string length")
        end = data.find(b"\0", offset)
        if end < 0:
            raise ValueError("unterminated DEX descriptor")
        defined.add(data[offset:end].decode("ascii"))
    return defined


def make_module_zip(path: Path, files: dict[str, bytes]) -> None:
    """An explicit allowlist keeps local UID pins, SDKs, dumps and keys out of ZIPs."""
    if set(files) != ZIP_ENTRIES:
        raise ValueError("module ZIP must contain exactly the documented entries")
    if files["disable"] != b"" or files["skip_mount"] != b"":
        raise ValueError("module must be initially disabled without system mounts")
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix="module-", suffix=".zip", dir=path.parent)
    os.close(fd)
    temporary_path = Path(temporary)
    try:
        with zipfile.ZipFile(temporary_path, "w", zipfile.ZIP_DEFLATED) as archive:
            for name in sorted(files):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = (stat.S_IFREG | (0o755 if name.endswith(".sh") else 0o644)) << 16
                archive.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(temporary_path) as archive:
            if archive.testzip() is not None:
                raise ValueError("ZIP integrity check failed")
        temporary_path.replace(path)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


def version_key(path: Path) -> tuple[int, ...]:
    return tuple(int(value) for value in re.findall(r"\d+", path.name))


def tools_for(args: argparse.Namespace) -> dict[str, Path]:
    sdk = Path(args.sdk).expanduser() if args.sdk else None
    android = Path(args.android_jar).expanduser() if args.android_jar else sdk / "platforms/android-29/android.jar" if sdk else None
    d8 = Path(args.d8).expanduser() if args.d8 else None
    if d8 is None and sdk:
        candidates = list((sdk / "build-tools").glob("*/lib/d8.jar")) + list((sdk / "build-tools").glob("*/lib/r8.jar"))
        if candidates:
            d8 = max(candidates, key=lambda item: version_key(item.parents[1]))
    lambda_stubs = Path(args.core_lambda_stubs).expanduser() if args.core_lambda_stubs else d8.parent.parent / "core-lambda-stubs.jar" if d8 else None
    ndk = Path(args.ndk).expanduser() if args.ndk else None
    if ndk is None and sdk and (sdk / "ndk").is_dir():
        candidates = [item for item in (sdk / "ndk").iterdir() if item.is_dir()]
        if candidates:
            ndk = max(candidates, key=version_key)
    host = {"Linux": "linux-x86_64", "Darwin": "darwin-x86_64", "Windows": "windows-x86_64"}.get(platform.system())
    if not host or not ndk:
        raise ValueError("provide --ndk (or ANDROID_NDK_HOME) for this host")
    toolchain = ndk / "toolchains/llvm/prebuilt" / host
    suffix = ".exe" if os.name == "nt" else ""
    if args.jdk:
        java, javac = [Path(args.jdk).expanduser() / "bin" / (name + suffix) for name in ("java", "javac")]
    else:
        java, javac = [Path(value) if (value := shutil.which(name)) else None for name in ("java", "javac")]
    readelf = toolchain / "bin" / ("llvm-readelf" + suffix)
    if not readelf.is_file() and shutil.which("readelf"):
        readelf = Path(shutil.which("readelf"))
    tools = {"android.jar": android, "d8.jar": d8, "core-lambda-stubs.jar": lambda_stubs, "java": java, "javac": javac,
             "clang++": toolchain / "bin" / ("clang++" + suffix),
             "readelf": readelf, "sysroot": toolchain / "sysroot"}
    for name, value in tools.items():
        if value is None or not (value.is_dir() if name == "sysroot" else value.is_file()):
            raise ValueError("missing " + name + "; see README toolchain setup")
    if sha256(BASE / "third_party/zygisk.hpp") != HEADER_SHA256:
        raise ValueError("public Zygisk header differs from the pinned API4 source")
    return tools


def run(command: list[str | Path], work: Path, name: str) -> str:
    result = subprocess.run([str(value) for value in command], stdin=subprocess.DEVNULL,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    (work / (name + ".stdout.txt")).write_bytes(result.stdout)
    (work / (name + ".stderr.txt")).write_bytes(result.stderr)
    if result.returncode:
        raise RuntimeError(name + " failed; inspect the local build log")
    return (result.stdout + result.stderr).decode("utf-8", "replace")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME"))
    parser.add_argument("--ndk", default=os.environ.get("ANDROID_NDK_HOME"))
    parser.add_argument("--jdk", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--android-jar", help="override the API29 android.jar path")
    parser.add_argument("--d8", help="override the D8/R8 JAR path")
    parser.add_argument("--core-lambda-stubs", help="override the SDK build-tools core-lambda-stubs.jar path")
    parser.add_argument("--out", type=Path, default=BASE / "build")
    parser.add_argument("--check", action="store_true", help="check local tools without compiling or writing output")
    args = parser.parse_args(argv)
    try:
        tools = tools_for(args)
        if args.check:
            print("Local tool paths and public header pin verified; no build performed.")
            return 0
        args.out.mkdir(parents=True, exist_ok=True)
        work = Path(tempfile.mkdtemp(prefix="work-", dir=args.out))
        classes, dex = work / "classes", work / "dex"
        classes.mkdir()
        dex.mkdir()
        d8_banner = run([tools["java"], "-XX:-UsePerfData", "-cp", tools["d8.jar"],
                         "com.android.tools.r8.D8", "--version"], work, "d8-version")
        d8_version = re.search(r"\bD8\s+(\d+(?:\.\d+){1,3})", d8_banner)
        if d8_version is None or int(d8_version.group(1).split(".")[0]) < 8:
            raise ValueError("use D8/R8 8.x or later from recent Android build-tools; --d8 accepts its JAR")
        javac_version = run([tools["javac"], "-version"], work, "javac-version").strip()
        sources = sorted((BASE / "java").rglob("*.java"))
        if not sources:
            raise ValueError("demo Java sources missing")
        run([tools["javac"], "-source", "8", "-target", "8", "-Xlint:-options", "-encoding", "UTF-8",
             "-bootclasspath", str(tools["core-lambda-stubs.jar"]) + os.pathsep + str(tools["android.jar"]),
             "-d", classes, *sources], work, "javac")
        run([tools["java"], "-XX:-UsePerfData", "-cp", tools["d8.jar"], "com.android.tools.r8.D8",
             "--min-api", "29", "--lib", tools["android.jar"], "--output", dex,
             *sorted(classes.rglob("*.class"))], work, "d8")
        payload = normalize_dex((dex / "classes.dex").read_bytes())
        required = {"Lorg/x04g/carouseldemo/Bootstrap;", "Lorg/x04g/carouseldemo/OemCarousel;", "Lorg/x04g/carouseldemo/DemoSession;"}
        if not required <= dex_classes(payload):
            raise ValueError("compiled payload is missing a demo entry class")
        (work / "payload.dex").write_bytes(payload)
        digest = hashlib.sha256(payload).digest()
        (work / "build_pins.hpp").write_text("#pragma once\nstatic constexpr size_t kDexBytes = %d;\nstatic constexpr uint8_t kDexSha256[32] = {%s};\n" %
            (len(payload), ",".join("0x%02x" % value for value in digest)), encoding="utf-8")
        native = work / "armeabi-v7a.so"
        run([tools["clang++"], "--target=armv7a-linux-androideabi29", "--sysroot=" + str(tools["sysroot"]),
             "-std=c++17", "-O2", "-Wall", "-Wextra", "-Werror", "-fPIC", "-shared", "-fno-exceptions",
             "-fno-rtti", "-fno-threadsafe-statics", "-fvisibility=hidden", "-ffunction-sections", "-fdata-sections",
             "-nostdlib++", "-Wl,--gc-sections", "-Wl,--no-undefined", "-Wl,--strip-all", "-Wl,-z,relro,-z,now",
             "-I", work, "-I", BASE / "native", BASE / "native/zygisk_entry.cpp", "-llog", "-o", native], work, "native")
        elf = run([tools["readelf"], "--wide", "--file-header", "--dynamic", "--dyn-syms", "--program-headers", native], work, "readelf")
        binary = native.read_bytes()
        needed = re.findall(r"\(NEEDED\)[^\n]*\[([^]]+)\]", elf)
        if binary[:6] != b"\x7fELF\x01\x01" or struct.unpack_from("<H", binary, 18)[0] != 40:
            raise ValueError("native output is not little-endian ELF32 ARM")
        if not set(needed) <= {"liblog.so", "libc.so", "libm.so", "libdl.so"}:
            raise ValueError("unexpected native dependency")
        if "TEXTREL" in elf or re.search(r"GNU_STACK[^\n]*\bRWE\b", elf) or not re.search(r"FUNC\s+GLOBAL\s+DEFAULT\s+\d+\s+zygisk_module_entry\s*$", elf, re.M):
            raise ValueError("native entry or ELF hardening check failed")
        provenance = {"scope": "host build only; not installed or accepted on a physical clock",
                      "builder_sha256": sha256(Path(__file__).resolve()), "D8_version": d8_version.group(1),
                      "javac_version": javac_version,
                      "zygisk_api": 4, "initially_disabled": True, "uid_pin_generated_on_device": True,
                      "payload_sha256": hashlib.sha256(payload).hexdigest(), "native_sha256": sha256(native),
                      "native_needed": needed, "tools_sha256": {name: sha256(value) for name, value in tools.items() if value.is_file()},
                      "source_sha256": {str(value.relative_to(BASE)): sha256(value) for value in sorted(BASE.rglob("*"))
                                        if value.is_file() and value.parts[len(BASE.parts)] in ("java", "native", "module", "third_party")},
                      "stock_launcher_sha256": "1e5b52f356ef7c078619dc369cccf704060aca484754cef454fa921598ed8c21"}
        files = {"module.prop": (BASE / "module/module.prop").read_text(encoding="utf-8").encode("utf-8"),
                 "customize.sh": (BASE / "module/customize.sh").read_text(encoding="utf-8").encode("utf-8"), "payload.dex": payload,
                 "zygisk/armeabi-v7a.so": binary, "disable": b"", "skip_mount": b"",
                 "LICENSE": (BASE / "LICENSE").read_bytes(), "NOTICE.md": (BASE / "NOTICE.md").read_bytes(),
                 "provenance.json": (json.dumps(provenance, indent=2, sort_keys=True) + "\n").encode()}
        package = args.out / "x04g-carousel-demo-disabled.zip"
        make_module_zip(package, files)
        (args.out / "build-report.json").write_text(json.dumps({**provenance, "zip_sha256": sha256(package)}, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"ZIP": str(package), "sha256": sha256(package), "initially_disabled": True}))
        return 0
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        parser.exit(1, "Build stopped: " + str(error) + "\n")


if __name__ == "__main__":
    raise SystemExit(main())
