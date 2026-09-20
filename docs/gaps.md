# What is not covered, and why

Eight of the sixty-seven checks have no passage driving them. They are in the list anyway, so they show in
`/harness todo` as never run rather than not appearing at all. A gap you can see is a gap someone can close.

Two of the eight — the editor pair — are walked by hand every time and recorded that way, so they have
results. The other six were not exercised at all in the 0.4.0 walks: `run.command.gated`, `layout.chain`,
`on.join`, `trigger.volume`, `state.role` and `objective.talk.task`. Sixty-one of the sixty-seven checks
carry a result on both lines.

This file is why each one is a gap, which is usually more interesting than the gap itself.

Written against LowTalk 0.4.0, after the two gate walks it was tagged on: Hytale 0.6.8 (build `9bff6e0d`) and
0.7.0-pre.3.1 (build `9a40a89f`). Five entries this file used to have were closed on the way.

## Needs the server set up differently

**`run.command.gated`.** The only command behind a security flag, and it has to be seen in both states —
`AllowRunCommand` off, then on. A dialogue cannot change server config halfway through, and a station that
runs console commands is not something to hand a creator.

**`layout.chain`.** Four levels resolve in order — dialogue, pack, API, server config — and `ForceLayout`
overrides all of them. Checking it means editing config between attempts. Four documented levels, none of them
ever verified.

**`on.join`.** A dialogue with `on: join` opens when a player finishes loading into a world. Requires a
disconnect and a reconnect, which no dialogue can ask for.

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
has no automated coverage at all — no station can open a UI page and judge it. Every editor bug this month was
found by a person using it: a reward row that dropped the client, a dropdown that would not open, a panel that
ran off the bottom of the screen, and an Add menu that added the wrong command. Four client-visible faults in
one surface, found by luck rather than by testing.

Still the most valuable thing left to build.

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
