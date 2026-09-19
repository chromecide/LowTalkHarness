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
     * @param autoFail the passage that means "this was exercised and it was wrong", for checks the dialogue can
     *                 decide for itself. Where a condition can be written down, writing it down beats asking a
     *                 person to notice: {@code perm()} returning true for a permission nobody holds is not
     *                 something anyone would spot by looking at a line of text.
     */
    public record Check(@Nonnull String id, @Nonnull Kind kind, @Nonnull String title, @Nonnull List<String> steps,
                        @Nonnull String expected, @Nonnull List<String> covers, @Nullable Integer station,
                        @Nullable String autoSeen, @Nullable String autoFail) {

        public Check(@Nonnull String id, @Nonnull Kind kind, @Nonnull String title, @Nonnull List<String> steps,
                     @Nonnull String expected, @Nonnull List<String> covers, @Nullable Integer station,
                     @Nullable String autoSeen) {
            this(id, kind, title, steps, expected, covers, station, autoSeen, null);
        }
    }

    private static final Map<String, Check> BY_ID = new LinkedHashMap<>();

    private static void add(Check c) {
        if (BY_ID.put(c.id(), c) != null) throw new IllegalStateException("two checks share the id " + c.id());
    }

    /** A check the harness's own NPC tree drives, tracked by the passage that runs it. */
    private static void tree(String id, String passage, String title, String expected, String... covers) {
        add(new Check(id, Kind.STATION, title, List.of("Talk to the harness tester and pick it."),
                expected, List.of(covers), null, "harness/" + passage));
    }

    /**
     * A tree check the dialogue decides for itself: one passage means it worked, another means it did not.
     *
     * <p>Strictly better than a tester's eyes where the claim can be written as a condition, because it cannot
     * be misread, misremembered or clicked past. The tester still sees what happened; they just are not the
     * instrument.
     */
    private static void judged(String id, String okPassage, String badPassage, String title, String expected,
                               String... covers) {
        add(new Check(id, Kind.STATION, title, List.of("Talk to the harness tester and pick it."),
                expected, List.of(covers), null, "harness/" + okPassage, "harness/" + badPassage));
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
                "Rain falls, for you and nobody else, and the line reports the weather the server now thinks "
                        + "you have. Rain, not cloud: this check asked for Zone1_Cloudy_Medium, which is "
                        + "tagged Cloudy and contains no rain, while the option offered to make it rain. The "
                        + "tester reported rain was not working and was right about what they saw.",
                "lowtalk:weather", "Weather");
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
        judged("npc.attitude", "check_npc_attitude_ok", "check_npc_attitude_bad",
                "<<attitude>> changes how the NPC regards the player",
                "attitude() reads hostile while the NPC is hostile and friendly once it is set back, in one "
                        + "pass with nothing for the tester to interrupt. It does not show the NPC behaving "
                        + "differently, and on this NPC it cannot: see attitude.behaviour.",
                "lowtalk:attitude", "AttitudeGroup");
        add(new Check("attitude.behaviour", Kind.STATION, "A hostile NPC actually behaves like one",
                List.of("Run /harness fighter to put a goblin in front of you.",
                        "Talk to it and let it turn on you."),
                "It comes at you once hostile -- and only once the dialogue closes, because it does nothing "
                        + "while the window is open. Making it friendly again does not stop it on its own -- "
                        + "an attitude decides who an NPC starts on, not a fight in progress -- so the calm "
                        + "option does that and then <<calm>>, which clears the target. "
                        + "The tester itself "
                        + "cannot answer this: its role is built on Template_Temple, which has attitude "
                        + "sensors for Neutral and Friendly and no hostile branch, no attack and no combat, "
                        + "so setting it hostile changes a value nothing in the role reads.",
                List.of("lowtalk:attitude", "lowtalk:calm", "AttitudeGroup", "WorldSupport",
                        "MarkedEntitySupport"), null,
                "harness_fighter/check_attitude_behaviour_ok", "harness_fighter/check_attitude_behaviour_bad"));

        tree("basics.continue", "check_basics_continue", "Two lines in a row give a Continue",
                "A Continue button between the lines rather than the option list, and the options back after.",
                "lowtalk:options");
        tree("basics.hidden", "check_basics_hidden", "An option hidden until a condition holds",
                "Absent from the list entirely until the reveal, then present.", "lowtalk:options");
        tree("basics.disabled", "check_basics_disabled", "An option shown but greyed out",
                "Visible and unclickable before the reveal, clickable after. Different from hidden: the player "
                        + "can see there is something there.",
                "lowtalk:show", "lowtalk:options");
        tree("basics.reveal", "check_basics_reveal", "<<set>> changes what the next menu offers",
                "Setting a variable changes the option list when the menu is next shown.", "lowtalk:set");

        tree("input.word", "check_input_word", "<<input>> takes typed text",
                "The box accepts typing, Enter and OK behave the same, and the text comes back echoed exactly, "
                        + "capitals and spaces included. An empty submit takes the empty branch.",
                "lowtalk:input");
        tree("input.plural", "check_input_plural", "plural() agrees with a typed number",
                "1 reads \"apple\" and anything else \"apples\", using the number the player typed.",
                "lowtalk:plural", "lowtalk:input");

        tree("random.chance", "check_random_chance", "chance() splits roughly evenly",
                "HEADS and TAILS both come up over several tries, rather than one answer every time.",
                "lowtalk:chance");
        tree("random.number", "check_random_number", "random() gives a number in range",
                "A whole number from 0 to 5, changing most tries.", "lowtalk:random");
        tree("random.ordinal", "check_random_ordinal", "ordinal() words a count, per NPC",
                "first, second, third and so on, counted on the NPC rather than the player, so a second tester "
                        + "has its own count.",
                "lowtalk:ordinal", "lowtalk:set");

        tree("format.variation", "check_format_random", "A random block picks one alternative",
                "One of three lines, a different one most times. The [a|b] greeting on the menu above should "
                        + "vary between visits too.",
                "lowtalk:variation");
        tree("format.once", "check_format_once", "A once-option removes itself",
                "Gone from the list for good after being picked, per player and NPC.", "lowtalk:once");
        tree("format.wait", "check_format_wait", "<<wait>> pauses without a button",
                "About two seconds between the lines, with no Continue to press.", "lowtalk:wait");
        tree("format.include", "check_format_include", "include: pulls in another file's passages",
                "A line from _harness_shared.talk. If include failed, the jump would report a problem instead.",
                "lowtalk:include");

        tree("progress.reputation.up", "check_progress_reputation_up", "<<reputation>> raises standing",
                "The number rises by ten and the rank changes at the boundary. Needs the NPC to be in a "
                        + "reputation group, which the tester's role is.",
                "ReputationPlugin", "lowtalk:reputation", "lowtalk:rank");
        tree("progress.reputation.down", "check_progress_reputation_down", "<<reputation>> lowers it again",
                "The number falls by ten.", "ReputationPlugin", "lowtalk:reputation");
        tree("progress.objective", "check_progress_objective", "<<objective>> starts one, and objective() reads it",
                "A tracker entry appears and the state reads \"active\". The tracker is unreliable on this "
                        + "version, so a client drop here is the game's bug rather than this check's.",
                "ObjectivePlugin", "lowtalk:objective");

        tree("memory.npc", "check_memory_npc", "A variable set on the NPC stays on that NPC",
                "A second tester still reads as not having met you. Spawn one with /harness npc to compare.",
                "lowtalk:set");
        tree("memory.player", "check_memory_player", "A $player variable is shared across NPCs",
                "Every tester reads the same count, unlike the per-NPC one above.", "lowtalk:set");
        tree("memory.visited", "check_memory_visited", "visited() knows which passages have been seen",
                "False before the marker passage is reached, true afterwards.", "lowtalk:visited");
        tree("memory.marker", "check_memory_marker", "A passage that exists to be visited",
                "Nothing visible happens; it gives visited() something to answer about.", "lowtalk:visited");
        tree("memory.reset", "check_memory_reset", "A variable can be cleared again",
                "The per-NPC line goes back to saying you have not met.", "lowtalk:set");

        // ---- still LowTalk's corridor: the tree cannot do these with one NPC ---------------------------------
        corridor("station.travel", 8, "test_travel/start", "The shop hand-off and teleport",
                "The barter shop opens and the conversation resumes on Back. Needs a merchant role, which the "
                        + "tester does not have.",
                "lowtalk:shop", "lowtalk:teleport");
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
        judged("learn.recipe", "check_learn_recipe_ok", "check_learn_recipe_bad",
                "<<learn>> teaches a recipe and knows() sees it",
                "knows() is false for the recipe, true after <<learn>>, and the crafting menu offers it. The "
                        + "dialogue forgets the recipe first, so the result does not depend on what this player "
                        + "already knew.",
                "lowtalk:learn", "lowtalk:knows", "CraftingRecipe");
        gap("state.role", Kind.WORLD, "<<state>> puts an NPC's role into a named state",
                List.of("Spawn an NPC whose role defines two real states and bind a dialogue to it.",
                        "Move it between them with <<state>>."),
                "It ends up in the state it was put into. The harness tester cannot do this: asked for its "
                        + "own list, its role has one usable state. It reports Idle and \"start\" -- the "
                        + "engine's own placeholder -- and entering start throws inside the game, because no "
                        + "sub-states are defined for it. The game's Test_State_* roles define several and "
                        + "would close this.",
                "lowtalk:state", "NPCPlugin");
        judged("perm.check", "check_perm_ok", "check_perm_bad", "perm() reads the player's permissions",
                "perm() is false while the node is denied to this player and true once it is granted, with "
                        + "the player put back as they were afterwards. This was a gap twice over: it needs "
                        + "one player in two permission states, and an Admin holds the wildcard so nothing "
                        + "comes back false. A deny entry beats the wildcard, and the tester writes one.",
                "lowtalk:perm");
        tree("player.npc.names", "check_player_npc_names", "{player} and {npc} interpolate the right names",
                "The line reads your own name and the tester's, not the literal braces and not each other's.",
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

    /**
     * Whether anything would notice this check being run.
     *
     * <p>A watched check is marked as run by watching LowTalk's events, so its never having been marked means
     * it was never run, and a pass recorded over that would be a claim about nothing. An unwatched one — the
     * editor, block binding, anything done out in the world — can never be marked whatever happens, so the
     * same rule applied to it would make the hand-walked half of the list able to fail and never able to pass.
     */
    public static boolean isWatched(@Nonnull String id) {
        Check c = BY_ID.get(id);
        return c != null && c.autoSeen() != null;
    }

    /** The checks a LowTalk passage means have been exercised. */
    public static List<Check> forPassage(@Nonnull String dialogueId, @Nonnull String passage) {
        String key = dialogueId + "/" + passage;
        List<Check> out = new ArrayList<>();
        for (Check c : BY_ID.values()) if (key.equals(c.autoSeen())) out.add(c);
        return out;
    }

    /** The checks a LowTalk passage means have been exercised and found wanting. */
    public static List<Check> failedByPassage(@Nonnull String dialogueId, @Nonnull String passage) {
        String key = dialogueId + "/" + passage;
        List<Check> out = new ArrayList<>();
        for (Check c : BY_ID.values()) if (key.equals(c.autoFail())) out.add(c);
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
