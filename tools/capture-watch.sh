#!/usr/bin/env bash
#
# Photograph the screen the moment a check runs.
#
# The server cannot see the client. A title is sent as a one-way ShowEventTitle packet with no acknowledgement,
# so nothing on the server can say whether anything appeared, let alone whether it looked right. The client is
# on this machine though, so the screen can be captured while the effect is still on it, and the picture filed
# against the check and the server version.
#
# That does not make the judgement automatic. It makes the evidence exist: something to look at afterwards,
# something to attach to a run, and something to compare with the same check on the next version.
#
# Run it beside the server, then walk the corridor:
#
#   tools/capture-watch.sh ~/hytale-mods/lowtalk-firstrun/server.log
#   tools/capture-watch.sh <log> --delay 0.8 --out ~/Desktop/evidence
#
# The Hytale client has to be the frontmost window when the check fires, because it draws its own surface and
# reports no window to the accessibility API, so there is nothing to capture by window id. This takes the whole
# screen.
#
set -euo pipefail

LOG="${1:-}"
DELAY=0.6
OUT=""

[ -n "$LOG" ] || { echo "usage: $(basename "$0") <server.log> [--delay 0.6] [--out DIR]" >&2; exit 1; }
shift || true
while [ $# -gt 0 ]; do
    case "$1" in
        --delay) DELAY="${2:-0.6}"; shift 2 ;;
        --out)   OUT="${2:-}"; shift 2 ;;
        *)       echo "unknown option: $1" >&2; exit 1 ;;
    esac
done

[ -f "$LOG" ] || { echo "no log at $LOG" >&2; exit 1; }
command -v screencapture >/dev/null || { echo "screencapture not found; this is macOS only" >&2; exit 1; }

# The server version the harness reported at boot, so pictures file alongside the record they belong to.
version=$(grep -ao 'harness watching server [^:]*' "$LOG" | tail -1 | awk '{print $4}')
[ -n "$version" ] || version="unknown"

if [ -z "$OUT" ]; then
    OUT="$(cd "$(dirname "$LOG")" && pwd)/mods/Chromecide_LowTalkHarness/evidence/$version"
fi
mkdir -p "$OUT"

echo "watching $LOG"
echo "server   $version"
echo "writing  $OUT"
echo "The Hytale client must be frontmost when a check runs. Ctrl-C to stop."

# Only new lines: a log full of earlier runs must not fire a burst of captures of whatever is on screen now.
tail -n 0 -F "$LOG" 2>/dev/null | while IFS= read -r line; do
    case "$line" in
        *"[harness] seen "*) ;;
        *) continue ;;
    esac
    # strip the ANSI the server colours its log with, then take the id after the marker
    id=$(printf '%s' "$line" | sed 's/\x1b\[[0-9;]*m//g' | sed 's/.*\[harness\] seen //' | awk '{print $1}')
    [ -n "$id" ] || continue
    sleep "$DELAY"
    shot="$OUT/${id}-$(date +%Y%m%d-%H%M%S).png"
    if screencapture -x "$shot" 2>/dev/null; then
        printf '  %s -> %s\n' "$id" "$(basename "$shot")"
    else
        printf '  %s -> capture failed (screen recording permission?)\n' "$id"
    fi
done
