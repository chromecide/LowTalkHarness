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
- **Verdict** — what someone said about it afterwards. No event can tell you that a title rendered in the wrong
  style, so that has to be typed in.

**Silence is a pass.** A check that ran and drew no comment counts as PASS. The alternative — a tester typing
`/harness pass` forty-eight times at the end of a walk — produces forty-eight keystrokes carrying no
information, and a strong pull towards typing them without looking, which is exactly the kind of record that
reads as rigour and is not. What carries information is a complaint, so the complaint is the only thing anyone
has to type:

```
/harness feedback station 5 title didn't show when i selected goblin
```

Free text, no id, no syntax. It is recorded against whatever passage you were last in — which is almost always
what it is about — and that check goes FAIL with your words as the note. Everything reported this way is read
back at the end by `/harness report`.

So the only state that is genuinely unknown is **never run**, and that is what `/harness todo` counts.

Records live in the server's `mods/Chromecide_LowTalkHarness/` folder, one file per server version, so testing
0.7 never erases what 0.6.7 proved.

## Commands

| Command | What it does |
|---|---|
| `/harness feedback <what went wrong>` | Say it in your own words; fails the check you are on and is kept for the end |
| `/harness report` | The pass/fail count and everything reported on this version |
| `/harness todo` | What has never been run on this server version |
| `/harness show <id>` | One check: the steps, what should happen, and how it went here |
| `/harness undo` | Take back the last piece of feedback, and the failure it caused |
| `/harness npc` | Put the tester NPC in front of you |
| `/harness hud` | The corner panel, on or off |
| `/harness reset <id>` | Forget a check so the tester offers it again |
| `/harness pass\|fail\|skip <id> [--note="..."]` | Override by hand |

`feedback` takes a check id as its first word if you give one — `/harness feedback title.goblinbreach it drew
as Major` — for when you have walked on before writing it down. Feedback that matches no check is kept anyway,
unattached, and read out with the rest: a complaint the harness cannot file is still a complaint.

`pass` is refused for a check the harness never saw run, because a record of passes that were never executed is
worse than no record. It is otherwise an override, for lifting a failure after a second look.

On `pass`/`fail`/`skip` the note is an option, not a positional argument, and must be quoted if it has spaces:
`/harness fail title.goblinbreach --note="looked identical to Major"`. `feedback` takes the rest of the line as
written, which is the point of it.

A check removed from `Checks.java` leaves its result behind in records written before it went. Those are moved
to a `retired` section of the file at boot and logged once: they stop being counted, without the file losing
what really happened on a real server.

## How a session runs

[docs/protocol.md](docs/protocol.md). Prepare, ground state, walk, one bounce, report, fix, re-test — in that
order, without overlapping. Written after an evening that found three real bugs and spent most of itself
recovering from testing, diagnosing and fixing all happening at once against a jar that kept changing.

## Which checks to re-run after a Hytale update

```
./gradlew whatChanged --args="~/hytale-archive/release/0.6.7 ~/hytale-archive/release/0.6.8"
```

Compares two archived server jars class by class and names the checks whose surfaces moved, so a server release
means re-running a few rather than all of them or none. It also lists classes that changed, are used by LowTalk,
and are covered by no check — the blind spots that update opened. Reads the archive kept by
`~/hytale-mods/tools/hytale-archive.sh`.

## The check list

`Checks.java`, written by hand rather than generated from the station dialogues. Most checks are one option in
one station, but the ones that get forgotten are the ones that are not: a sequence across two stations, something
that has to survive a restart, a setting that must be changed first, or the in-game editor, which has no station
and cannot have one. A list generated from options would contain only the easy shape and look complete.

Each check names the surfaces it exercises, so a Hytale update can say which checks to re-run rather than all of
them: when a version diff touches `EventTitleUtil`, the checks tagged with it are the ones that matter.

Checks with no station and nothing to watch are still listed, so they show as never run rather than not showing
at all. [docs/gaps.md](docs/gaps.md) says why each one has no station — several cannot have one, and the
in-game editor, the largest surface in the mod, has no automated coverage whatsoever.

`CheckCoverageTest` fails the build when LowTalk grows a command or function no check mentions. Not "must be
tested" — a gap is a fine answer — but a feature nobody has thought about is not.

## Running it

Needs LowTalk in the sibling checkout at `../lowtalk`; the composite build compiles both from source so the
harness always tests the LowTalk in this tree.

```
./gradlew build          # jar into build/libs
```

Drop the jar into a server's `mods/` next to LowTalk's, then `/lowtalk testworld build` and walk the corridor.

## Licence

MIT, same as LowTalk.
