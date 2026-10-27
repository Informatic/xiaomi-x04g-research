# Notices and scope

The [MIT license](LICENSE) applies to the original example code, documentation
and tests in `examples/stock-carousel-demo/`. Third-party material retains its
own notice below. This license does not relicense the parent repository or its
existing research, scripts or images.

## Zygisk public API header

`third_party/zygisk.hpp` is the public API 4 header from
[topjohnwu/zygisk-module-sample, commit 7bb941ac8edfcffd1d23761e401c45ca95409dc1](https://github.com/topjohnwu/zygisk-module-sample/blob/7bb941ac8edfcffd1d23761e401c45ca95409dc1/module/jni/zygisk.hpp).
The pinned SHA256 is
`fc6523882c0f8659b4fcd1e04ffb07d3a948090e5a8c08817af6e2b506a078d0`.
Its original permission notice is preserved in the header and reproduced here:

```text
Copyright 2022-2023 John "topjohnwu" Wu

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
PERFORMANCE OF THIS SOFTWARE.
```

This is a 0BSD-style permissive notice as supplied upstream, without an SPDX tag.
The example's MIT notice does not replace it. The public header's permission
should not be assumed to license other Magisk files. No copied Magisk module
installer or internal Magisk implementation is included in this example.

## Platform and omitted material

Android SDK/NDK and JDK files are external build inputs under their respective
licenses, not bundled dependencies. Runtime Android classes come from the
device. The example uses standard Android Views and system typography.

No OEM APK/DEX/JAR dump, firmware image, extracted GoogleSans/MaterialIcons font,
OEM-derived SVG, camera or RF configuration, private signing key, or account
material is included. The larger application's QR dependencies are also omitted;
this counter demo does not need Nayuki or ZXing.
