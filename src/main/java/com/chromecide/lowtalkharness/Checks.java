package com.chromecide.lowtalkharness;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What there is to test, as data.
 *
 * <p>Deliberately not derived from the station dialogues. Most checks are one option in one station, but the
 * ones that get forgotten are the ones that are not: a sequence across two stations, something that has to
 * survive a restart, a setting that has to be changed first, or the in-game editor, which has no station and
 * cannot have one. A list generated from options would quietly contain only the easy shape and would look
 * complete while missing the parts most likely to break.
 *
 * <p>Each check names the Hytale or LowTalk surfaces it exercises. That is what lets a server update say which
 * checks to re-run rather than all of them: when a version diff touches EventTitleUtil, the checks tagged with
 * it are the ones that matter.
 */
public final class Checks {

    /** The shape of a check, which is mostly a statement about how easy it is to forget. */
    public enum Kind {
        /** One option in one station: pick it, look at what happened. */
        STATION,
        /** Several steps, in order, usually across more than one station. */
        SEQUENCE,
        /** Something must still be true after the server restarts. */
        RESTART,
        /** Needs a setting changed, or a game mode, before it means anything. */
        SETUP,
        /** The in-game editor or a bind page: no NPC asks the question, so it is walked by hand. */
        EDITOR,
        /** Done out in the world rather than at a station. */
        WORLD
    }

    /**
     * @param id       stable, dotted, never reused for anything else; this is the key in every run record
     * @param kind     how it has to be run
     * @param title    what a person doing it reads
     * @param steps    what to do, in order; a single-step check has one
     * @param expected what should happen, in enough detail to judge
     * @param covers   the surfaces this exercises, for targeting re-runs after a server update
     * @param station  the station number when there is one, else null
     * @param autoSeen the LowTalk dialogue and passage that mean "this was exercised", as "dialogue/passage",
     *                 or null when nothing observable says so and a person has to say
     */
    public record Check(@Nonnull String id, @Nonnull Kind kind, @Nonnull String title, @Nonnull List<String> steps,
                        @Nonnull String expected, @Nonnull List<String> covers, @Nullable Integer station,
                        @Nullable String autoSeen) {}

    private static final Map<String, Check> BY_ID = new LinkedHashMap<>();

    private static void add(Check c) {
        if (BY_ID.put(c.id(), c) != null) throw new IllegalStateException("two checks share the id " + c.id());
    }

    private static Check station(String id, int station, String title, String expected, String autoSeen, String... covers) {
        return new Check(id, Kind.STATION, title, List.of(title), expected, List.of(covers), station, autoSeen);
    }

    static {
        // ---- Station 5, feedback. The titles are here because 0.7 changed how a style is named, so these are
        // the checks a server update is most likely to disturb.
        add(station("title.default", 5, "A title with no style named",
                "A small title across the screen for about 3 seconds, with the second line above the main one.",
                "test_feedback/title_default", "EventTitleUtil", "lowtalk:title"));
        add(station("title.minor.alias", 5, "A title written the old way, as \"minor\"",
                "Exactly the same as title.default. If it differs, the alias older dialogues rely on has stopped meaning Default.",
                "test_feedback/title_minor", "EventTitleUtil", "lowtalk:title"));
        add(station("title.major", 5, "A title in the Major style",
                "A large cinematic title, plainly different from Default, for about 4 seconds.",
                "test_feedback/title_major", "EventTitleUtil", "lowtalk:title"));
        add(station("title.goblinbreach", 5, "A title in the GoblinBreach style (0.7 and later)",
                "The game's goblin-breach treatment, different from Major. On a server without the style the window closes and the log names the style it could not find, which is correct.",
                "test_feedback/title_goblinbreach", "EventTitleUtil", "EventTitleStyle", "lowtalk:title"));
        add(station("title.voideviction", 5, "A title in the VoidEviction style (0.7 and later)",
                "The game's void-eviction treatment, different again from Major and GoblinBreach.",
                "test_feedback/title_voideviction", "EventTitleUtil", "EventTitleStyle", "lowtalk:title"));
        add(station("notify.toast", 5, "A toast notification",
                "A small notification in the corner reading \"LowTalk test\" / \"Notification works\".",
                "test_feedback/start", "NotificationUtil", "lowtalk:notify"));
        add(station("notify.warning", 5, "A toast in the warning style",
                "The same, in the game's warning colour.",
                "test_feedback/start", "NotificationUtil", "lowtalk:notify"));

        // ---- The shapes that get forgotten. These are the reason this list is written by hand.
        add(new Check("block.bind.door", Kind.WORLD,
                "Bind a dialogue to a door, then use the door",
                List.of("Point the LowTalk tool at a door and bind a dialogue to it.",
                        "Walk away, then use the door normally."),
                "The dialogue opens instead of the door opening. A door is two blocks and only its base is reported, "
                        + "so this is the check that catches a binding recorded against the wrong half.",
                List.of("BlockReads", "UseBlockEvent", "lowtalk:blockbind"), null, null));
        add(new Check("block.bind.single", Kind.WORLD,
                "Bind a dialogue to a single-block target",
                List.of("Bind a dialogue to something one block tall, such as a lantern or a chest.",
                        "Use it."),
                "The dialogue opens. Proves the base-block resolution did not break the ordinary case.",
                List.of("BlockReads", "UseBlockEvent", "lowtalk:blockbind"), null, null));
        add(new Check("block.bind.survives.restart", Kind.RESTART,
                "A block binding survives a server restart",
                List.of("Bind a dialogue to a block.", "Stop the server and start it again.",
                        "Use the block."),
                "The boot log says the binding was loaded, and using the block still opens the dialogue.",
                List.of("lowtalk:blockbind"), null, null));
        add(new Check("npc.rename.survives.restart", Kind.RESTART,
                "A renamed NPC keeps its name across a restart",
                List.of("At station 12, rename the NPC.", "Stop the server and start it again.",
                        "Look at the NPC."),
                "The nameplate still shows the new name. Station 12 has always claimed this; nothing has ever checked it.",
                List.of("lowtalk:npc_name"), 12, null));
        add(new Check("objective.talk.task", Kind.SEQUENCE,
                "An objective completed by talking to a different NPC",
                List.of("At station 12, start Objective_LowTalk_Talk.",
                        "Walk to station 1 and talk to it."),
                "The tracker shows \"Talk to the station 1 tester\" and the objective completes on arrival. This is "
                        + "LowTalk's own LowTalkNode task type, and it is the only check that spans two stations.",
                List.of("ObjectivePlugin", "lowtalk:LowTalkNode"), 12, null));
        add(new Check("editor.add.every.command", Kind.EDITOR,
                "Every command can be found in the editor's Add menu",
                List.of("Open the in-game editor on any passage.",
                        "Open the Add dropdown and search for a command you did not write the menu for, such as \"rain\".",
                        "Add it and check the row is the command you asked for."),
                "The menu names every built-in command in plain words, the search finds them, and the row that appears "
                        + "is the one named. The editor has no station and cannot have one, so it is walked by hand.",
                List.of("lowtalk:editor", "lowtalk:addmenu"), null, null));
        add(new Check("run.command.gated", Kind.SETUP,
                "<<run>> does nothing unless the server allows it",
                List.of("Set AllowRunCommand to false in lowtalk.json and reload.",
                        "Trigger a dialogue that uses <<run>>.",
                        "Set it to true, reload, and trigger it again."),
                "Refused while off, runs while on. This is the only command behind a security flag and it has never "
                        + "had a station.",
                List.of("lowtalk:run", "lowtalk:config"), null, null));
    }

    private Checks() {}

    public static List<Check> all() {
        return List.copyOf(BY_ID.values());
    }

    @Nullable
    public static Check byId(@Nonnull String id) {
        return BY_ID.get(id);
    }

    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    /** The checks a LowTalk passage means have been exercised. */
    public static List<Check> forPassage(@Nonnull String dialogueId, @Nonnull String passage) {
        String key = dialogueId + "/" + passage;
        List<Check> out = new ArrayList<>();
        for (Check c : BY_ID.values()) if (key.equals(c.autoSeen())) out.add(c);
        return out;
    }

    /** The checks that touch a named surface, for re-running after a server update changes it. */
    public static List<Check> covering(@Nonnull String surface) {
        List<Check> out = new ArrayList<>();
        for (Check c : BY_ID.values()) {
            for (String s : c.covers()) {
                if (s.toLowerCase(Locale.ROOT).contains(surface.toLowerCase(Locale.ROOT))) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }
}
