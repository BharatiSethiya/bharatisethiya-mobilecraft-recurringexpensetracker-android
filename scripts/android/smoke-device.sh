#!/usr/bin/env bash
set -euo pipefail

package_name="${APP_PACKAGE:?}"
foreground="$(adb shell dumpsys window | tr -d '\r' | grep -m1 'mCurrentFocus=' || true)"
if [[ "${foreground}" != *"${package_name}"* ]]; then
  echo "Expected ${package_name} to own current window focus; observed: ${foreground:-no current focus}" >&2
  exit 1
fi

dump_ui() {
  adb shell uiautomator dump /sdcard/catima-smoke.xml >/dev/null
  adb shell cat /sdcard/catima-smoke.xml | tr -d '\r'
}

home_xml="$(dump_ui)"
bounds="$(printf '%s' "${home_xml}" | python3 -c '
import re, sys, xml.etree.ElementTree as ET
root = ET.fromstring(sys.stdin.read())
for node in root.iter("node"):
    if node.attrib.get("content-desc") == "Add loyalty card":
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            print(" ".join(match.groups()))
            break
')"
if [[ -z "${bounds}" ]]; then
  echo 'Catima is foregrounded, but accessibility node "Add loyalty card" with valid bounds was not found.' >&2
  exit 1
fi
read -r left top right bottom <<<"${bounds}"
adb shell input tap "$(((left + right) / 2))" "$(((top + bottom) / 2))"
sleep 1

destination_xml="$(dump_ui)"
if [[ "${destination_xml}" != *'text="Add Card"'* ]]; then
  # With TalkBack enabled, the first tap focuses the semantic node and a
  # double-tap activates it. Retry using that platform interaction without
  # disabling the user's accessibility service.
  adb shell input tap "$(((left + right) / 2))" "$(((top + bottom) / 2))"
  adb shell input tap "$(((left + right) / 2))" "$(((top + bottom) / 2))"
  sleep 1
  destination_xml="$(dump_ui)"
fi
if [[ "${destination_xml}" != *'text="Add Card"'* ]] ||
   [[ "${destination_xml}" != *'content-desc="Store name field"'* ]] ||
   [[ "${destination_xml}" != *'content-desc="Save card"'* ]]; then
  echo 'The Add loyalty card interaction did not open the manual Add Card form with its required accessibility semantics.' >&2
  exit 1
fi

echo 'Smoke passed: Catima was foregrounded, Add loyalty card was activated, and the manual Add Card form appeared.'
