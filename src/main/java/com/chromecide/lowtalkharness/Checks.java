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

    /** A check the harness's own NPC tree drives, tracked by the passage that runs it. */
    private static void tree(String id, String passage, String title, String expected, String... covers) {
        add(new Check(id, Kind.STATION, title, List.of("Talk to the harness tester and pick it."),
                expected, List.of(covers), null, "harness/" + passage));
    }

    /** A check still driven from LowTalk's own test corridor, because the tree cannot do it yet. */
    private static void corridor(String id, int number, String passage, String title, String expected, String... covers) {
        add(new Check(id, Kind.STATION, title, List.of("Walk to station " + number + " and work through its options."),
                expected, List.of(covers), number, passage));
    }

    /** Something real that nothing drives automatically. Listed so it is outstanding rather than forgotten. */
    private static void gap(String id, Kind kind, String title, List<String> steps, String expected, String... covers) {
        add(new Check(id, kind, title, steps, expected, List.of(covers), null, null));
    }

    static {
        // ---- the harness tester: one NPC, one passage per check ----------------------------------------------
        tree("notify.toast", "check_notify_toast", "A toast notification",
                "A small notification in the corner reading \"LowTalk test\" / \"Notification works\", which fades.",
                "NotificationUtil", "lowtalk:notify");
        tree("notify.warning", "check_notify_warning", "A toast in the warning style",
                "The same shape in the game's warning colour, plainly different from the plain one.",
                "NotificationUtil", "lowtalk:notify");

        tree("title.default", "check_title_default", "A title with no style named",
                "A small title for about 3 seconds, with the second line above the main one.",
                "EventTitleUtil", "lowtalk:title");
        tree("title.minor.alias", "check_title_minor_alias", "A title written the old way, as \"minor\"",
                "Exactly the same as title.default. If it differs, the alias older dialogues rely on has stopped "
                        + "meaning Default.",
                "EventTitleUtil", "lowtalk:title");
        tree("title.major", "check_title_major", "A title in the Major style",
                "A large cinematic title, plainly different from Default, for about 4 seconds.",
                "EventTitleUtil", "lowtalk:title");
        tree("title.goblinbreach", "check_title_goblinbreach", "A title in the GoblinBreach style (0.7+)",
                "A goblin banner with its own glyphs and emblem, nothing like Major. On a server without the style "
                        + "the command is refused by name and this check fails by itself, which is correct.",
                "EventTitleUtil", "EventTitleStyle", "lowtalk:title");
        tree("title.voideviction", "check_title_voideviction", "A title in the VoidEviction style (0.7+)",
                "A purple void banner, different again from Major and GoblinBreach.",
                "EventTitleUtil", "EventTitleStyle", "lowtalk:title");

        tree("items.give", "check_items_give", "<<give>> hands over items",
                "Two bread arrive with a narration line and appear in the inventory.",
                "lowtalk:give");
        tree("items.take", "check_items_take", "<<take>> removes them again",
                "One bread is handed back with a line; with none held it fails cleanly rather than going negative.",
                "lowtalk:take");
        tree("items.count", "check_items_count", "count() and has() read the inventory",
                "The numbers in the line match what is actually held, and change after giving or taking.",
                "lowtalk:count", "lowtalk:has");

        tree("body.heal", "check_body_heal", "<<heal>> restores health",
                "The health bar returns to full.", "lowtalk:heal");
        tree("body.stat", "check_body_stat", "<<stat>> sets a stat",
                "Health drops to 10 and the bar reflects it.", "lowtalk:stat", "lowtalk:max_stat");
        tree("body.effect", "check_body_effect", "<<effect>> applies an entity effect",
                "A regeneration buff icon appears and health climbs.", "lowtalk:effect");
        tree("body.cure", "check_body_cure", "<<cure>> removes it",
                "The buff icon disappears and health stops climbing.", "lowtalk:cure");

        tree("world.weather", "check_world_weather", "<<weather>> changes the sky for one player",
                "The sky turns overcast for you and nobody else.", "lowtalk:weather", "Weather");
        tree("world.weather.clear", "check_world_weather_clear", "<<weather clear>> gives the sky back",
                "The natural sky returns.", "lowtalk:weather", "Weather");
        tree("world.time", "check_world_time", "<<time>> moves the clock",
                "The sun jumps to noon and hour() reads 12 on the next line.",
                "lowtalk:time", "lowtalk:hour", "WorldTimeResource");
        tree("world.translate", "check_world_translate", "t() returns a translated string",
                "Words, not the key itself. A key coming back unchanged means the language file did not load.",
                "lowtalk:t");

        tree("media.music", "check_media_music", "<<music>> forces a playlist",
                "The music changes within a few seconds.", "lowtalk:music", "MusicContainer");
        tree("media.music.clear", "check_media_music_clear", "<<music clear>> returns to the area's music",
                "The forced playlist stops and the area's own music comes back.", "lowtalk:music");
        tree("media.vfx", "check_media_vfx", "<<vfx>> plays particles at the NPC",
                "A burst at the NPC, at the scale and duration asked for.", "lowtalk:vfx", "ParticleSystem");
        tree("media.camera", "check_media_camera", "<<camera>> shakes the view",
                "A short shake at the intensity given.", "lowtalk:camera", "CameraShake");

        tree("npc.anim", "check_npc_anim", "<<anim>> plays an animation on the NPC",
                "The NPC waves. If nothing happens the animation id may not exist for this role.",
                "lowtalk:anim");
        tree("npc.sound", "check_npc_sound", "<<sound>> plays a sound at the NPC",
                "The pickup sound, positioned at the NPC rather than at the player.",
                "lowtalk:sound", "SoundEvent");
        tree("npc.rename", "check_npc_rename", "<<npc_name>> renames the NPC",
                "The nameplate above it changes immediately.", "lowtalk:npc_name");
        tree("npc.rename.restore", "check_npc_rename_restore", "<<npc_name>> puts the name back",
                "The nameplate returns to \"LowTalk harness\". Renaming persists, so this matters.",
                "lowtalk:npc_name");
        tree("npc.attitude", "check_npc_attitude", "<<attitude>> changes how the NPC regards the player",
                "attitude() reads the current value, the NPC turns hostile, then calms when set friendly again.",
                "lowtalk:attitude", "AttitudeGroup");

        // ---- still LowTalk's corridor: the tree cannot do these with one NPC ---------------------------------
        corridor("station.basics", 1, "test_basics/start", "Choices, hubs, Continue, Leave and guarded options",
                "Two lines give a Continue between them; a hidden option appears only once revealed; a disabled "
                        + "option is greyed and inert; Leave closes after one line.",
                "lowtalk:options", "lowtalk:jump", "lowtalk:end", "lowtalk:show");
        corridor("station.memory", 2, "test_memory/menu", "Memory: per-NPC, per-player, once-blocks and visited()",
                "Station 2 is two NPCs on purpose: $met is per NPC, the visit counter is per player. Needs both, so "
                        + "one tester cannot show it. visited() flips after the passage is seen and a once-block runs once.",
                "lowtalk:set", "lowtalk:once", "lowtalk:visited");
        corridor("station.input", 3, "test_input/start", "Typed input, interpolation and plural()",
                "The box takes what is typed, Enter submits, the answer is interpolated back, and plural() agrees "
                        + "with the number.",
                "lowtalk:input", "lowtalk:plural");
        corridor("station.progress", 7, "test_progress/start", "Objectives and reputation",
                "An objective starts and reads as active; reputation moves by ten and the rank changes at the "
                        + "boundary. See the objective warning: the tracker is unreliable on this version.",
                "ObjectivePlugin", "ReputationPlugin", "lowtalk:objective", "lowtalk:reputation", "lowtalk:rank");
        corridor("station.travel", 8, "test_travel/start", "The shop hand-off and teleport",
                "The barter shop opens and the conversation resumes on Back. Needs a merchant role, which the "
                        + "tester does not have.",
                "lowtalk:shop", "lowtalk:teleport");
        corridor("station.random", 9, "test_random/start", "random(), chance() and ordinal()",
                "Rolling again changes the numbers and the ordinal reads correctly.",
                "lowtalk:random", "lowtalk:chance", "lowtalk:ordinal");
        corridor("station.format", 10, "test_format/start", "Variations, random blocks, once-options and include",
                "[a|b] varies between openings, a random block picks one alternative, a once-option vanishes after "
                        + "use, the pause has no Continue, and the included file's line appears.",
                "lowtalk:wait", "lowtalk:include", "lowtalk:variation");
        corridor("station.npc.spawn", 12, "test_npc/start", "Spawning and despawning an NPC",
                "A new NPC appears beside you and despawning closes the window and removes it. Despawning the "
                        + "harness tester would end the session it is being run from.",
                "lowtalk:spawn", "lowtalk:despawn");
        corridor("station.talker", 14, "test_talker/start", "A dialogue opened by the NPC's own role",
                "The NPC opens it through its role's LowTalkOpenDialogue action, with LowTalk's use hook not "
                        + "involved. Needs a role wired that way, and Adventure mode.",
                "NPCPlugin", "lowtalk:roleaction");

        // ---- nothing drives these. See docs/gaps.md ---------------------------------------------------------
        gap("run.command.gated", Kind.SETUP, "<<run>> only works when the server allows it",
                List.of("Set AllowRunCommand false in lowtalk.json and /lowtalk reload.",
                        "Trigger a dialogue using <<run>>.", "Set it true, reload, and trigger it again."),
                "Refused while off, runs while on. The only command behind a security flag.",
                "lowtalk:run", "lowtalk:config");
        gap("learn.recipe", Kind.WORLD, "<<learn>> teaches a recipe, and knows() sees it",
                List.of("Open a dialogue with <<learn>> for a recipe the player lacks.",
                        "Check the crafting menu and a line guarded by knows()."),
                "The recipe becomes available and knows() returns true. Depends on what the player already knows, "
                        + "so it cannot live in a shared tree.",
                "lowtalk:learn", "lowtalk:knows", "CraftingRecipe");
        gap("state.role", Kind.WORLD, "<<state>> puts an NPC's role into a named state",
                List.of("Open a dialogue with <<state>> naming a state from the NPC's role JSON."),
                "The NPC's behaviour changes. Needs a role defining named states; the tester's does not.",
                "lowtalk:state", "NPCPlugin");
        gap("perm.check", Kind.SETUP, "perm() reads the player's permissions",
                List.of("Guard a line with perm(\"some.node\").", "Open it with and without the permission."),
                "The line appears only with the permission. Needs the same player in two permission states.",
                "lowtalk:perm");
        gap("player.npc.names", Kind.STATION, "{player} and {npc} interpolate the right names",
                List.of("Open any dialogue whose text uses {player} and {npc}."),
                "Both read the actual names rather than the literal braces.",
                "lowtalk:player", "lowtalk:npc");
        gap("editor.add.every.command", Kind.EDITOR, "Every command can be found in the editor's Add menu",
                List.of("Open the in-game editor on a passage.",
                        "Search the Add dropdown by what a command does, such as \"rain\".",
                        "Add it and check the row is the command named."),
                "The menu names every built-in command in plain words and the row matches.",
                "lowtalk:editor", "lowtalk:addmenu");
        gap("editor.rows.render", Kind.EDITOR, "Every row type in the editor draws and can be edited",
                List.of("Add one of each kind: line, option, if, once, random, set, input, wait, jump, end, and a "
                        + "command with arguments.", "Edit each, then save and reopen."),
                "Every row draws with its fields, edits stick, and nothing drops the client. Three client drops "
                        + "this month came from rows addressing controls that were not there.",
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
                "The boot log reports the binding loaded and the block still opens the dialogue.",
                "lowtalk:blockbind");
        gap("npc.rename.survives.restart", Kind.RESTART, "A renamed NPC keeps its name across a restart",
                List.of("Rename the harness tester.", "Stop and start the server.", "Look at it."),
                "The nameplate still shows the new name. Claimed since the feature shipped, never checked.",
                "lowtalk:npc_name");
        gap("objective.talk.task", Kind.SEQUENCE, "An objective completed by talking to another NPC",
                List.of("Start Objective_LowTalk_Talk at station 12.", "Walk to station 1 and talk to it."),
                "The tracker shows the task and it completes on arrival. LowTalk's own LowTalkNode task type.",
                "ObjectivePlugin", "lowtalk:LowTalkNode", "lowtalk:objective_line");
        gap("trigger.volume", Kind.WORLD, "A trigger volume opens a dialogue",
                List.of("Place a trigger volume with the LowTalkDialogue effect.", "Walk into it."),
                "The dialogue opens on entry. Three trigger types are registered on every boot and nothing "
                        + "exercises any of them.",
                "TriggerVolumesPlugin", "lowtalk:trigger");
        gap("on.join", Kind.RESTART, "A dialogue with on: join opens when a player loads in",
                List.of("Give a dialogue the on: join directive.", "Disconnect and reconnect."),
                "It opens by itself once the world has loaded.",
                "lowtalk:onjoin");
        gap("layout.chain", Kind.SETUP, "Layout and history resolve through all four levels",
                List.of("Set Layout in lowtalk.json, override it in a pack's Settings.json, then on a dialogue.",
                        "Open one at each level, then set ForceLayout and check it wins."),
                "The most specific wins each time and ForceLayout overrides everything. Four documented levels, "
                        + "none ever checked.",
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
