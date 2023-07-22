{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  packages = with pkgs; [
    (python3.withPackages (ps: with ps; [
      pyserial
      pyusb
      colorama
      pycryptodomex
      pyusb

      # android_universal
      pycryptodome
      lz4
      pyasn1
      pyasn1-modules
    ]))
    android-tools
    binwalk
    openssl

    busybox
    dtc
  ];
}
