#!/usr/bin/env bash
# folder-grid.yaml の前提を用意する。架空の名刺画像 3 枚を更新日時をずらして置く。
# 使い方: .maestro/setup-folder-grid.sh SERIAL
set -euo pipefail
serial=${1:?usage: setup-folder-grid.sh SERIAL}
here=$(cd "$(dirname "$0")" && pwd)
# 開発用 AVD の既定画面（320x640 mdpi）では 3 枚が 1 行に収まらないため、一般的なスマホの大きさにする。
# 戻すには: adb -s SERIAL shell wm size reset && adb -s SERIAL shell wm density reset
adb -s "$serial" shell wm size 1080x2340
adb -s "$serial" shell wm density 440
dir=/sdcard/Pictures/tansu-e2e
adb -s "$serial" shell rm -rf "$dir"
adb -s "$serial" shell mkdir -p "$dir"
adb -s "$serial" push "$here"/fixtures/folder-grid/*.jpg "$dir"/ >/dev/null
adb -s "$serial" shell touch -m -d 2026-01-01T10:00:00 "$dir/card-a.jpg"
adb -s "$serial" shell touch -m -d 2026-01-03T10:00:00 "$dir/card-b.jpg"
adb -s "$serial" shell touch -m -d 2026-01-05T10:00:00 "$dir/card-c.jpg"
