#!/usr/bin/env bash
# Captures Play Store screenshots from a connected device/emulator (adb required).
# Usage: tools/capture_screenshots.sh ar|fr|en  <name>   e.g. tools/capture_screenshots.sh ar main
set -euo pipefail
lang="${1:?language: ar|fr|en}"; name="${2:?shot name}"
mkdir -p store/screenshots
adb exec-out screencap -p > "store/screenshots/${lang}_${name}.png"
echo "saved store/screenshots/${lang}_${name}.png"
