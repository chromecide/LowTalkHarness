package com.chromecide.lowtalkharness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule the whole harness now turns on: a check that ran and drew no comment is a pass, and the only way a
 * check goes bad is that something said so — a command that threw, or a person who typed what they saw.
 *
 * <p>Worth pinning down in tests because it is a claim about a released mod's evidence. If silence ever stopped
 * meaning a pass, every record on disk would quietly change meaning, and nothing in the game would say so.
 */
class RunRecordTest {

    /** An id that really exists, so nothing under test lands in the retired pile by accident. */
    private static String anId() {
        return Checks.ids().get(0);
    }

    private static String anotherId() {
        return Checks.ids().get(1);
    }

    @Test
    void runningACheckIsEnoughToPassIt(@TempDir Path dir) {
        RunRecord r = new RunRecord(dir, "0.0.0-test");
        String id = anId();

        assertNull(r.outcome(id), "a check nobody has run has no outcome at all");
        r.markSeen(id);
        assertEquals(RunRecord.Verdict.PASS, r.outcome(id), "running it and saying nothing is a pass");
        assertTrue(r.check(id).impliedPass(), "and the pass should know it was never spoken");
    }

    @Test
    void anExplicitVerdictWinsOverSilence(@TempDir Path dir) {
        RunRecord r = new RunRecord(dir, "0.0.0-test");
        String id = anId();
        r.markSeen(id);
        r.decide(id, RunRecord.Verdict.FAIL, "the banner never drew");

        assertEquals(RunRecord.Verdict.FAIL, r.outcome(id));
        assertFalse(r.check(id).impliedPass());
        r.undecide(id);
        assertEquals(RunRecord.Verdict.PASS, r.outcome(id), "taking the verdict back leaves what running implies");
    }

    /** A check nobody ran cannot be passed by silence: there was no silence, there was nobody. */
    @Test
    void neverRunIsNotAPass(@TempDir Path dir) {
        RunRecord r = new RunRecord(dir, "0.0.0-test");
        int[] t = r.tally(Checks.ids());
        assertEquals(0, t[1], "nothing decided");
        assertEquals(0, t[2], "and nothing passed");
    }

    @Test
    void feedbackFailsTheCheckItIsAbout(@TempDir Path dir) {
        RunRecord r = new RunRecord(dir, "0.0.0-test");
        String id = anId();
        r.markSeen(id);

        r.addFeedback("station 5 title didn't show when i selected goblin", "player-1", "harness",
                "check_title_goblin", List.of(id));

        assertEquals(RunRecord.Verdict.FAIL, r.outcome(id));
        assertEquals("station 5 title didn't show when i selected goblin", r.check(id).note);
        assertEquals(1, r.feedback().size());
    }

    /** Feedback that fits no check is still feedback, and must survive to be read out at the end. */
    @Test
    void feedbackWithNothingToPinItOnIsKept(@TempDir Path dir) {
        RunRecord r = new RunRecord(dir, "0.0.0-test");
        r.addFeedback("the whole world went orange", "player-1", null, null, List.of());
        r.flush();

        RunRecord reloaded = new RunRecord(dir, "0.0.0-test");
        assertEquals(1, reloaded.feedback().size());
        assertEquals("the whole world went orange", reloaded.feedback().get(0).text);
        assertEquals(0, reloaded.tally(Checks.ids())[3], "and it fails nothing, because it named nothing");
    }

    @Test
    void takingFeedbackBackTakesTheFailureWithIt(@TempDir Path dir) {
        RunRecord r = new RunRecord(dir, "0.0.0-test");
        String id = anId();
        r.markSeen(id);
        r.addFeedback("wrong style", "player-1", "harness", "check", List.of(id));

        assertTrue(r.dropFeedback(0));
        assertEquals(0, r.feedback().size());
        assertEquals(RunRecord.Verdict.PASS, r.outcome(id), "back to what running it implies, not to nothing");
        assertTrue(r.check(id).seen, "and it is still recorded as having run");
    }

    /** Undoing one complaint must not lift a failure that came from somewhere else. */
    @Test
    void takingFeedbackBackLeavesOtherFailuresAlone(@TempDir Path dir) {
        RunRecord r = new RunRecord(dir, "0.0.0-test");
        String id = anId();
        r.markSeen(id);
        r.decide(id, RunRecord.Verdict.FAIL, "<<title>> failed: no such style");
        r.addFeedback("looked odd", "player-1", "harness", "check", List.of(id));
        r.decide(id, RunRecord.Verdict.FAIL, "<<title>> failed: no such style");  // the command failed again

        r.dropFeedback(0);
        assertEquals(RunRecord.Verdict.FAIL, r.outcome(id), "the command's own failure stands on its own");
    }

    @Test
    void everythingSurvivesAWriteAndAReload(@TempDir Path dir) {
        RunRecord r = new RunRecord(dir, "0.0.0-test");
        String ran = anId(), broke = anotherId();
        r.markSeen(ran);
        r.markSeen(broke);
        r.addFeedback("the second one drew twice", "player-1", "harness", "check_two", List.of(broke));
        r.flush();

        RunRecord reloaded = new RunRecord(dir, "0.0.0-test");
        assertEquals(RunRecord.Verdict.PASS, reloaded.outcome(ran));
        assertEquals(RunRecord.Verdict.FAIL, reloaded.outcome(broke));
        assertEquals(List.of("the second one drew twice"),
                reloaded.feedback().stream().map(f -> f.text).toList());
        assertEquals(List.of(broke), reloaded.feedback().get(0).checks);
    }

    /**
     * A check dropped from the list leaves a result behind in every record written before it went. It must stop
     * being counted — a removed check is not a passing one — without the file quietly losing what really happened.
     */
    @Test
    void aResultForACheckThatNoLongerExistsIsSetAsideNotCounted(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("run-0.0.0-test.json");
        java.nio.file.Files.writeString(file, """
                {
                  "serverVersion": "0.0.0-test",
                  "checks": {
                    "station.feedback": { "seen": true, "verdict": "PASS" }
                  }
                }
                """);

        RunRecord r = new RunRecord(dir, "0.0.0-test");
        assertEquals(List.of("station.feedback"), r.retiredIds());
        assertEquals(0, r.tally(Checks.ids())[1], "it counts towards nothing");

        r.flush();
        String written = java.nio.file.Files.readString(file);
        assertTrue(written.contains("retired"), "but it is still in the file");
        assertTrue(written.contains("station.feedback"));
    }

    /**
     * The rule that decides whether a pass may be typed by hand. A check the harness watches must have been
     * watched happening; a check nothing watches can only ever be judged by hand, so the same rule would leave
     * it able to fail and never able to pass — which is most of what is left on any version.
     */
    @Test
    void onlyWatchedChecksMustBeSeenBeforeTheyCanPass() {
        assertTrue(Checks.isWatched("title.major"), "a tree check is watched, so a pass has to be earned");
        assertFalse(Checks.isWatched("editor.rows.render"), "the editor has nothing watching it and never will");
        assertFalse(Checks.isWatched("no.such.check"), "and an id that names nothing is not watched either");
    }
}
