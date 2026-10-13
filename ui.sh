#!/usr/bin/env bash
# Drive the running app on the emulator: tap / type / swipe / screenshot, by text OR pixel.
#
#   ./ui.sh here                       # everything on screen: center | text | content-desc
#   ./ui.sh tap "Open sidebar"         # by text or content-desc
#   ./ui.sh tap 74 220                 # or raw pixels (the screenshot is 1080x2400 here)
#   ./ui.sh tap-text "Save"            # match `text` only (default also matches content-desc)
#   ./ui.sh type "hello world"         # into whatever has focus
#   ./ui.sh field 0 "Ada Lovelace"     # clear the top-most text field, then type
#   ./ui.sh key BACK | key ENTER | key 4
#   ./ui.sh swipe 540 1800 540 600 300 # x1 y1 x2 y2 [duration ms]
#   ./ui.sh ime off                    # hide the keyboard (`ime on` needs coordinates)
#   ./ui.sh drag-shot shots/x.png 380 900 720 900   # screenshot DURING a drag
#   ./ui.sh shot shots/x.png
#   ./ui.sh wait 2
#
# Compose exposes both `text` and `content-desc` to the accessibility tree, so targets can be
# named instead of guessed from pixels. Everything below works on that tree via uiautomator.
set -eu

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
sdk=$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" 2>/dev/null | head -1)
for cand in "$sdk" "${ANDROID_HOME:-}" "$HOME/.android-sdk" "$HOME/Android/Sdk"; do
  if [ -n "$cand" ] && [ -x "$cand/platform-tools/adb" ]; then ADB=$cand/platform-tools/adb; break; fi
done
: "${ADB:?no adb found — set ANDROID_HOME or create local.properties}"
XML=/tmp/template-ui.xml

dump() { # refresh the view hierarchy locally
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  $ADB shell cat /sdcard/ui.xml 2>/dev/null | tr '<' '\n' > "$XML"
}
nodes() { grep '^node ' "$XML"; }
center() { # bounds="[x1,y1][x2,y2]" -> "cx cy"
  echo "$1" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' |
    awk '{printf "%d %d\n", ($1+$3)/2, ($2+$4)/2}'
}
fields() { # every text field, top-most first: "cx cy" per line
  nodes | grep 'class="android.widget.EditText"' | while read -r n; do center "$n"; done |
    sort -k2 -n
}
type_text() { # `input text` needs spaces as %s and eats shell metacharacters
  esc=$(printf '%s' "$1" | sed 's/ /%s/g; s/\([()<>|&;*\\"'"'"']\)/\\\1/g')
  $ADB shell input text "$esc"
}
clear_focused_field() { # MOVE_END + a generous number of DELETEs.
  # Do not use `input keycombination 113 29` (Ctrl+A): it silently does nothing on some
  # images, and a half-cleared field then appends the new value to the old one.
  local deletes
  deletes=$(printf '67 %.0s' $(seq 1 96))
  $ADB shell input keyevent 123 >/dev/null 2>&1 || true
  $ADB shell input keyevent $deletes >/dev/null 2>&1 || true
}

cmd=${1:-here}; shift || true
case $cmd in
  here|dump)
    dump
    nodes | while read -r n; do
      t=$(sed -n 's/.*text="\([^"]*\)".*/\1/p' <<<"$n")
      d=$(sed -n 's/.*content-desc="\([^"]*\)".*/\1/p' <<<"$n")
      [ -n "$t$d" ] && printf '%s  %s\n' "$(center "$n")" "${t:-[$d]}"
    done
    ;;
  find|tap|tap-text)
    needle=${1:?usage: ui.sh $cmd <text>}; [ $# -gt 1 ] && shift
    if [[ $needle =~ ^[0-9]+$ ]] && [ $# -ge 1 ]; then   # raw coordinates
      $ADB shell input tap "$needle" "$1"; echo "tap $needle $1"; exit 0
    fi
    dump
    if [ "$cmd" = find ]; then
      nodes | grep -i -- "$needle" | while read -r n; do
        printf '%s  %s\n' "$(center "$n")" "$(sed -n 's/.*text="\([^"]*\)".*/\1/p' <<<"$n")"
      done
      exit 0
    fi
    hit=$(nodes | grep -i -- "text=\"$needle\"" | head -1) || true
    if [ -z "$hit" ] && [ "$cmd" != tap-text ]; then
      hit=$(nodes | grep -i -- "content-desc=\"$needle\"" | head -1) || true
    fi
    [ -n "$hit" ] || { echo "not found: $needle" >&2; exit 1; }
    read -r x y <<<"$(center "$hit")"
    $ADB shell input tap "$x" "$y"
    echo "tap $x $y  <- $needle"
    ;;
  type)
    type_text "${1:?usage: ui.sh type <text>}"
    ;;
  field)
    # Fill the n-th text field (0 = top-most) and clear it first, so the call is repeatable.
    idx=${1:?usage: ui.sh field <index> <text>}
    txt=${2:?usage: ui.sh field <index> <text>}
    dump
    read -r x y <<<"$(fields | sed -n "$((idx + 1))p")"
    [ -n "${x:-}" ] || { echo "no text field #$idx on screen" >&2; exit 1; }
    $ADB shell input tap "$x" "$y"; sleep 1
    clear_focused_field
    sleep 1
    type_text "$txt"
    ;;
  key)   $ADB shell input keyevent "${1:?usage: ui.sh key <NAME|code>}" ;;
  swipe) $ADB shell input swipe "$1" "$2" "$3" "$4" "${5:-250}"; echo "swipe $1 $2 -> $3 $4 (${5:-250}ms)" ;;
  ime)
    case ${1:-state} in
      off) $ADB shell input keyevent 4 ;;
      on)  $ADB shell input tap "${2:-540}" "${3:-1900}" ;;
      state) ;;
      *) echo "usage: ui.sh ime on|off|state" >&2; exit 2 ;;
    esac
    $ADB shell dumpsys input_method | grep -o 'mInputShown=[a-z]*' | head -1
    ;;
  drag-shot)
    # Screenshot in the middle of a gesture. `input swipe` only returns when the gesture is
    # over, so it has to run in the background while we capture — this is the only way to
    # photograph a pressed state, a crosshair or a mid-drag drawer.
    out=${1:?usage: ui.sh drag-shot <file> <x1> <y1> <x2> <y2> [durationMs] [delayMs]}; shift
    dur=${5:-2500}; delay=${6:-1.2}
    ($ADB shell input swipe "$1" "$2" "$3" "$4" "$dur" &) 
    sleep "$delay"
    mkdir -p "$(dirname "$out")"
    $ADB exec-out screencap -p > "$out"
    echo "$out ($(magick identify -format '%wx%h' "$out" 2>/dev/null || echo '?'))"
    ;;
  shot)
    out=${1:-$ROOT/shots/ui.png}; mkdir -p "$(dirname "$out")"
    $ADB exec-out screencap -p > "$out"
    echo "$out ($(magick identify -format '%wx%h' "$out" 2>/dev/null || echo '?'))"
    ;;
  wait) sleep "${1:-1}" ;;
  *) echo "unknown command: $cmd" >&2; sed -n '2,24p' "$0" >&2; exit 2 ;;
esac
