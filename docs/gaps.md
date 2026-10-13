# What is not covered, and why

Eleven of the sixty-seven checks have no result on the walk 0.5.0 was tagged on. They are in the list anyway,
so they show in `/harness todo` as never run rather than not appearing at all. A gap you can see is a gap
someone can close.

**Two different things are listed here, and conflating them would flatter the record.** Seven have nothing
driving them: no passage, no opener, no way for the harness to know they happened, so closing one means
building something. Four have a driver and were simply not walked this time — a decision to defer, not an
absence of coverage. They are named as such below, because "nobody has built this" and "nobody did it on
Tuesday" deserve different answers.

This file is why each one is outstanding, which is usually more interesting than the gap itself.

Written against LowTalk 0.5.0, after the gate walk it was tagged on: Hytale 0.7.0, build `b6ad2eca`. One jar
this release — 0.7.0 stable supersedes the pre-release line, so there is no second line to owe a walk to.
Six entries this file used to have have been closed.

## Needs the server set up differently

**`run.command.gated`.** The only command behind a security flag, and it has to be seen in both states —
`AllowRunCommand` off, then on. A dialogue cannot change server config halfway through, and a station that
runs console commands is not something to hand a creator.

**`layout.chain`.** Four levels resolve in order — dialogue, pack, API, server config — and `ForceLayout`
overrides all of them. Checking it means editing config between attempts. Four documented levels, none of them
ever verified.

## Needs something in the world that is not there

**`trigger.volume`.** LowTalk registers `LowTalkDialogue`, `LowTalkCondition` and `LowTalkSetVariable` with the
trigger volume plugin on every boot, and nothing exercises any of them. It needs a volume placed in the world
rather than an NPC to talk to.

**`state.role`.** `<<state>>` needs an NPC whose role defines two real states. The harness tester does not
have one: asked for its own list it reports `Idle` and `start`, and `start` is the engine's placeholder, with
no sub-states, so entering it throws inside the game. The game's own `Test_State_*` roles define several and
would close this — the same way a role that can fight closed `attitude.behaviour`.

**`objective.talk.task`.** An objective completed by talking to a second NPC: start `Objective_LowTalk_Talk`
at station 12, then walk to station 1. It crosses two stations, so no single passage marks it done. Worth
knowing that Hytale's objective system is rough ground — see the warning in the format guide — so a failure
here is as likely to be the game's as ours.

## The editor

**`editor.add.every.command`, `editor.rows.render`.** The in-game editor is the largest surface in the mod and
has no automated coverage at all — no station can open a UI page and judge it. Every editor bug so far has been
found by a person using it: a reward row that dropped the client, a dropdown that would not open, a panel that
ran off the bottom of the screen, and an Add menu that added the wrong command. Then 0.5.0 added two more — a
Bind button that stayed disabled when you chose a dialogue, and a `<<shop>>` the editor would build and then
refuse to save. Six client-visible faults in one surface, every one found by luck.

Worth noting what did catch the 0.5.0 pair in the end, because it was not a walk: both were one fact written
in two places with nothing comparing them, and both are now held by a test that compares the two places. That
is cheaper than driving the UI and it closes a whole shape of bug rather than one instance. It is not a
substitute for opening the editor and looking — nothing yet is — but it is the first thing in this surface
that a build can check.

Still the most valuable thing left to build.

## Not gaps: driven, and deferred to the next release

These four have everything they need. They were left for 0.6.0 as a deliberate call, and saying so is the
point of listing them apart — a reader who cannot tell these from the seven above is being told the coverage
is worse than it is.

**`block.bind.survives.restart`.** Arguably done and unrecorded. The 0.5.0 walk bound a dialogue to a door,
the server was stopped and started, the boot log reported `1 block binding(s) loaded`, and the log then shows
`opened test_basics (BLOCK)` — a block opening a dialogue after a restart, which is the whole claim. What is
missing is a person's verdict, and the gate reserves that for somebody who actually looked, so an unrecorded
pass stays unrecorded rather than being inferred from a log line.

**`npc.rename.survives.restart`.** The tester grew an option for this during 0.5.0 — it renames itself and
leaves the name changed, so the check can be armed from the same NPC that drives everything else instead of
needing a second one. It was armed and the server was bounced; the nameplate was not looked at afterwards.
One `/harness names` away.

**`block.bind.single`.** Bind a dialogue to something one block tall, such as a chest, and use it. Proves
base-block resolution did not break the ordinary case while the door case was being fixed. Nothing stands in
the way of it; it is a minute of work that did not get done.

**`station.talker`.** A dialogue opened by the NPC's own role through `LowTalkOpenDialogue`, with LowTalk's
use hook not involved. The corridor has the station and the harness watches the passage, so it is driven — but
it needs a role wired that way and the player in Adventure mode, and the walk ran in Creative.

## What closed, and how

Kept because the reasons these were once written off are worth remembering — most of them were conclusions
about the obvious approach rather than about the question.

- **`perm.check`** — "needs the same player with and without a permission". True of the naive approach, and
  an Admin holds the wildcard so nothing comes back false either. A permission can be written as a deny with
  a leading minus, user entries are read before group ones, and a deny beats the wildcard. The tester denies
  itself a node, asks, grants it, asks again, and puts the player back.
- **`learn.recipe`** — "depends on what the player already knows". Only if you do not control it. The harness
  forgets the recipe first. Closing it found the runtime's command ordering bug, so it is now that fix's
  regression test.
- **`player.npc.names`** — never done, no reason. Done.
- **`npc.rename.survives.restart`** — promised since the feature shipped, never checked, and false when
  finally checked: nothing marked the entity dirty, so the rename was never saved.
- **`on.join`** — "requires a disconnect and a reconnect, which no dialogue can ask for". True, and beside
  the point: the reconnect was never the hard part. The real reason it had never run is that no dialogue
  anywhere declared `on: join`, so there was nothing to reconnect *into* — the check could not be done at all,
  by anyone, and that had been read as a scheduling problem for two releases. The harness ships a dialogue
  with the directive now, gated on `visited()` so it opens once per player instead of nagging on every login.
  It marks itself from the opener rather than a passage, because the passage watcher ignores anything not
  opened by an NPC or a role; nobody can click a dialogue open with `Opener.JOIN`, so the opener is honest
  evidence. Closed on the first bounce after it shipped.
- **`attitude.set`, now `attitude.behaviour`** — "the corridor's testers are deliberately placid". The answer
  was an NPC that is not: `/harness fighter` spawns a goblin. Closing it produced three facts about attitude
  that no amount of reading the code had produced, and one new LowTalk command.

## Blind spots the server can move under us

`./gradlew whatChanged <old> <new>` compares two archived server jars and, as well as naming the checks worth
re-running, lists classes that **changed, are used by LowTalk, and are named by no check**. Those are the
places a Hytale update can break something with nothing watching.

Run against `0.6.7 -> 0.6.8` it named none, because that update changed one Windows-only class. Run against
`0.7.0-pre.2.1 -> 0.7.0-pre.3` it named fifteen, including `ISpawnProvider` — the class whose signature change
broke the build on pre.3, and which no check mentions to this day.

It compares **classes**, though, and assets move too. `Goblin_Scavenger` exists on 0.6.8 and not on
0.7.0-pre.3.1, which is why the fighter check spawned nothing on the pre-release line and said so only in the
tester's chat. Every check that names a role, a particle, a recipe or a weather rests on an asset id that can
move with nothing watching. The fighter tries a list of roles now, which survives one rename; comparing the
asset maps of two versions the way whatChanged compares their classes is not done.
