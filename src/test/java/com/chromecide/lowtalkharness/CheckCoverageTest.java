package com.chromecide.lowtalkharness;

import com.chromecide.lowtalk.parser.Validator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
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

    /**
     * A check watching a passage of the harness's own dialogues must name one that is really there.
     *
     * <p>A watched passage that does not exist never fires, and nothing says so: the check simply sits as never
     * run for ever, looking like work outstanding rather than a typo. That is how the station 2 check was
     * broken — it watched "start", and station 2 has no passage by that name, because two guarded starts are
     * the thing it exists to demonstrate.
     *
     * <p>Only our own dialogues can be checked this way. Checks that watch LowTalk's corridor name passages in
     * a file this project does not own, so they are left to the eye.
     */
    @Test
    void everyWatchedPassageInOurOwnDialoguesExists() throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of("src/main/resources/Server/LowTalk/Dialogues");
        if (!java.nio.file.Files.isDirectory(dir)) return;

        Map<String, Set<String>> passages = new LinkedHashMap<>();
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(dir)) {
            for (java.nio.file.Path f : files.filter(x -> x.toString().endsWith(".talk")).toList()) {
                String text = java.nio.file.Files.readString(f);
                Set<String> names = new LinkedHashSet<>();
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(?m)^==\\s*(\\w+)\\s*$").matcher(text);
                while (m.find()) names.add(m.group(1));
                String dialogue = f.getFileName().toString().replace(".talk", "");
                passages.put(dialogue, names);
                // an included file's passages belong to whoever includes it, so pool them under the includer too
                if (dialogue.startsWith("_")) {
                    passages.computeIfAbsent("harness", k -> new LinkedHashSet<>()).addAll(names);
                }
            }
        }

        List<String> missing = new ArrayList<>();
        for (Checks.Check c : Checks.all()) {
            // Both halves. A mistyped failure passage is the worse of the two: the dialogue decides the check
            // failed, jumps somewhere nothing is watching, and the run record shows a pass.
            for (String watch : new String[] {c.autoSeen(), c.autoFail()}) {
                if (watch == null) continue;
                String dialogue = watch.substring(0, watch.indexOf('/'));
                String passage = watch.substring(watch.indexOf('/') + 1);
                Set<String> known = passages.get(dialogue);
                if (known == null) continue;                    // not one of ours; nothing to check against
                if (!known.contains(passage)) missing.add(c.id() + " watches " + watch);
            }
        }
        assertTrue(missing.isEmpty(),
                "these checks watch a passage that does not exist, so they can never fire:\n  "
                        + String.join("\n  ", missing));
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

    /**
     * A link to another page of the tree must never be guarded.
     *
     * <p>The page links were once guarded on a hand-written sum of the check prefixes behind them. Four checks
     * were added without extending the sum, every older group had been walked, so the sum was zero and two
     * whole pages of the tree stopped being offered — silently, because a hidden option looks exactly like a
     * finished one. The tester reported the new checks "not showing" and nothing in the record disagreed.
     *
     * <p>An area may hide itself when there is nothing left in it. Navigation may not.
     */
    @Test
    void linksBetweenPagesOfTheTreeAreNotGuarded() throws Exception {
        List<String> lines = Files.readAllLines(
                Path.of("src/main/resources/Server/LowTalk/Dialogues/harness.talk"));
        List<String> guarded = new ArrayList<>();
        for (int i = 0; i < lines.size() - 1; i++) {
            String option = lines.get(i).strip();
            if (!option.startsWith("-> ") || !option.contains("<<if")) continue;
            String body = lines.get(i + 1).strip();
            // a page is a hub passage: start, start_more, start_more2, ...
            if (body.matches("<<jump start(_\\w+)?>>")) guarded.add((i + 1) + ": " + option);
        }
        assertTrue(guarded.isEmpty(),
                "these options lead to another page of the tree and are guarded, so the page can hide itself:\n  "
                        + String.join("\n  ", guarded));
    }

    /**
     * A passage never speaks two lines in a row.
     *
     * <p>Each line in a .talk file is its own dialogue line with its own Continue button, so a sentence
     * wrapped across two source lines is shown to the player cut in half: they read as far as the wrap, press
     * Continue, and get the rest. Seventeen passages in this tree were written that way, and walking it meant
     * "continue, continue, continue to get through basic dialog" — the tester's words.
     *
     * <p>The label wraps and the transcript scrolls, so a long line is fine. A wrapped one is not.
     */
    @Test
    void noPassageSpeaksTwoLinesInARow() throws Exception {
        List<String> lines = Files.readAllLines(
                Path.of("src/main/resources/Server/LowTalk/Dialogues/harness.talk"));
        // One passage speaks twice on purpose: the Continue button between two lines is the thing it checks.
        // It is marked with a comment on the line above rather than exempted by name, so the exception has to
        // be written down where the next person reads it.
        // Only inside passages: the file's own header (npc:, title:, include:) is the same shape as prose.
        int firstPassage = 0;
        while (firstPassage < lines.size() && !lines.get(firstPassage).startsWith("==")) firstPassage++;

        List<String> split = new ArrayList<>();
        for (int i = firstPassage; i < lines.size() - 1; i++) {
            if (!spoken(lines.get(i)) || !spoken(lines.get(i + 1))) continue;
            boolean deliberate = false;
            for (int back = i - 1; back >= 0 && back >= i - 3; back--) {
                if (lines.get(back).startsWith("#") && lines.get(back).contains("on purpose")) deliberate = true;
                if (lines.get(back).startsWith("==")) break;
            }
            if (!deliberate) split.add((i + 1) + ": " + lines.get(i).strip());
        }
        assertTrue(split.isEmpty(),
                "these lines are followed by another spoken line, so the player reads them cut in half:\n  "
                        + String.join("\n  ", split));
    }

    /** A line the NPC says: not a comment, a heading, an option, a command, or anything inside a block. */
    private static boolean spoken(String line) {
        if (line.isBlank() || Character.isWhitespace(line.charAt(0))) return false;
        return !line.startsWith("#") && !line.startsWith("==") && !line.startsWith("->") && !line.startsWith("<<");
    }

    /**
     * Every function and command the tree uses is one the harness registers.
     *
     * <p>LowTalk reads an unknown function as false and an unknown command as a warning, so a dialogue that
     * calls something nobody provides does not fail — it quietly takes the other branch. The fighter's
     * check asks {@code player_detectable()} before it starts, and when that function was removed by mistake
     * during a tidy-up the check reported that the player was invisible, on every run, to a tester who was
     * standing right in front of it. The server said so in a warning at boot that nobody read.
     *
     * <p>Registrations live in Java and calls live in .talk files, so nothing but a test connects them.
     */
    @Test
    void everythingTheTreeCallsIsRegistered() throws Exception {
        Path plugin = Path.of("src/main/java/com/chromecide/lowtalkharness/HarnessPlugin.java");
        String java = Files.readString(plugin);
        Set<String> registered = new LinkedHashSet<>();
        Matcher reg = Pattern.compile("register(?:Function|Command)\\(\"([a-z_]+)\"").matcher(java);
        while (reg.find()) registered.add(reg.group(1));

        // what LowTalk itself provides; the tree may use those freely
        Set<String> builtIn = Set.of("player", "npc", "has", "count", "visited", "objective", "attitude",
                "perm", "hour", "random", "chance", "ordinal", "plural", "reputation", "rank", "stat",
                "max_stat", "effect", "knows", "objective_line", "t", "weather");

        List<String> unknown = new ArrayList<>();
        Path dialogues = Path.of("src/main/resources/Server/LowTalk/Dialogues");
        try (var files = Files.list(dialogues)) {
            for (Path talk : files.filter(f -> f.toString().endsWith(".talk")).toList()) {
                // Only where an expression can live: inside {...} and inside <<...>>. Prose is full of
                // things that look like calls -- "you have opened a tester 3 time(s)" is not a call to time().
                String text = Files.readString(talk);
                Matcher region = Pattern.compile("\\{[^}]*\\}|<<[^>]*>>").matcher(text);
                while (region.find()) {
                    Matcher call = Pattern.compile("\\b([a-z_]{3,})\\(").matcher(region.group());
                    while (call.find()) {
                        String name = call.group(1);
                        if (!registered.contains(name) && !builtIn.contains(name) && !name.equals("if")) {
                            unknown.add(talk.getFileName() + ": " + name + "()");
                        }
                    }
                }
            }
        }
        assertTrue(unknown.isEmpty(),
                "the tree calls these and nothing registers them, so LowTalk reads them as false and the "
                        + "checks quietly take the wrong branch:\n  "
                        + String.join("\n  ", unknown.stream().distinct().toList()));
    }
}
