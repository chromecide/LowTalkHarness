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
 * <p>Deliberately not generated from the station dialogues. Most checks are one station, but the ones that get
 * forgotten are the ones that are not: a sequence across two stations, something that has to survive a restart,
 * a setting that must be changed first, or the in-game editor, which has no station and cannot have one. A list
 * generated from what exists would contain only what exists, and look complete.
 *
 * <p><b>Granularity.</b> A check is marked as run by watching which passage the player reached, so a check can
 * only be tracked on its own if it has a passage of its own. Station 5's title styles do, because each one was
 * split out for exactly that reason. Everywhere else the options run from the station's own passage and jump
 * back to it, so the station is the unit: reaching it means it was visited, and the verdict covers the station.
 * Splitting a station is worth doing when one of its behaviours starts changing independently of the rest.
 *
 * <p><b>Gaps are checks too.</b> Anything with no station at all is listed here with no passage to watch, so it
 * sits in {@code /harness todo} as never run rather than being invisible. That is the honest state: the work is
 * known, and nobody has done it. See {@code docs/gaps.md} for why each one has no station.
 *
 * <p>Each check names the surfaces it exercises, so a Hytale update can say which checks to re-run rather than
 * all of them.
 */
public final class Checks {

    /** The shape of a check, which is mostly a statement about how easy it is to forget. */
    public enum Kind {
        /** A station in the test corridor: walk up, talk, work through its options. */
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
     * @param covers   the surfaces this exercises: Hytale class names, and lowtalk:&lt;command or function&gt;
     * @param station  the station number when there is one, else null
     * @param autoSeen the dialogue and passage that mean "this was exercised", as "dialogue/passage", or null
     *                 when nothing observable says so and only a person can
     */
    public record Check(@Nonnull String id, @Nonnull Kind kind, @Nonnull String title, @Nonnull List<String> steps,
                        @Nonnull String expected, @Nonnull List<String> covers, @Nullable Integer station,
                        @Nullable String autoSeen) {}

    private static final Map<String, Check> BY_ID = new LinkedHashMap<>();

    private static void add(Check c) {
        if (BY_ID.put(c.id(), c) != null) throw new IllegalStateException("two checks share the id " + c.id());
    }

    /** A whole station, tracked by arriving at it. */
    private static void station(String id, int number, String dialogue, String title, String expected, String... covers) {
        add(new Check(id, Kind.STATION, title, List.of("Walk to station " + number + " and work through its options."),
                expected, List.of(covers), number, dialogue + "/start"));
    }

    /** One behaviour inside a station that was split out so it could be tracked on its own. */
    private static void split(String id, int number, String passage, String title, String expected, String... covers) {
        add(new Check(id, Kind.STATION, title, List.of(title), expected, List.of(covers), number, passage));
    }

    /** Something real that no station covers. Listed so it is outstanding rather than forgotten. */
    private static void gap(String id, Kind kind, String title, List<String> steps, String expected, String... covers) {
        add(new Check(id, kind, title, steps, expected, List.of(covers), null, null));
    }

    static {
        // ---- the corridor, one check per station -------------------------------------------------------------
        station("station.basics", 1, "test_basics", "Choices, hubs, Continue and Leave",
                "Two lines in a row give a Continue between them; a hidden option appears only after it is revealed; "
                        + "a disabled option is greyed and does nothing; jump moves to another passage; Leave closes "
                        + "the window after one line.",
                "lowtalk:options", "lowtalk:jump", "lowtalk:end", "lowtalk:show");
        station("station.memory", 2, "test_memory", "Memory: per-NPC, per-player, once-blocks and visited()",
                "visited() is false before the passage is seen and true after; a once-block runs once; resetting the "
                        + "NPC's memory makes the guarded start behave as it did the first time.",
                "lowtalk:set", "lowtalk:once", "lowtalk:visited");
        station("station.input", 3, "test_input", "Typed input, interpolation and plural()",
                "The text box takes what is typed, Enter submits, the answer comes back interpolated into a line, "
                        + "and plural() agrees with the number.",
                "lowtalk:input", "lowtalk:plural");
        station("station.items", 4, "test_items", "give, take, has() and count()",
                "Two bread arrive with a narration line and appear in the inventory; taking one hands it back and "
                        + "fails cleanly with none; the has() option appears only while holding three.",
                "lowtalk:give", "lowtalk:take", "lowtalk:has", "lowtalk:count");
        station("station.feedback", 5, "test_feedback", "Notifications, sound and animation",
                "A toast appears in the corner, the warning style differs from the default, the pickup sound plays "
                        + "at the NPC, and the NPC emotes. Titles have their own checks.",
                "NotificationUtil", "lowtalk:notify", "lowtalk:sound", "lowtalk:anim");
        station("station.body", 6, "test_body", "heal, stat, effect, cure and reading stats",
                "Health returns to full, can be set and added to, a buff icon appears and effect() sees it, and "
                        + "curing removes the icon.",
                "lowtalk:heal", "lowtalk:stat", "lowtalk:effect", "lowtalk:cure", "lowtalk:max_stat");
        station("station.progress", 7, "test_progress", "Objectives and reputation",
                "An objective starts and reads as active; reputation rises and falls by ten and the rank changes at "
                        + "the boundary. See the objective warning: the tracker is unreliable on this version.",
                "ObjectivePlugin", "ReputationPlugin", "lowtalk:objective", "lowtalk:reputation",
                "lowtalk:rank", "lowtalk:stat");
        station("station.travel", 8, "test_travel", "The shop hand-off and teleport",
                "The barter shop opens and the conversation resumes on Back; teleport moves the player and ends the "
                        + "conversation.",
                "lowtalk:shop", "lowtalk:teleport");
        station("station.random", 9, "test_random", "random(), chance(), ordinal() and hour()",
                "Rolling again changes the numbers, the ordinal reads correctly, and hour() matches the world clock.",
                "lowtalk:random", "lowtalk:chance", "lowtalk:ordinal", "lowtalk:hour");
        station("station.format", 10, "test_format", "Variations, random blocks, once-options, wait and include",
                "[a|b] varies between openings, a random block picks one alternative, a once-option vanishes after "
                        + "use, the pause has no Continue, and the line from the included file appears.",
                "lowtalk:wait", "lowtalk:include", "lowtalk:variation");
        station("station.world", 11, "test_world", "Weather, time of day and translations",
                "The sky changes and clears again, the clock moves and pauses, and t() returns the translated "
                        + "string rather than the key.",
                "lowtalk:weather", "lowtalk:time", "lowtalk:t");
        station("station.npc", 12, "test_npc", "Renaming, spawning and despawning an NPC",
                "The nameplate changes and changes back, a new NPC appears beside you, despawning closes the window "
                        + "and removes the NPC, and the objective extras start, cancel and chain as described.",
                "lowtalk:npc_name", "lowtalk:spawn", "lowtalk:despawn",
                "lowtalk:objective", "lowtalk:objective_line", "ObjectivePlugin");
        station("station.media", 13, "test_media", "Music, particles and camera shake",
                "The playlist changes and returns to the area's music, particles burst at the NPC and again at twice "
                        + "the size, and the camera shakes hard then gently.",
                "lowtalk:music", "lowtalk:vfx", "lowtalk:camera");
        station("station.talker", 14, "test_talker", "A dialogue opened by the NPC's own role",
                "The NPC opens the dialogue through its role's LowTalkOpenDialogue action, with LowTalk's use hook "
                        + "not involved. Needs Adventure mode, or the NPC brain ignores you.",
                "NPCPlugin", "lowtalk:roleaction");

        // ---- station 5's titles, split out so each style is tracked on its own ---------------------------------
        split("title.default", 5, "test_feedback/title_default", "A title with no style named",
                "A small title for about 3 seconds, with the second line above the main one.",
                "EventTitleUtil", "lowtalk:title");
        split("title.minor.alias", 5, "test_feedback/title_minor", "A title written the old way, as \"minor\"",
                "Exactly the same as title.default. If it differs, the alias older dialogues rely on has stopped "
                        + "meaning Default.",
                "EventTitleUtil", "lowtalk:title");
        split("title.major", 5, "test_feedback/title_major", "A title in the Major style",
                "A large cinematic title, plainly different from Default, for about 4 seconds.",
                "EventTitleUtil", "lowtalk:title");
        split("title.goblinbreach", 5, "test_feedback/title_goblinbreach", "A title in the GoblinBreach style (0.7+)",
                "The game's goblin-breach treatment, different from Major. On a server without the style the command "
                        + "is refused by name and this check fails by itself, which is correct.",
                "EventTitleUtil", "EventTitleStyle", "lowtalk:title");
        split("title.voideviction", 5, "test_feedback/title_voideviction", "A title in the VoidEviction style (0.7+)",
                "The game's void-eviction treatment, different again from Major and GoblinBreach.",
                "EventTitleUtil", "EventTitleStyle", "lowtalk:title");

        // ---- no station covers these. See docs/gaps.md -------------------------------------------------------
        gap("run.command.gated", Kind.SETUP, "<<run>> only works when the server allows it",
                List.of("Set AllowRunCommand to false in lowtalk.json and /lowtalk reload.",
                        "Trigger a dialogue using <<run>>.",
                        "Set it true, reload, and trigger it again."),
                "Refused while off, runs while on. The only command behind a security flag, and it has never had a "
                        + "station because a station that runs console commands is not something to ship.",
                "lowtalk:run", "lowtalk:config");
        gap("attitude.set", Kind.WORLD, "<<attitude>> changes how an NPC regards the player",
                List.of("Bind a dialogue with <<attitude hostile>> to an NPC and open it.",
                        "Check the NPC's behaviour, then set it back to friendly."),
                "The NPC turns on the player and calms again. attitude() reads the same value back.",
                "lowtalk:attitude", "AttitudeGroup");
        gap("learn.recipe", Kind.WORLD, "<<learn>> teaches a recipe, and knows() sees it",
                List.of("Open a dialogue with <<learn>> for a recipe the player does not have.",
                        "Check the crafting menu, and a line guarded by knows()."),
                "The recipe becomes available and knows() returns true for it.",
                "lowtalk:learn", "lowtalk:knows", "CraftingRecipe");
        gap("state.role", Kind.WORLD, "<<state>> puts an NPC's role into a named state",
                List.of("Open a dialogue with <<state>> naming a state from the NPC's role JSON."),
                "The NPC's behaviour changes to that state. Needs a role that defines one, which no station NPC does.",
                "lowtalk:state", "NPCPlugin");
        gap("perm.check", Kind.SETUP, "perm() reads the player's permissions",
                List.of("Write a dialogue line guarded by perm(\"some.node\").",
                        "Open it with and without the permission granted."),
                "The line appears only with the permission. Needs two permission states, so no station can show it.",
                "lowtalk:perm");
        gap("player.npc.names", Kind.STATION, "{player} and {npc} interpolate the right names",
                List.of("Open any dialogue whose text uses {player} and {npc}."),
                "Both read the actual names. Used once in the whole corridor, so effectively untested.",
                "lowtalk:player", "lowtalk:npc");
        gap("editor.add.every.command", Kind.EDITOR, "Every command can be found in the editor's Add menu",
                List.of("Open the in-game editor on a passage.",
                        "Search the Add dropdown for a command by what it does, such as \"rain\".",
                        "Add it and check the row is the command named."),
                "The menu names every built-in command in plain words and the row matches. The editor is the largest "
                        + "surface in the mod and has no automated coverage at all.",
                "lowtalk:editor", "lowtalk:addmenu");
        gap("editor.rows.render", Kind.EDITOR, "Every row type in the editor draws and can be edited",
                List.of("Add one of each kind from the Add menu: line, option, if, once, random, set, input, wait, "
                        + "jump, end, and a command with arguments.",
                        "Edit each one, then save and reopen."),
                "Every row draws with its fields, edits stick, and nothing drops the client. Three separate client "
                        + "drops this month came from rows addressing controls that were not there.",
                "lowtalk:editor", "lowtalk:ui");
        gap("block.bind.door", Kind.WORLD, "A dialogue bound to a door opens instead of the door",
                List.of("Point the LowTalk tool at a door and bind a dialogue.", "Walk away and use the door."),
                "The dialogue opens. A door is two blocks and only its base is reported, so this catches a binding "
                        + "recorded against the wrong half.",
                "BlockReads", "UseBlockEvent", "lowtalk:blockbind");
        gap("block.bind.single", Kind.WORLD, "A dialogue bound to a single-block target still works",
                List.of("Bind a dialogue to something one block tall, such as a chest.", "Use it."),
                "The dialogue opens. Proves base-block resolution did not break the ordinary case.",
                "BlockReads", "UseBlockEvent", "lowtalk:blockbind");
        gap("block.bind.survives.restart", Kind.RESTART, "A block binding survives a restart",
                List.of("Bind a dialogue to a block.", "Stop and start the server.", "Use the block."),
                "The boot log reports the binding loaded and using the block still opens the dialogue.",
                "lowtalk:blockbind");
        gap("npc.rename.survives.restart", Kind.RESTART, "A renamed NPC keeps its name across a restart",
                List.of("Rename the NPC at station 12.", "Stop and start the server.", "Look at the NPC."),
                "The nameplate still shows the new name. Station 12 has always claimed this and nothing has checked it.",
                "lowtalk:npc_name");
        gap("objective.talk.task", Kind.SEQUENCE, "An objective completed by talking to another NPC",
                List.of("Start Objective_LowTalk_Talk at station 12.", "Walk to station 1 and talk to it."),
                "The tracker shows the task and it completes on arrival. LowTalk's own LowTalkNode task type, and the "
                        + "only check spanning two stations.",
                "ObjectivePlugin", "lowtalk:LowTalkNode");
        gap("trigger.volume", Kind.WORLD, "A trigger volume opens a dialogue",
                List.of("Place a trigger volume with the LowTalkDialogue effect.", "Walk into it."),
                "The dialogue opens on entry. Registered at boot on every server and exercised by nothing.",
                "TriggerVolumesPlugin", "lowtalk:trigger");
        gap("on.join", Kind.RESTART, "A dialogue with on: join opens when a player loads in",
                List.of("Give a dialogue the on: join directive.", "Disconnect and reconnect."),
                "It opens by itself once the world has loaded. Needs a rejoin, so no station can cover it.",
                "lowtalk:onjoin");
        gap("layout.chain", Kind.SETUP, "Layout and history resolve through all four levels",
                List.of("Set Layout in lowtalk.json, then override it in a pack's Settings.json, then on a dialogue.",
                        "Open dialogues at each level, then set ForceLayout and check it wins."),
                "The most specific setting wins each time and ForceLayout overrides everything. Four levels "
                        + "documented, none of them checked.",
                "lowtalk:layout", "lowtalk:config");
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
        String needle = surface.toLowerCase(Locale.ROOT);
        List<Check> out = new ArrayList<>();
        for (Check c : BY_ID.values()) {
            for (String s : c.covers()) {
                if (s.toLowerCase(Locale.ROOT).contains(needle)) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    /** Every surface any check claims to exercise. */
    public static java.util.Set<String> coveredSurfaces() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (Check c : BY_ID.values()) out.addAll(c.covers());
        return out;
    }
}
