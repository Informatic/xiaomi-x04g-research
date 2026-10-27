"""Offline unit tests for the actual builder; no Android, NDK invocation or network."""
from contextlib import ExitStack, redirect_stdout
from pathlib import Path
import hashlib
import importlib.util
import io
import json
import os
import socket
import stat
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import zlib

BASE = Path(__file__).resolve().parents[1]
BUILD = BASE / 'build.py'
REQUIRED = {'module.prop', 'customize.sh', 'payload.dex', 'zygisk/armeabi-v7a.so',
            'disable', 'skip_mount', 'provenance.json', 'LICENSE', 'NOTICE.md'}


def load_builder():
    spec = importlib.util.spec_from_file_location('carousel_demo_builder_under_test', BUILD)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    # Import is inspection, never a build or any other mutation. Files may be read.
    with ExitStack() as guards:
        for target in ('subprocess.run', 'subprocess.Popen', 'os.system', 'socket.socket',
                       'pathlib.Path.mkdir', 'pathlib.Path.write_bytes', 'pathlib.Path.write_text',
                       'pathlib.Path.touch', 'pathlib.Path.unlink', 'pathlib.Path.rename'):
            guards.enter_context(patch(target, side_effect=AssertionError('import_side_effect:' + target)))
        spec.loader.exec_module(module)
    return module


def dex_fixture(short_signature=False):
    """Header-valid synthetic input; not a runnable DEX or proprietary bytecode."""
    data = bytearray(256)
    data[:8] = b'dex\n035\0'
    struct.pack_into('<III', data, 32, len(data), 112, 0x12345678)
    data[112:] = bytes(range(144))
    signed = data[32:-52] if short_signature else data[32:]
    data[12:32] = hashlib.sha1(signed).digest()
    struct.pack_into('<I', data, 8, zlib.adler32(data[12:]) & 0xffffffff)
    return bytes(data)


class BuildTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.builder = load_builder()

    def assert_rejected(self, data):
        with self.assertRaises((ValueError, RuntimeError, AssertionError)):
            self.builder.normalize_dex(data)

    def test_import_is_read_only(self):
        self.assertTrue(callable(load_builder().normalize_dex))

    def test_canonical_dex_is_unchanged(self):
        before = dex_fixture()
        self.assertEqual(self.builder.normalize_dex(before), before)

    def test_known_d8_short_signature_is_normalized(self):
        before = dex_fixture(short_signature=True)
        after = self.builder.normalize_dex(before)
        self.assertEqual(after[:8], before[:8])
        self.assertEqual(after[32:], before[32:], 'normalization must not alter bytecode/header fields')
        self.assertEqual(after[12:32], hashlib.sha1(after[32:]).digest())
        self.assertEqual(struct.unpack_from('<I', after, 8)[0], zlib.adler32(after[12:]) & 0xffffffff)
        self.assertNotEqual(after[8:32], before[8:32])
        self.assertEqual(self.builder.normalize_dex(after), after, 'normalization must be idempotent')

    def test_dex_invalid_header_and_checksum_are_rejected(self):
        original = dex_fixture()
        for offset, value in ((0, b'BAD!'), (32, struct.pack('<I', len(original)-1)),
                              (36, struct.pack('<I', 111)), (40, struct.pack('<I', 0x78563412))):
            broken = bytearray(original)
            broken[offset:offset+len(value)] = value
            with self.subTest(offset=offset):
                self.assert_rejected(bytes(broken))
        self.assert_rejected(original[:100])
        broken = bytearray(original); broken[-1] ^= 1
        self.assert_rejected(bytes(broken))

    def test_unknown_signature_is_rejected_even_with_valid_adler(self):
        broken = bytearray(dex_fixture())
        broken[12:32] = b'\x42' * 20
        struct.pack_into('<I', broken, 8, zlib.adler32(broken[12:]) & 0xffffffff)
        self.assert_rejected(bytes(broken))

    def test_zip_exact_allowlist_disabled_and_confined(self):
        files = {name: ('synthetic fixture:' + name).encode() for name in REQUIRED}
        files['disable'] = b''; files['skip_mount'] = b''
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)/'module.zip'
            self.builder.make_module_zip(output, files)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(set(archive.namelist()), REQUIRED)
                self.assertEqual(len(archive.namelist()), len(REQUIRED))
                for entry in archive.infolist():
                    self.assertEqual(archive.read(entry), files[entry.filename])
                    self.assertFalse(entry.flag_bits & 1, 'encrypted ZIP entries are not auditable')
                    self.assertFalse(entry.filename.startswith(('/', '\\')))
                    self.assertNotIn('..', Path(entry.filename).parts)
                    self.assertNotIn('\\', entry.filename)
                    self.assertTrue(stat.S_ISREG(entry.external_attr >> 16))
                self.assertEqual(archive.read('disable'), b'')
                self.assertEqual(archive.read('skip_mount'), b'')
                self.assertTrue((archive.getinfo('customize.sh').external_attr >> 16) & 0o111)

    def test_zip_rejects_missing_or_extra_payload_before_writing(self):
        files = {name: b'synthetic' for name in REQUIRED}
        variants = [dict(files, **{'../outside': b'bad'}), dict(files, **{'/outside': b'bad'}),
                    dict(files, **{'uid.pin': b'12345'}), dict(files, **{'original.apk': b'proprietary'}),
                    dict(files, **{'boot.img': b'firmware'})]
        missing = dict(files); missing.pop('disable'); variants.append(missing)
        with tempfile.TemporaryDirectory() as temp:
            for i, payload in enumerate(variants):
                output = Path(temp)/('rejected-%d.zip' % i)
                with self.subTest(variant=i):
                    with self.assertRaises((ValueError, RuntimeError, AssertionError)):
                        self.builder.make_module_zip(output, payload)
                    self.assertFalse(output.exists(), 'rejected content must not leave a partial distributable')

    def test_help_does_not_build(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)/'must-not-exist'
            result = subprocess.run([sys.executable, str(BUILD), '--help', '--out', str(output)],
                                    capture_output=True, timeout=10, stdin=subprocess.DEVNULL)
            self.assertEqual(result.returncode, 0)
            self.assertFalse(output.exists())

    def test_invalid_inputs_do_not_create_output(self):
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp); output = base/'must-not-exist'
            result = subprocess.run([sys.executable, str(BUILD), '--sdk', str(base/'missing-sdk'),
                                    '--ndk', str(base/'missing-ndk'), '--jdk', str(base/'missing-jdk'),
                                    '--out', str(output)], capture_output=True, timeout=10, stdin=subprocess.DEVNULL)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(output.exists(), 'toolchain preflight must precede output creation')

    def test_check_mode_is_read_only_and_never_executes_tools(self):
        # Synthetic existing paths exercise preflight only, not compiler validity.
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp); ndk = base/'ndk'; jdk = base/'jdk'
            host = {'Linux':'linux-x86_64', 'Darwin':'darwin-x86_64', 'Windows':'windows-x86_64'}[self.builder.platform.system()]
            toolchain = ndk/'toolchains/llvm/prebuilt'/host
            suffix = '.exe' if os.name == 'nt' else ''
            files = [base/'android.jar', base/'d8.jar', jdk/'bin'/('java'+suffix),
                     jdk/'bin'/('javac'+suffix), toolchain/'bin'/('clang++'+suffix),
                     toolchain/'bin'/('llvm-readelf'+suffix), base/'core-lambda-stubs.jar']
            for item in files:
                item.parent.mkdir(parents=True, exist_ok=True); item.write_bytes(b'synthetic preflight path')
            (toolchain/'sysroot').mkdir()
            output = base/'must-not-exist'
            with ExitStack() as guards:
                for target in ('subprocess.run', 'subprocess.Popen', 'socket.socket', 'pathlib.Path.mkdir',
                               'pathlib.Path.write_bytes', 'pathlib.Path.write_text', 'pathlib.Path.touch'):
                    guards.enter_context(patch(target, side_effect=AssertionError('preflight_side_effect:'+target)))
                guards.enter_context(redirect_stdout(io.StringIO()))
                result = self.builder.main(['--ndk',str(ndk),'--jdk',str(jdk),'--android-jar',str(files[0]),
                                            '--d8',str(files[1]),'--core-lambda-stubs',str(files[-1]),
                                            '--out',str(output),'--check'])
            self.assertEqual(result, 0)
            self.assertFalse(output.exists())

    def test_real_built_package_when_present(self):
        package = BASE/'build/x04g-carousel-demo-disabled.zip'
        if not package.exists():
            self.skipTest('host build output not present; this test does not build or contact a device')
        with zipfile.ZipFile(package) as archive:
            self.assertEqual(set(archive.namelist()), REQUIRED)
            self.assertEqual(len(archive.namelist()), len(REQUIRED))
            self.assertIsNone(archive.testzip())
            self.assertEqual(archive.read('disable'), b''); self.assertEqual(archive.read('skip_mount'), b'')
            provenance = json.loads(archive.read('provenance.json'))
            payload = archive.read('payload.dex'); native = archive.read('zygisk/armeabi-v7a.so')
            self.assertEqual(hashlib.sha256(payload).hexdigest(), provenance['payload_sha256'])
            self.assertEqual(hashlib.sha256(native).hexdigest(), provenance['native_sha256'])
            self.assertEqual(payload, self.builder.normalize_dex(payload))
            defined = self.builder.dex_classes(payload)
            self.assertTrue(defined)
            self.assertTrue(all(name.startswith('Lorg/x04g/carouseldemo/') for name in defined),
                            'the distributable must not define extracted OEM or other unrelated classes')
            self.assertEqual(native[:6], b'\x7fELF\x01\x01')
            self.assertEqual(struct.unpack_from('<H', native, 18)[0], 40)
            self.assertTrue(provenance['initially_disabled'])
            self.assertTrue(provenance['uid_pin_generated_on_device'])


if __name__ == '__main__':
    unittest.main()
