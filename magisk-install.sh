#!/usr/bin/env bash

MAGISK_APK_URL="https://github.com/topjohnwu/Magisk/releases/download/v26.1/Magisk-v26.1.apk"
MAGISK_APK_SHA256="ae1a02b1ab608a51d5bc9b323e0588d06d30d9987ac8da01f4710d76f705dccb"
DL_DIR="/tmp/dl"
EXTRACT_DIR="/tmp/extract"

HOST_PLATFORM="$(uname -m)"
TARGET_PLATFORM="armeabi-v7a"

BOOT_PATH="$(realpath $1)"
ROOT_PATH="$(dirname -- "$(readlink -f -- "$0")")"

set -ex

if [[ ! -f "$EXTRACT_DIR/boot_patch.sh" ]]; then
    mkdir -p "$DL_DIR" "$EXTRACT_DIR"
    echo "- Downloading magisk apk..."
    wget $MAGISK_APK_URL -O "$DL_DIR/magisk.apk"
    echo "$MAGISK_APK_SHA256  $DL_DIR/magisk.apk" | sha256sum -c

    echo "- Extracting tools..."
    unzip -o $DL_DIR/magisk.apk assets/boot_patch.sh assets/util_functions.sh assets/stub.apk lib/${HOST_PLATFORM}/lib{magiskboot,busybox}.so lib/${TARGET_PLATFORM}/lib{magisk32,magiskpolicy,magiskinit}.so -d $EXTRACT_DIR

    mv $EXTRACT_DIR/assets/boot_patch.sh $EXTRACT_DIR/boot_patch.sh
    mv $EXTRACT_DIR/assets/util_functions.sh $EXTRACT_DIR/util_functions.sh
    mv $EXTRACT_DIR/assets/stub.apk $EXTRACT_DIR/stub.apk
    mv $EXTRACT_DIR/lib/${TARGET_PLATFORM}/libmagisk32.so $EXTRACT_DIR/magisk32
    mv $EXTRACT_DIR/lib/${TARGET_PLATFORM}/libmagiskinit.so $EXTRACT_DIR/magiskinit
    mv $EXTRACT_DIR/lib/${TARGET_PLATFORM}/libmagiskpolicy.so $EXTRACT_DIR/magiskpolicy
    mv $EXTRACT_DIR/lib/${HOST_PLATFORM}/libmagiskboot.so $EXTRACT_DIR/magiskboot
    mv $EXTRACT_DIR/lib/${HOST_PLATFORM}/libbusybox.so $EXTRACT_DIR/busybox

    rmdir $EXTRACT_DIR/lib/* $EXTRACT_DIR/lib $EXTRACT_DIR/assets
    chmod +x $EXTRACT_DIR/magisk* $EXTRACT_DIR/boot_patch.sh $EXTRACT_DIR/busybox

    echo "- Patching boot_patch.sh..."
    patch $EXTRACT_DIR/boot_patch.sh < $ROOT_PATH/boot-patch.patch
fi

echo "- Copying extra assets..."
cp $ROOT_PATH/init.adb.rc $ROOT_PATH/rootshell.sh $EXTRACT_DIR
echo "- Running boot_patch.sh..."
BOOTMODE=true $EXTRACT_DIR/busybox sh $EXTRACT_DIR/boot_patch.sh "$BOOT_PATH"

echo "- Done. Resulting boot image: $EXTRACT_DIR/new-boot.img"
