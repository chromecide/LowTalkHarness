#!/usr/bin/env bash
#
# Put a test server back to nothing, so a walk describes the jar and not the last three weeks.
#
# Accumulated state has made checks pass and fail for reasons that had nothing to do with the mod: a block
# binding that outlived the corridor rebuild that removed its door, a goblin that came back hostile after a
# restart because its calm was runtime-only, an NPC still carrying a name from a check run days earlier. None
# of that is visible while walking, and all of it is indistinguishable from a real result.
#
# So a gate walk starts from nothing. This deletes the worlds and the mod's own data, and keeps the things
# that are about the machine rather than the test:
#
#   kept     auth.enc         the device login; copying or losing it means logging in again
#   kept     config.json      server settings, including the ones a check depends on
#   kept     permissions.json so the tester is still an operator when it comes back
#   kept     mods/*.jar       the artefact under test
#   deleted  universe/        every world, every entity, every player's inventory and position
#   deleted  mods/Chromecide_LowTalk/data/   block bindings, NPC tags, dialogue variables
#
# Usage: tools/reset-world.sh ~/hytale-mods/lowtalk-firstrun
#
set -euo pipefail

DIR="${1:-}"
if [ -z "$DIR" ] || [ ! -d "$DIR" ]; then
  echo "Usage: $0 <server directory>"
  exit 1
fi
if [ ! -f "$DIR/config.json" ]; then
  echo "$DIR does not look like a Hytale server directory (no config.json)."
  exit 1
fi
# Look for a live JVM, not for the string anywhere in a process list: the shell that pipes a console into
# the server keeps the jar's path in its own command line long after the server itself has exited, and a
# check that matched that refused to run with nothing running.
if ps -eo command | grep -v "sh -c" | grep -q "[b]in/java.*HytaleServer.jar"; then
  echo "A Hytale server is running. Stop it first, or it will write its state back out as it exits."
  exit 1
fi

echo "About to reset $DIR"
for path in "$DIR/universe" "$DIR/mods/Chromecide_LowTalk/data"; do
  if [ -e "$path" ]; then
    echo "  delete  $path  ($(du -sh "$path" 2>/dev/null | cut -f1))"
  fi
done
for keep in auth.enc config.json permissions.json; do
  [ -e "$DIR/$keep" ] && echo "  keep    $DIR/$keep"
done

rm -rf "$DIR/universe" "$DIR/mods/Chromecide_LowTalk/data"
echo "Done. The next boot makes fresh worlds; the corridor, the tester and any block bindings must be rebuilt."
