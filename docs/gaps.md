# What is not covered, and why

The check list in `Checks.java` includes things that have no station and no way to be tracked automatically.
They are listed as checks anyway, with nothing to watch, so they appear in `/harness todo` as never run rather
than not appearing at all. A gap you can see is a gap someone can close.

This file is why each one is a gap, which is usually more interesting than the gap itself.

## No station can exist for these

**`<<run>>` (`run.command.gated`).** The only command behind a security flag, and a station that runs console
commands is not something to ship to creators. It also needs testing in two states — `AllowRunCommand` off then
on — and a station cannot change server config halfway through.

**`perm()` (`perm.check`).** Needs the same player with and without a permission. One station, one permission
state.

**`on: join` (`on.join`).** Fires when a player finishes loading into a world. Requires a disconnect and
reconnect, which no dialogue can ask for.

**Layout and history (`layout.chain`).** Four levels resolve in order — dialogue, pack, API, server config —
and `ForceLayout` overrides all of them. Checking it means editing config between attempts. Four documented
levels, none of them ever verified.

**Restart persistence (`block.bind.survives.restart`, `npc.rename.survives.restart`).** The claim is that
something survives a server bounce. Station 12 has promised renamed NPCs persist since the feature shipped and
nothing has ever checked it.

## No station exists yet, but could

**`<<attitude>>` (`attitude.set`).** Needs an NPC whose behaviour visibly changes. The corridor's testers are
deliberately placid, so turning one hostile mid-corridor would be unpleasant to walk past afterwards.

**`<<learn>>` and `knows()` (`learn.recipe`).** Needs a recipe the player does not already know, which depends
on the player, so a station would pass or fail depending on who walked up to it.

**`<<state>>` (`state.role`).** Needs an NPC role that defines named states. No station NPC has one.

**`{player}` and `{npc}` (`player.npc.names`).** Used exactly once in the whole corridor. Not hard to cover,
just never done.

**Trigger volumes (`trigger.volume`).** LowTalk registers `LowTalkDialogue`, `LowTalkCondition` and
`LowTalkSetVariable` with the trigger volume plugin on every boot, and nothing exercises any of them. It needs
a volume placed in the world rather than an NPC to talk to.

## The editor

**`editor.add.every.command`, `editor.rows.render`.** The in-game editor is the largest surface in the mod and
has no automated coverage at all — no station can open a UI page and judge it. Every editor bug this month was
found by a person using it: a reward row that dropped the client, a dropdown that would not open, a panel that
ran off the bottom of the screen, and an Add menu that added the wrong command. That is four client-visible
faults in one surface, found by luck rather than by testing.

Worth remembering when weighing what to build next.

## Blind spots the server can move under us

`./gradlew whatChanged <old> <new>` compares two archived server jars and, as well as naming the checks worth
re-running, lists classes that **changed, are used by LowTalk, and are named by no check**. Those are the
places a Hytale update can break something with nothing watching.

Run against `0.7.0-pre.2.1 -> 0.7.0-pre.3` it names fifteen, including `ISpawnProvider` — which is exactly the
class whose signature change broke the build on pre.3, and which no check mentions to this day.
