package com.chromecide.lowtalkharness;

import com.chromecide.lowtalk.parser.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every command and function LowTalk offers must be named by some check.
 *
 * <p>Not "must be tested" — a check can be a gap, with no station and nothing to watch, and that is a fine
 * answer. What is not a fine answer is a feature nobody has thought about, which is what {@code <<run>>},
 * {@code <<attitude>>}, {@code <<learn>>}, {@code <<state>>}, {@code knows()} and {@code perm()} all were until
 * this test was written: shipped, documented, and absent from every list of what to try.
 *
 * <p>So this fails the build when LowTalk grows a command that no check mentions. Adding a feature means saying
 * how it will be checked, even if the honest answer for now is "by hand, nobody has yet".
 */
class CheckCoverageTest {

    /** Checks tag what they exercise as "lowtalk:&lt;name&gt;" alongside Hytale class names. */
    private static Set<String> taggedLowTalkNames() {
        Set<String> out = new LinkedHashSet<>();
        for (String surface : Checks.coveredSurfaces()) {
            if (surface.toLowerCase(Locale.ROOT).startsWith("lowtalk:")) {
                out.add(surface.substring("lowtalk:".length()).toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    @Test
    void everyBuiltInCommandIsNamedBySomeCheck() {
        Set<String> tagged = taggedLowTalkNames();
        List<String> missing = new ArrayList<>();
        for (String command : Validator.BUILTIN_COMMANDS.keySet()) {
            if (!tagged.contains(command.toLowerCase(Locale.ROOT))) missing.add(command);
        }
        missing.sort(null);
        assertTrue(missing.isEmpty(),
                "these commands are in no check, so nobody is even meaning to test them: " + missing
                        + "\nAdd a check in Checks.java — a gap with no station is a valid answer.");
    }

    @Test
    void everyBuiltInFunctionIsNamedBySomeCheck() {
        Set<String> tagged = taggedLowTalkNames();
        List<String> missing = new ArrayList<>();
        for (String function : Validator.BUILTIN_FUNCTIONS) {
            if (!tagged.contains(function.toLowerCase(Locale.ROOT))) missing.add(function);
        }
        missing.sort(null);
        assertTrue(missing.isEmpty(),
                "these functions are in no check: " + missing
                        + "\nAdd a check in Checks.java — a gap with no station is a valid answer.");
    }

    /** A tag naming a command that no longer exists means the check is describing something gone. */
    @Test
    void noCheckClaimsACommandOrFunctionThatIsGone() {
        Set<String> real = new LinkedHashSet<>();
        Validator.BUILTIN_COMMANDS.keySet().forEach(c -> real.add(c.toLowerCase(Locale.ROOT)));
        Validator.BUILTIN_FUNCTIONS.forEach(f -> real.add(f.toLowerCase(Locale.ROOT)));
        // Two sorts of tag name nothing in those lists and should not: the language's own statement keywords,
        // which are parsed rather than registered as commands, and concerns broader than any one command.
        Set<String> keywords = Set.of("set", "jump", "end", "input", "wait", "once", "show", "include");
        Set<String> concerns = Set.of("editor", "addmenu", "ui", "config", "blockbind", "trigger", "onjoin",
                "layout", "options", "variation", "roleaction", "lowtalknode");
        Set<String> notCommands = new LinkedHashSet<>(keywords);
        notCommands.addAll(concerns);
        List<String> unknown = new ArrayList<>();
        for (String tag : taggedLowTalkNames()) {
            if (!real.contains(tag) && !notCommands.contains(tag)) unknown.add(tag);
        }
        unknown.sort(null);
        assertTrue(unknown.isEmpty(),
                "these checks name a command or function LowTalk does not have: " + unknown
                        + "\nEither it was renamed and the check should follow, or the tag is a typo.");
    }

    /** A check that watches a passage has to name one that could exist. */
    @Test
    void everyWatchedPassageIsWellFormed() {
        for (Checks.Check c : Checks.all()) {
            String watch = c.autoSeen();
            if (watch == null) continue;
            assertTrue(watch.matches("[a-z0-9_]+/[a-z0-9_]+"),
                    c.id() + " watches '" + watch + "', which is not a dialogue/passage pair");
        }
    }

    /** Ids are the key in every run record on every version, so a duplicate would merge two histories. */
    @Test
    void idsAreUniqueAndStable() {
        assertEquals(Checks.all().size(), Checks.ids().stream().distinct().count());
        for (Checks.Check c : Checks.all()) {
            assertTrue(c.id().matches("[a-z][a-z0-9.]*"),
                    c.id() + " should be lower case and dotted, so it reads the same everywhere it appears");
        }
    }

    /** A check nobody can act on is not a check. */
    @Test
    void everyCheckSaysWhatToDoAndWhatToExpect() {
        for (Checks.Check c : Checks.all()) {
            assertTrue(!c.steps().isEmpty() && !c.steps().get(0).isBlank(), c.id() + " has no steps");
            assertTrue(c.expected().length() > 20, c.id() + " does not say what to expect in any useful detail");
            assertTrue(!c.covers().isEmpty(), c.id() + " names no surface, so no server update can ever flag it");
        }
    }
}
