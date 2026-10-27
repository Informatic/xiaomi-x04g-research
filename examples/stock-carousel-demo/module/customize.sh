#!/system/bin/sh
# SPDX-License-Identifier: MIT
# Sourced by Magisk's installer in booted Android, never by this repository's builder.

[ "${BOOTMODE:-false}" = true ] || abort "Install from booted Android with Magisk."
[ "${MAGISK_VER_CODE:-0}" -ge 26000 ] || abort "Zygisk API4 requires Magisk 26.0 or later."
[ "${API:-0}" = 29 ] || abort "This example targets Android 10/API29 only."
[ "$(getprop ro.product.model)" = X04G ] || abort "Only Xiaomi X04G is supported."
case ",$(getprop ro.product.cpu.abilist32)," in
  *,armeabi-v7a,*) ;;
  *) abort "An ARM32 application ABI is required." ;;
esac

oem=/product/app/com.google.assistant.launcher/app.apk
[ "$(pm path --user 0 com.google.assistant.launcher)" = "package:$oem" ] || abort "Active launcher path differs from the supported stock APK."
digest=$(sha256sum "$oem") || abort "Cannot read stock launcher."
[ "${digest%% *}" = 1e5b52f356ef7c078619dc369cccf704060aca484754cef454fa921598ed8c21 ] || abort "Unsupported launcher APK hash."
details=$(dumpsys package com.google.assistant.launcher) || abort "Cannot inspect launcher package."
version=$(printf '%s\n' "$details" | sed -n 's/^[[:space:]]*versionCode=\([0-9]*\).*/\1/p')
[ "$version" = 111706 ] || abort "Unsupported launcher version."

# Do not hard-code a UID from another user's clock.
identity=$(pm list packages -U --user 0 com.google.assistant.launcher) || abort "Cannot read launcher UID."
case "$identity" in
  "package:com.google.assistant.launcher uid:"*) uid=${identity##*uid:} ;;
  *) abort "Unexpected launcher UID response." ;;
esac
case "$uid" in
  1[0-9][0-9][0-9][0-9]) ;;
  *) abort "Expected one regular Android application UID in 10000..19999." ;;
esac
[ "$identity" = "package:com.google.assistant.launcher uid:$uid" ] || abort "Launcher UID response must contain exactly one package."

# The working application and this demonstration both own the same OEM pager.
[ ! -d /data/adb/modules/x04g_home_bootstrap_direct01 ] || abort "Remove the other X04G carousel extension before installing this demo."

[ -n "${MODPATH:-}" ] && [ -d "$MODPATH" ] || abort "Magisk did not provide an installation directory."
printf '%s\n' "$uid" > "$MODPATH/uid.pin" || abort "Cannot create local UID pin."
set_perm "$MODPATH/uid.pin" 0 0 0600 || abort "Cannot protect the UID pin."
set_perm "$MODPATH/payload.dex" 0 0 0644 || abort "Cannot set DEX ownership/context."
set_perm "$MODPATH/zygisk/armeabi-v7a.so" 0 0 0755 || abort "Cannot set native library ownership/context."
touch "$MODPATH/disable" "$MODPATH/skip_mount" || abort "Cannot retain disabled state."
ui_print "Exact stock launcher and local UID verified."
ui_print "Installed disabled. Inspect uid.pin, then enable and reboot as described in README."
