# LowTalk Harness

A test harness for [LowTalk](https://github.com/chromecide/LowTalk), as a separate Hytale mod.

**This is not a mod to install on a real server.** It exists so LowTalk can be tested the same way every time,
on every release and pre-release of Hytale, with a record of what was actually tried.

## Why a separate mod

Three reasons, in the order they matter:

1. **The LowTalk under test is the jar that ships.** A harness built into LowTalk behind a flag would mean the
   artifact people download is the one least exercised. Here the harness sits beside the real release jar.
2. **Nothing to strip before release.** No build flags, no source sets, no check that the harness did not leak
   into the release.
3. **It is a real consumer of LowTalk's public API.** That API is a shipped surface. Until this existed it had
   one consumer using one of its four events, so most of it had never been used by anything but its author.
   Where the harness cannot see something it needs, that is an API gap to fix in LowTalk rather than a reason
   to reach inside.

## What it does

Watches dialogues through `DialogueListener` and records, **per Hytale server version**, which checks were
exercised and how they went.

Two separate facts are kept for each check:

- **Seen** — the harness observed it happen. Recorded automatically from LowTalk's own events, so it is never
  forgotten and never wrong.
- **Verdict** — a person said whether it looked right. No event can tell you that a title rendered in the wrong
  style, so this is typed in.

A check that is *seen but undecided* is the interesting state: it ran, and nobody said whether it worked.

Records live in the server's `mods/Chromecide_LowTalkHarness/` folder, one file per server version, so testing
0.7 never erases what 0.6.7 proved.

## Commands

| Command | What it does |
|---|---|
| `/harness todo` | What still needs running or judging on this server version |
| `/harness show <id>` | One check: the steps, what should happen, and how it went here |
| `/harness pass <id> [note]` | Record that it looked right |
| `/harness fail <id> [note]` | Record that it did not, with what you saw |
| `/harness skip <id> [note]` | Record that it does not apply on this version |

`pass` is refused for a check the harness never saw run, because a record of passes that were never executed is
worse than no record.

## The check list

`Checks.java`, written by hand rather than generated from the station dialogues. Most checks are one option in
one station, but the ones that get forgotten are the ones that are not: a sequence across two stations, something
that has to survive a restart, a setting that must be changed first, or the in-game editor, which has no station
and cannot have one. A list generated from options would contain only the easy shape and look complete.

Each check names the surfaces it exercises, so a Hytale update can say which checks to re-run rather than all of
them: when a version diff touches `EventTitleUtil`, the checks tagged with it are the ones that matter.

## Running it

Needs LowTalk in the sibling checkout at `../lowtalk`; the composite build compiles both from source so the
harness always tests the LowTalk in this tree.

```
./gradlew build          # jar into build/libs
```

Drop the jar into a server's `mods/` next to LowTalk's, then `/lowtalk testworld build` and walk the corridor.

## Licence

MIT, same as LowTalk.
