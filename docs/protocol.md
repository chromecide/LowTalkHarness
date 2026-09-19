# How a test session runs

Written after the session of 2026-09-19, which found three real bugs and wasted most of its time doing it.
The bugs were worth having. The waste was not, and all of it came from the same thing: testing, diagnosing and
fixing happening at once, in the same world, against a jar that kept changing underneath.

This is the order that stops that. It is not ceremony — each rule below is here because its absence cost an
hour of a real evening.

## The two roles

**Justin is the hands.** In game, walking, clicking, looking at things a server cannot see. He does not read
code and does not need to. The only thing he ever has to type is `/harness feedback <what went wrong>`.

**Claude is everything else.** Builds, deploys, bounces, reads logs, records verdicts, fixes, and decides what
needs re-running. If an answer can be got from the log, Claude gets it from the log rather than asking.

A question put to Justin costs a context switch and an interruption to the walk. Two were needed on
2026-09-19; both were avoidable and both became tools (`/harness names`, `[harness] opened <id>`).

## The phases

Do them in order. Do not overlap them.

### 1. Prepare — no server running

- Decide the build under test and build it: `./gradlew buildAll` in LowTalk, `./gradlew buildAll` in the
  harness.
- Archive the Hytale server jar for this version (`~/hytale-mods/tools/hytale-archive.sh`) so
  `whatChanged` has something to compare against later.
- Deploy both jars, boot, and note the build id the harness prints.
- **Build any diagnostic you think you might need now.** Adding one mid-session costs a stop, a jar, a start,
  a rejoin and a walk back to wherever you were. On 2026-09-19 that happened twice.

Nothing in this phase happens once the walk has started.

### 2. Ground state — one command, before anything is judged

- `/lowtalk testworld build`. It rebuilds the corridor, respawns the stations, and clears the block bindings
  and NPCs inside it.
- For a release candidate, start from a **fresh world**, not an accumulated one.

This phase exists because of the door. A door had been knocked into the corridor wall and bound days earlier;
a later rebuild filled the hole back in and left the binding pointing at solid stone. The next restart looked
exactly like block bindings no longer surviving restarts. An hour went into a bug that was not there, and a
FAIL was recorded against evidence that did not exist.

**A world you have been editing for days is not a test fixture.**

### 3. Walk — uninterrupted, one build, one pass

- Talk to the harness tester and work through the tree. Options disappear as their checks run, so the tree
  empties as you go.
- Silence is a pass. A check that ran and drew no comment is recorded PASS, so **most of the walk is typing
  nothing at all**.
- When something is wrong: `/harness feedback the goblin title never drew`, and **keep walking**. It fails
  that check with your own words and is read back at the end.

Rules for this phase, all of them load-bearing:

- **The jar does not change during a walk.** Swapping it splits the evidence: half the record then describes a
  build that no longer exists. If a fix is urgent enough to deploy mid-walk, the walk restarts.
- **Nothing is fixed during a walk.** Finding a bug is not permission to go and fix it; that is how a session
  ends with three fixes and a third of a walk.
- **Nothing is judged that nobody saw.** A verdict recorded from reasoning rather than observation is worse
  than no verdict, because it looks the same in the file.

### 4. Bounce — once, at the end

Restart-dependent checks are batched here rather than run whenever they come up, because each one costs a full
stop and start. Set up everything that has to survive a restart during the walk (rename an NPC, bind a block),
then bounce once and check them together.

### 5. Report and triage

- `/harness report` — the pass and fail counts and every piece of feedback, in the tester's own words.
- Each piece of feedback becomes exactly one of: a fix to make, a check-level FAIL that stands, or a
  documented gap in [gaps.md](gaps.md). Nothing is left as a note nobody acted on.

### 6. Fix — no game

Changes happen here, with the server down or ignored. Each fix names the check it answers, in the commit.

### 7. Re-test — a new cycle

A fix means a new build, which means a new build id, which means phase 1 again. Re-run the checks the fixes
name, plus whatever `./gradlew whatChanged <old> <new>` names if the Hytale server also moved. Not the whole
tree — that is what the per-check records are for.

The tester hides an option once its check has run, so put the ones you mean to re-run back first:
`/harness reset title` for a group, `/harness reset all` before a release walk. Results are kept until then,
carried over from the build that saw them and reported as such, which is the honest reading: evidence about a
jar that no longer exists.

## What the harness enforces, and what is only discipline

Enforced:

- A check that ran is recorded automatically; it cannot be forgotten or overstated.
- A command that throws fails its check without anyone noticing it.
- A `pass` is refused for a watched check that was never seen.
- Every result is stamped with the LowTalk build it was observed on, and `/harness report` calls out results
  carried over from an older build than the one running.
- `CheckCoverageTest` fails the build when LowTalk grows a command or function no check mentions.

Discipline, not enforced:

- One build per walk.
- No fixing mid-walk.
- Ground state before judging.

If one of these keeps being broken, it should become enforcement rather than a stronger sentence in this file.

## The release gate

Before a LowTalk version is tagged:

1. One clean walk on the **final jar**, on the **release line** server (the pre-release line is informative,
   never a gate — LowTalk ships against the release line).
2. Every result stamped with that jar's build id. A pass carried over from an earlier build does not count
   towards the gate, however recently it was observed.
3. Zero outstanding feedback: everything reported is fixed, failed with a reason, or moved to gaps.md.
4. Every gap listed in gaps.md with why it has no station.
5. The built jar booted on a plain server, not just the dev one — see the mod's own release notes for why.

A release is allowed to have gaps. It is not allowed to have unknowns that nobody wrote down.
