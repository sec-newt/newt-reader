#!/usr/bin/env bash
# Emulator test: turns Reader on, opens a local two-page web page, and checks Reader
# reads it, follows an inline link without closing, goes Back, and closes before "Send".
set -euo pipefail

pkg="io.github.secnewt.reader.debug"
adb install -r app-debug.apk
adb shell settings put secure enabled_accessibility_services "$pkg/io.github.secnewt.reader.service.ReaderService"
adb shell settings put secure accessibility_enabled 1
sleep 5

adb shell am start -n "$pkg/io.github.secnewt.reader.TestPageActivity"
sleep 10

# Sends one broadcast, then waits up to 30 s for a matching NewtReaderTest log line.
step() {
  local expect="$1"; shift
  adb logcat -c
  adb shell am broadcast -p "$pkg" "$@" > /dev/null
  for _ in $(seq 1 30); do
    if adb logcat -d -s NewtReaderTest | grep -q -- "$expect"; then echo "OK: $expect"; return 0; fi
    sleep 1
  done
  echo "::error::Never saw '$expect'. Reader's log:"
  adb logcat -d -s NewtReaderTest | tail -20
  echo "Accessibility tree of the test page:"
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null && adb shell cat /sdcard/ui.xml | sed 's/></>\n</g' | grep -E 'text="[^"]+"|content-desc="[^"]+"' | head -60 || true
  exit 1
}

# adb shell re-splits its arguments, so a label with a space needs inner quotes.
step "shows: Test page one$" -a io.github.secnewt.reader.debug.OPEN
step "shows: Test page two$" -a io.github.secnewt.reader.debug.TAP --es label "'Next page'"
step "shows: Test page one$" -a io.github.secnewt.reader.debug.BACK
step "closed"                -a io.github.secnewt.reader.debug.TAP --es label "Send"
echo "Reader read, followed, went back, and closed before Send."
