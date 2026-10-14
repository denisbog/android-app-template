#!/usr/bin/env bash
# Drive the running app on the emulator: tap / type / swipe / screenshot, by text OR pixel.
#
#   ./ui.sh here                       # everything on screen, in screen order: center | label
#   ./ui.sh find "Match"               # matching controls, numbered
#   ./ui.sh tap "Match"                # by text or content-desc, SUBSTRING match
#   ./ui.sh tap 74 220                 # or raw pixels (the screenshot is 1080x2400 here)
#   ./ui.sh tap-text "Save"            # match `text` only (default also matches content-desc)
#   ./ui.sh type "hello world"         # into whatever has focus
#   ./ui.sh field 0 "Ada Lovelace"     # clear the top-most text field, then type (and verify)
#   ./ui.sh clear 0                    # just clear it (an empty `field` value also works)
#   ./ui.sh type-keys "Pa55w0rd!"      # type by keyevent: use when `input text` drops characters
#   ./ui.sh key BACK | key ENTER | key 4
#   ./ui.sh swipe 540 1800 540 600 300 # x1 y1 x2 y2 [duration ms]
#   ./ui.sh ime off                    # hide the keyboard (`ime on` needs coordinates)
#   ./ui.sh drag-shot shots/x.png 380 900 720 900   # screenshot DURING a drag
#   ./ui.sh shot shots/x.png
#   ./ui.sh wait 2
#   ./ui.sh wait-for "Saved" 10        # poll until it appears; non-zero exit on timeout
#   ./ui.sh assert "Saved"             # fail (exit 1) if it is not on screen
#   ./ui.sh assert-not "Loading"       # fail if it *is* on screen
#
# Compose exposes both `text` and `content-desc` to the accessibility tree, so targets can be
# named instead of guessed from pixels. Everything below works on that tree via uiautomator.
#
# Matching is whole-string first, then a SUBSTRING fallback on `text`/`content-desc`, because
# real labels carry leading glyphs and trailing arrows ("⧉  Match", "Continue with your account
# →", "＋  New article") that no caller should have to reproduce. It is fixed-string, so nothing
# needs escaping. TAP_EXACT=1 forbids the fallback.
#
# When a needle matches several *different* places, the command lists them and exits 1 instead
# of silently tapping the first: picking one of three cards labelled "Match" and calling it a
# pass is worse than an error. Choose with NTH=2. Nodes that share a centre are collapsed first,
# because Compose merges semantics and one control is often a parent plus its labelled child.
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
field_nodes() { nodes | grep 'class="android.widget.EditText"'; }
fields() { # every text field, top-most first: "cx cy" per line
  field_nodes | while read -r n; do center "$n"; done | sort -k2 -n
}
field_text() { # the n-th field's value, as the accessibility tree reports it ('' if masked)
  field_nodes | sed -n "${1:-1}p" | sed -n 's/.*text="\([^"]*\)".*/\1/p'
}
text_of() { sed -n 's/.*text="\([^"]*\)".*/\1/p' <<<"$1"; }
desc_of() { sed -n 's/.*content-desc="\([^"]*\)".*/\1/p' <<<"$1"; }
label_of() { # "" when the node has neither text nor content-desc (so it is not listed at all)
  local t d; t=$(text_of "$1"); d=$(desc_of "$1")
  [ -n "$t$d" ] || return 0
  printf '%s' "${t:-[$d]}"
}

# Node lines whose text="…" — or content-desc="…" when mode is `both` — contains `needle`.
# Fixed-string matching via index(), so glyphs and regex metacharacters in labels are literal.
# `how` is `substr` or `exact`; exact compares the whole attribute.
match_nodes() { # $1 needle · $2 text|both · $3 substr|exact
  nodes | awk -v n="$1" -v mode="$2" -v how="$3" '
    function inside(v) { return (how == "exact") ? (v == n) : (index(v, n) > 0) }
    {
      t = ""; d = ""
      if (match($0, /text="[^"]*"/))         t = substr($0, RSTART + 6,  RLENGTH - 7)
      if (match($0, /content-desc="[^"]*"/)) d = substr($0, RSTART + 14, RLENGTH - 15)
      if (inside(t)) { print; next }
      if (mode == "both" && inside(d)) print
    }'
}

# Collapse nodes that share a centre: Compose merges semantics, so one control is often the
# clickable parent plus its labelled child. Ambiguity should mean different places, not that.
unique_centres() {
  while IFS= read -r n; do printf '%s\t%s\n' "$(center "$n")" "$n"; done |
    sort -u -k1,1 | cut -f2-
}
type_text() { # `input text` needs spaces as %s and eats shell metacharacters
  esc=$(printf '%s' "$1" | sed 's/ /%s/g; s/\([()<>|&;*\\"'"'"']\)/\\\1/g')
  $ADB shell input text "$esc"
}
# One character -> the keycode(s) `input keyevent`/`input keycombination` needs, or empty when
# unmapped. Uppercase and shifted symbols are a two-code combination: `input text` cannot
# express them reliably, but `input keycombination` can (it is listed in `input` usage).
keycode_for() {
  case $1 in
    [a-z]) printf '%s' "$(( $(printf '%d' "'$1") - 97 + 29 ))" ;;
    [A-Z]) printf '59 %s' "$(( $(printf '%d' "'$1") - 65 + 29 ))" ;;
    [0-9]) printf '%s' "$(( $1 + 7 ))" ;;
    ' ')   printf '62' ;;
    '-') printf '69' ;;  '=') printf '70' ;;  '[') printf '71' ;;  ']') printf '72' ;;
    '\\') printf '73' ;;  ';') printf '74' ;;  "'") printf '75' ;;  ',') printf '55' ;;
    '.') printf '56' ;;  '/') printf '76' ;;  '`') printf '68' ;;
    '!') printf '59 8'  ;;  '@') printf '59 77' ;;  '#') printf '59 18' ;;  '$') printf '59 11' ;;
    '%') printf '59 12' ;;  '^') printf '59 13' ;;  '&') printf '59 14' ;;  '*') printf '59 15' ;;
    '(') printf '59 16' ;;  ')') printf '59 17' ;;  '_') printf '59 69' ;;  '+') printf '59 70' ;;
    '{') printf '59 71' ;;  '}') printf '59 72' ;;  '|') printf '59 73' ;;  ':') printf '59 74' ;;
    '"') printf '59 75' ;;  '<') printf '59 55' ;;  '>') printf '59 56' ;;  '?') printf '59 76' ;;
    '~') printf '59 68' ;;
    *) printf '' ;;
  esac
}

type_keys() { # type one character at a time, by keycode — reliable where `input text` is lossy
  local line=$1 i c seq
  for (( i = 0; i < ${#line}; i++ )); do
    c=${line:i:1}
    seq=$(keycode_for "$c")
    # Refuse rather than type something wrong: a silently corrupted password is much more
    # expensive to debug than a missing keycode.
    [ -n "$seq" ] || { echo "type-keys: no keycode for character #$((i + 1))" >&2; return 1; }
    if [ "${seq#* }" != "$seq" ]; then
      $ADB shell input keycombination $seq
    else
      $ADB shell input keyevent "$seq"
    fi
  done
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
    # Sorted by y then x: uiautomator dumps in tree order, which tells you nothing about where
    # things are on screen.
    nodes | while read -r n; do
      lbl=$(label_of "$n")
      [ -n "$lbl" ] && printf '%s  %s\n' "$(center "$n")" "$lbl"
    done | sort -k2,2n -k1,1n
    ;;
  find|tap|tap-text)
    needle=${1:?usage: ui.sh $cmd <text>}; [ $# -gt 1 ] && shift
    if [[ $needle =~ ^[0-9]+$ ]] && [ $# -ge 1 ]; then   # raw coordinates
      $ADB shell input tap "$needle" "$1"; echo "tap $needle $1"; exit 0
    fi
    dump
    mode=both; [ "$cmd" = tap-text ] && mode=text
    # A whole-string match wins; substring is only a fallback. That keeps every existing call
    # working (`tap "Save"` still means the Save button, even though the sidebar also contains
    # "Saved to SharedPreferences…") while making the decorated labels reachable
    # (`tap "Match"` finds "⧉  Match"). TAP_EXACT=1 forbids the fallback.
    hits=$(match_nodes "$needle" "$mode" exact | unique_centres)
    [ -n "$hits" ] || [ "${TAP_EXACT:-0}" = 1 ] || hits=$(match_nodes "$needle" "$mode" substr | unique_centres)

    if [ "$cmd" = find ]; then
      [ -n "$hits" ] || { echo "no match: $needle" >&2; exit 1; }
      i=0
      printf '%s\n' "$hits" | while IFS= read -r n; do
        i=$((i + 1))
        printf '%2d) %s  %s\n' "$i" "$(center "$n")" "$(label_of "$n")"
      done
      exit 0
    fi

    [ -n "$hits" ] || { echo "not found: $needle" >&2; exit 1; }
    count=$(printf '%s\n' "$hits" | grep -c .)
    if [ "$count" -gt 1 ] && [ -z "${NTH:-}" ]; then
      echo "ambiguous: $count matches for '$needle' — choose one with NTH=<n>:" >&2
      i=0
      printf '%s\n' "$hits" | while IFS= read -r n; do
        i=$((i + 1))
        printf '  %2d) %s  %s\n' "$i" "$(center "$n")" "$(label_of "$n")" >&2
      done
      exit 1
    fi
    hit=$(printf '%s\n' "$hits" | sed -n "${NTH:-1}p")
    read -r x y <<<"$(center "$hit")"
    $ADB shell input tap "$x" "$y"
    echo "tap $x $y  <- $(label_of "$hit")"
    ;;
  type)
    type_text "${1:?usage: ui.sh type <text>}"
    ;;
  type-keys)
    type_keys "${1:?usage: ui.sh type-keys <text>}"
    ;;
  field|clear)
    # Fill the n-th text field (0 = top-most) and clear it first, so the call is repeatable.
    idx=${1:?usage: ui.sh $cmd <index> [text]}
    # `clear` takes no value, and for `field` an empty value is exactly how you clear the field
    # — `${2:?}` rejected it as "unset", which made `field 0 ""` print a usage error and do
    # nothing. `${2-}` accepts both "unset" and "empty"; the check below keeps the typo case.
    [ "$cmd" = field ] && [ $# -lt 2 ] && { echo "usage: ui.sh field <index> <text>" >&2; exit 2; }
    txt=${2-}
    dump
    read -r x y <<<"$(fields | sed -n "$((idx + 1))p")"
    [ -n "${x:-}" ] || { echo "no text field #$idx on screen" >&2; exit 1; }
    $ADB shell input tap "$x" "$y"; sleep 1
    clear_focused_field
    [ "$cmd" = clear ] && { echo "cleared text field #$idx"; exit 0; }
    sleep 1
    # `input text ""` throws IllegalArgumentException from the shell — skip it for a plain clear.
    [ -n "$txt" ] && type_text "$txt"

    # `input text` silently drops characters (measured: 15 in, 13 out, for a value holding an
    # uppercase letter and a symbol). Read the field back, so a value that never arrived is
    # reported here instead of surfacing later as a mysterious failure in the app.
    [ -n "$txt" ] || { echo "cleared text field #$idx"; exit 0; }
    sleep 0.3
    dump
    got=$(field_text "$((idx + 1))")
    if [ "$got" = "$txt" ]; then
      :
    elif [ -z "$got" ] || printf '%s' "$got" | grep -q '^[•*·.]\+$'; then
      echo "field #$idx: cannot verify (masked or unreadable) — if the value matters, use type-keys" >&2
    else
      echo "field #$idx: typed '$txt' but the field holds '$got'" >&2
      echo "  'input text' drops characters — retry with: ui.sh type-keys '$txt'" >&2
    fi
    ;;
  key)   $ADB shell input keyevent "${1:?usage: ui.sh key <NAME|code>}" ;;
  swipe) $ADB shell input swipe "$1" "$2" "$3" "$4" "${5:-250}"; echo "swipe $1 $2 -> $3 $4 (${5:-250}ms)" ;;
  ime)
    case ${1:-state} in
      off)
        # BACK is the only lever that works here — ESC (keyevent 111) does not dismiss the IME
        # on every image. But BACK is *context dependent*: it hides the keyboard when the app
        # handles back, and finishes the activity when it does not, which drops you on the
        # launcher with no explanation. So report both facts instead of just firing it.
        $ADB shell input keyevent 4
        sleep 0.5
        printf 'ime:   %s\n' "$($ADB shell dumpsys input_method | grep -o 'mInputShown=[a-z]*' | head -1)"
        printf 'focus: %s\n' "$($ADB shell dumpsys window 2>/dev/null | grep -o 'mCurrentFocus=Window{[^}]*}' | head -1)"
        if $ADB shell dumpsys window 2>/dev/null | grep -q 'mCurrentFocus=Window{.*launcher'; then
          echo "note: BACK left the app — that is a missing back handler in it, not a script bug" >&2
          echo "      (see docs/PITFALLS.md §1.6: keep a BackHandler enabled while a field has focus)" >&2
        fi
        ;;
      on)  $ADB shell input tap "${2:-540}" "${3:-1900}" ;;
      state) ;;
      *) echo "usage: ui.sh ime on|off|state" >&2; exit 2 ;;
    esac
    [ "${1:-state}" = off ] || $ADB shell dumpsys input_method | grep -o 'mInputShown=[a-z]*' | head -1
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
  wait-for)
    needle=${1:?usage: ui.sh wait-for <text> [timeoutSeconds]}
    limit=${2:-10}
    # `dump` costs roughly half a second, so that is the poll cadence.
    for _ in $(seq 1 $(( limit * 2 ))); do
      dump
      if match_nodes "$needle" both substr | grep -q .; then
        echo "found: $needle"; exit 0
      fi
      sleep 0.5
    done
    echo "TIMEOUT after ${limit}s waiting for: $needle" >&2
    exit 1
    ;;
  assert|assert-not)
    needle=${1:?usage: ui.sh $cmd <text>}
    dump
    if match_nodes "$needle" both substr | grep -q .; then
      present=1
    else
      present=0
    fi
    if [ "$cmd" = assert ] && [ "$present" = 0 ]; then
      echo "ASSERT FAILED: '$needle' is not on screen" >&2; exit 1
    fi
    if [ "$cmd" = assert-not ] && [ "$present" = 1 ]; then
      echo "ASSERT FAILED: '$needle' is still on screen" >&2; exit 1
    fi
    echo "ok: $cmd $needle"
    ;;
  *) echo "unknown command: $cmd" >&2; sed -n '2,24p' "$0" >&2; exit 2 ;;
esac
