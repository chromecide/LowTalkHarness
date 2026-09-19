package com.chromecide.lowtalkharness;

import com.chromecide.lowtalk.api.DialogueContext;
import com.chromecide.lowtalk.api.DialogueListener;
import com.chromecide.lowtalk.api.LowTalkApi;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * A test harness for LowTalk, as a separate mod.
 *
 * <p>Separate on purpose, for three reasons. It keeps every line of this out of LowTalk's released jar without
 * build flags or source sets. It means the LowTalk being tested is the jar that ships, rather than a variant
 * built with the harness compiled in. And it makes the harness a real consumer of LowTalk's public API, which
 * until now had exactly one consumer using one of its four events — so the API gets exercised as the deliverable
 * it is, and where the harness cannot see something, that is an API gap worth fixing rather than a reason to
 * reach inside.
 *
 * <p>What it does: watches dialogues through {@link DialogueListener} and records which checks were exercised on
 * which server version, then lets a person say whether each one looked right. Never for release.
 */
public class HarnessPlugin extends JavaPlugin implements DialogueListener {

    private static HarnessPlugin instance;
    private RunRecord record;
    /** The panel each player has asked for, by player id. Off until asked: it is a tester's tool, not furniture. */
    private final Map<UUID, HarnessHud> huds = new ConcurrentHashMap<>();
    /** Where each player is, so a command that fails — or a complaint typed in chat — lands on the right check. */
    private final Map<UUID, Where> whereabouts = new ConcurrentHashMap<>();

    /**
     * The last passage a player reached, kept after the conversation ends.
     *
     * <p>Not cleared on {@code onEnd}, because the complaint usually comes a moment after the page closes:
     * you watch the title not appear, back out, and type. Losing the location at that exact moment would throw
     * away the only context worth having.
     */
    public record Where(@Nonnull String dialogueId, @Nonnull String node) {}

    public HarnessPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    public static HarnessPlugin get() {
        return instance;
    }

    public RunRecord record() {
        return record;
    }

    /** The last passage this player was in, or null if they have not talked to anything yet. */
    @Nullable
    public Where whereIs(@Nonnull UUID playerId) {
        return whereabouts.get(playerId);
    }

    @Override
    protected void setup() {
        String version = serverVersion();
        String build = Build.describe();
        record = new RunRecord(dataFolder(), version, build);

        LowTalkApi api = LowTalkApi.get();
        api.addListener(this);
        registerFunctions(api);
        getCommandRegistry().registerCommand(new HarnessCommand(this));

        int[] t = record.tally(Checks.ids());
        getLogger().at(Level.INFO).log(
                "LowTalk harness watching server %s, LowTalk %s: %d checks, %d run, %d answered "
                        + "(%d pass, %d fail), %d still to run, %d pieces of feedback. Record: %s",
                version, build, Checks.ids().size(), t[0], t[1], t[2], t[3], Checks.ids().size() - t[1],
                record.feedback().size(), record.file());
        skipWhatThisServerCannotDo();

        List<String> carried = record.fromAnotherBuild(Checks.ids());
        if (!carried.isEmpty()) {
            getLogger().at(Level.INFO).log(
                    "%d result(s) were observed on an earlier build of LowTalk and do not count towards a release: %s",
                    carried.size(), String.join(", ", carried));
        }
        // A check that was removed from the list leaves its result behind in older records. Say so once, rather
        // than letting a stale id sit in the file looking like something that still means anything.
        List<String> retired = record.retiredIds();
        if (!retired.isEmpty()) {
            getLogger().at(Level.INFO).log("%d recorded checks no longer exist and are kept under 'retired': %s",
                    retired.size(), String.join(", ", retired));
        }
    }

    @Override
    protected void shutdown() {
        if (record != null) record.flush();
    }

    /**
     * Record, as a skip, anything this server has no way of doing.
     *
     * <p>Not a judgement about behaviour — a fact about the server, read off its own enum and printed in the
     * boot log next to it. A check that cannot run here should say "does not apply" rather than sitting as
     * never run forever, and certainly rather than failing: a tester who clicks GoblinBreach on 0.6.7 gets a
     * command that throws, which is correct behaviour recorded as a fault.
     */
    private void skipWhatThisServerCannotDo() {
        record("title.goblinbreach", "GoblinBreach");
        record("title.voideviction", "VoidEviction");
    }

    private void record(String checkId, String style) {
        if (Checks.byId(checkId) == null) return;
        if (com.chromecide.lowtalk.hytale.compat.EventTitles.knows(style)) return;
        RunRecord.Check state = record.check(checkId);
        if (state.verdict == RunRecord.Verdict.SKIP) return;
        record.decide(checkId, RunRecord.Verdict.SKIP,
                "this server has no " + style + " title style (it offers "
                        + String.join(", ", com.chromecide.lowtalk.hytale.compat.EventTitles.styleNames()) + ")");
        record.flush();
        getLogger().at(Level.INFO).log("[harness] %s skipped: no %s style on this server", checkId, style);
    }

    /**
     * Two functions the tester's own dialogue uses to hide what has already been done.
     *
     * <p>The record is the harness's, not LowTalk's, so the dialogue cannot see it without being told. Rather
     * than keeping a second copy of "what has been run" in dialogue variables — which would drift, and would be
     * per player rather than per server version — the record is exposed as functions and the options guard on
     * them. What the tree shows is then exactly what the record says is left.
     *
     * <p>Registering them is also the API's third extension point getting a consumer: commands and listeners
     * had one, functions had none.
     */
    private void registerFunctions(LowTalkApi api) {
        api.registerFunction("checked", "checked(\"title.major\")",
                "True when that harness check has been run on this server version.",
                (ctx, args) -> args.isEmpty() ? Boolean.FALSE
                        : Boolean.valueOf(record.check(String.valueOf(args.get(0))).seen));
        // What this server can actually do. The tree guards the two pre-release title styles on it, so a
        // release-line walk is never asked to judge an effect the server has no name for -- which is what
        // produced the one FAIL sitting in the 0.6.7 record: a tester clicked GoblinBreach on a server whose
        // enum has two entries, and the command threw, exactly as it should have.
        api.registerFunction("has_title_style", "has_title_style(\"GoblinBreach\")",
                "True when this server's own title-style enum has that style.",
                (ctx, args) -> args.isEmpty() ? Boolean.FALSE
                        : Boolean.valueOf(com.chromecide.lowtalk.hytale.compat.EventTitles.knows(String.valueOf(args.get(0)))));
        // Test-only, and deliberately not in LowTalk: <<learn>> only adds a string to the player's known set,
        // so "learn it and see that knows() is true" is a tautology unless the recipe is genuinely unknown
        // first. Forgetting it is the setup step, and a released dialogue mod has no business shipping one.
        api.registerCommand("forget_recipe", "<<forget_recipe Alchemy_Cauldron>>",
                "Harness only: make the player not know a recipe, so learning it can be observed.",
                (ctx, args) -> {
                    if (args.isEmpty()) return "forget_recipe needs a recipe id";
                    // DialogueContext hands out the NPC's entity but not the player's, so this goes through
                    // PlayerRef. Worth noting as an API gap rather than working around silently: every effect
                    // LowTalk ships that touches the player reaches for the same thing internally.
                    Ref<EntityStore> ref = ctx.getPlayer().getReference();
                    if (ref == null || !ref.isValid()) return "the player is not in a world";
                    com.hypixel.hytale.builtin.crafting.CraftingPlugin.forgetRecipe(ref, args.get(0), ref.getStore());
                    return null;
                });
        // Reading an NPC's state back. LowTalk can put an NPC into a state and offers no way to ask which one
        // it is in, so <<state>> was untestable by anything but watching behaviour change -- and the tester's
        // role was believed to have no states at all, which turned out to be wrong: Template_Temple, which it
        // inherits from, declares Idle and Stopped.
        // What states this NPC's role actually has, asked of the role rather than read out of its JSON by
        // eye. The first version of the state check asserted against "Stopped", a word that appears in the
        // template file and is not one of its states, and the check failed for that reason alone.
        // Permissions, moved on the player mid-conversation. perm() was a gap for the stated reason that it
        // needs "the same player with and without a permission", which no dialogue could arrange -- and an
        // Admin holds the wildcard, so nothing they are asked about comes back false. Both halves turn out to
        // be arrangeable: a node may be written as a deny with a leading minus, user permissions are consulted
        // before group ones, and a deny is matched before the wildcard. So the tester can take a permission
        // away from an admin, ask, give it back, ask again, and put everything back as it found it.
        //
        // Scoped to lowtalkharness.* and nothing else. This is a privilege-changing command in a test mod;
        // it has no business being able to name a permission that means something.
        api.registerCommand("perm_deny", "<<perm_deny lowtalkharness.perm.probe>>",
                "Harness only: deny the player a lowtalkharness.* node, overriding any group grant.",
                (ctx, args) -> permWrite(ctx, args, true));
        api.registerCommand("perm_grant", "<<perm_grant lowtalkharness.perm.probe>>",
                "Harness only: grant the player a lowtalkharness.* node.",
                (ctx, args) -> permWrite(ctx, args, false));
        api.registerCommand("perm_clear", "<<perm_clear lowtalkharness.perm.probe>>",
                "Harness only: remove both the grant and the deny, leaving the player as they were.",
                (ctx, args) -> permClear(ctx, args));
        api.registerFunction("npc_states", "npc_states()",
                "Harness only: the states this dialogue's NPC role defines, comma separated.",
                (ctx, args) -> String.join(", ", stateNames(ctx)));
        api.registerFunction("npc_other_state", "npc_other_state()",
                "Harness only: a state of this NPC's role that it is not currently in, or \"\" if there is none.",
                (ctx, args) -> {
                    Ref<EntityStore> npc = ctx.getNpcRef();
                    var store = ctx.getEntityStore();
                    if (npc == null || store == null || !npc.isValid()) return "";
                    var support = com.hypixel.hytale.server.npc.role.support.StateSupport.get(npc, store);
                    int now = support.getStateIndex();
                    for (int index : support.getStateHelper().getAllMainStates()) {
                        if (index != now) return support.getStateHelper().getStateName(index);
                    }
                    return "";
                });
        api.registerFunction("npc_in_state", "npc_in_state(\"Stopped\")",
                "Harness only: true when this dialogue's NPC is in that state of its role.",
                (ctx, args) -> {
                    if (args.isEmpty()) return Boolean.FALSE;
                    Ref<EntityStore> npc = ctx.getNpcRef();
                    var store = ctx.getEntityStore();
                    if (npc == null || store == null || !npc.isValid()) return Boolean.FALSE;
                    var support = com.hypixel.hytale.server.npc.role.support.StateSupport.get(npc, store);
                    String wanted = String.valueOf(args.get(0));
                    int index = support.getStateHelper().getStateIndex(wanted);
                    boolean in = index >= 0 && support.inState(index);
                    getLogger().at(Level.INFO).log("[harness] npc_in_state(%s): index=%d, now in '%s' (index %d) -> %s",
                            wanted, index, support.getStateName(), support.getStateIndex(), in);
                    return in;
                });
        // The same questions perm() and knows() answer, asked without going through LowTalk, and logged. When
        // a self-judging check fails, this says whether the feature disagreed with the game or the assertion
        // disagreed with reality.
        api.registerFunction("probe_perm", "probe_perm(\"lowtalk.creator\")",
                "Harness only: hasPermission, read directly and logged.",
                (ctx, args) -> {
                    boolean held = !args.isEmpty() && ctx.getPlayer().hasPermission(String.valueOf(args.get(0)));
                    getLogger().at(Level.INFO).log("[harness] probe_perm(%s) = %s", args, held);
                    return held;
                });
        api.registerFunction("probe_knows", "probe_knows(\"X_Recipe_Generated_0\")",
                "Harness only: the player's known-recipe set, read directly and logged.",
                (ctx, args) -> {
                    Ref<EntityStore> ref = ctx.getPlayer().getReference();
                    var player = ref == null || !ref.isValid() ? null
                            : ref.getStore().getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
                    var known = player == null ? null : player.getPlayerConfigData().getKnownRecipes();
                    boolean has = known != null && !args.isEmpty() && known.contains(String.valueOf(args.get(0)));
                    getLogger().at(Level.INFO).log("[harness] probe_knows(%s) = %s (%d known recipes)",
                            args, has, known == null ? -1 : known.size());
                    return has;
                });
        api.registerFunction("remaining", "remaining(\"title\")",
                "How many harness checks whose id starts with that prefix have not been run yet.",
                (ctx, args) -> {
                    String prefix = args.isEmpty() ? "" : String.valueOf(args.get(0));
                    int left = 0;
                    for (Checks.Check c : Checks.all()) {
                        if (c.autoSeen() == null) continue;      // nothing watches it, so it can never tick off
                        if (!c.id().startsWith(prefix)) continue;
                        if (!record.check(c.id()).seen) left++;
                    }
                    return (double) left;
                });
    }

    /** The one namespace these commands may touch. Anything else is a bug or a mistake, and refused. */
    private static final String PERM_PREFIX = "lowtalkharness.";

    @Nullable
    private static String checkedNode(@Nonnull java.util.List<String> args) {
        if (args.isEmpty()) return null;
        String node = args.get(0).trim();
        return node.startsWith(PERM_PREFIX) ? node : null;
    }

    /** Write a grant or a deny for the player, clearing the opposite so the two cannot both be present. */
    @Nullable
    private String permWrite(@Nonnull DialogueContext ctx, @Nonnull java.util.List<String> args, boolean deny) {
        String node = checkedNode(args);
        if (node == null) return "this command only takes a " + PERM_PREFIX + "* permission";
        UUID id = ctx.getPlayer().getUuid();
        var perms = com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
        perms.removeUserPermission(id, java.util.Set.of(node, "-" + node));
        perms.addUserPermission(id, java.util.Set.of(deny ? "-" + node : node));
        getLogger().at(Level.INFO).log("[harness] %s %s for %s", deny ? "denied" : "granted", node, id);
        return null;
    }

    /** Put the player back exactly as they were: neither granted nor denied at the user level. */
    @Nullable
    private String permClear(@Nonnull DialogueContext ctx, @Nonnull java.util.List<String> args) {
        String node = checkedNode(args);
        if (node == null) return "this command only takes a " + PERM_PREFIX + "* permission";
        UUID id = ctx.getPlayer().getUuid();
        com.hypixel.hytale.server.core.permissions.PermissionsModule.get()
                .removeUserPermission(id, java.util.Set.of(node, "-" + node));
        getLogger().at(Level.INFO).log("[harness] cleared %s for %s", node, id);
        return null;
    }

    /** Every state name this dialogue's NPC role defines, in the order the role lists them. */
    private static List<String> stateNames(@Nonnull DialogueContext ctx) {
        Ref<EntityStore> npc = ctx.getNpcRef();
        var store = ctx.getEntityStore();
        if (npc == null || store == null || !npc.isValid()) return List.of();
        var support = com.hypixel.hytale.server.npc.role.support.StateSupport.get(npc, store);
        List<String> out = new java.util.ArrayList<>();
        for (int index : support.getStateHelper().getAllMainStates()) {
            out.add(support.getStateHelper().getStateName(index));
        }
        return out;
    }

    // ---- the panel

    /** Turn the panel on or off for one player. Returns whether it is now on. */
    public boolean toggleHud(@Nonnull PlayerRef playerRef, @Nonnull Ref<EntityStore> entity) {
        Player player = entity.getStore().getComponent(entity, Player.getComponentType());
        if (player == null) return false;
        UUID id = playerRef.getUuid();
        HarnessHud existing = huds.remove(id);
        if (existing != null) {
            player.getHudManager().removeCustomHud(playerRef, HarnessHud.KEY);
            return false;
        }
        HarnessHud hud = new HarnessHud(this, playerRef);
        huds.put(id, hud);
        player.getHudManager().addCustomHud(playerRef, hud);
        return true;
    }

    /** Redraw every open panel, after the record changed. */
    public void refreshHuds() {
        for (HarnessHud hud : huds.values()) {
            try {
                hud.refresh();
            } catch (RuntimeException e) {
                getLogger().at(Level.WARNING).withCause(e).log("could not redraw the harness panel");
            }
        }
    }

    private void pointHudAt(@Nonnull UUID playerId, @Nullable String dialogueId) {
        HarnessHud hud = huds.get(playerId);
        if (hud != null) hud.setDialogue(dialogueId);
    }

    /**
     * The version of the server we are running on, which is the only thing a record is worth filing under.
     * Read from the server jar's own manifest, the way everything else in this project establishes a version.
     */
    private String serverVersion() {
        try {
            Package p = Class.forName("com.hypixel.hytale.server.core.universe.Universe").getPackage();
            String v = p == null ? null : p.getImplementationVersion();
            if (v != null && !v.isBlank()) return v;
        } catch (ClassNotFoundException | RuntimeException ignored) {
            // fall through to the unknown case rather than guessing at a version
        }
        return "unknown";
    }

    private Path dataFolder() {
        return getDataDirectory();
    }

    // ---- watching LowTalk

    /**
     * A passage was reached. This is the whole of automatic tracking: the corridor is walked, LowTalk says where
     * the player went, and the checks bound to those passages are marked as exercised. No station was edited to
     * make this work, and none can drift out of step with it.
     */
    @Override
    public void onNode(@Nonnull DialogueContext ctx, @Nonnull String node) {
        whereabouts.put(ctx.getPlayer().getUuid(), new Where(ctx.getDialogueId(), node));
        pointHudAt(ctx.getPlayer().getUuid(), ctx.getDialogueId());
        // A passage the dialogue reaches only when its own assertion failed. The tester sees the same text
        // either way; they are simply no longer the thing that has to notice.
        for (Checks.Check c : Checks.failedByPassage(ctx.getDialogueId(), node)) {
            record.markSeen(c.id());
            record.decide(c.id(), RunRecord.Verdict.FAIL, "the dialogue's own check of this did not hold");
            getLogger().at(Level.WARNING).log("[harness] %s failed its own assertion", c.id());
            record.flush();
            refreshHuds();
        }

        List<Checks.Check> hit = Checks.forPassage(ctx.getDialogueId(), node);
        if (hit.isEmpty()) {
            refreshHuds();
            return;
        }
        for (Checks.Check c : hit) {
            boolean first = !record.check(c.id()).seen;
            record.markSeen(c.id());
            // A marker for anything outside the server that wants to act the moment a check runs. The capture
            // watcher keys off this to photograph the screen while the effect is still on it, which is the only
            // way to get evidence of something the server cannot see: no packet comes back to say a title drew.
            if (first) getLogger().at(Level.INFO).log("[harness] seen %s", c.id());
        }
        record.flush();
        refreshHuds();
    }

    /**
     * The panel follows the conversation: opening one points it at that station.
     *
     * <p>Also the one line that says a conversation happened at all. Without it a dialogue opening is invisible
     * from outside the game unless it happens to reach a passage some check is watching for the first time —
     * so "did the bound door talk?" could not be answered from the log, only by asking the person who clicked
     * it. A tester should never have to be the instrument.
     */
    @Override
    public void onStart(@Nonnull DialogueContext ctx) {
        pointHudAt(ctx.getPlayer().getUuid(), ctx.getDialogueId());
        getLogger().at(Level.INFO).log("[harness] opened %s", ctx.getDialogueId());
    }

    /** Back to the whole picture when the conversation closes. */
    @Override
    public void onEnd(@Nonnull DialogueContext ctx) {
        pointHudAt(ctx.getPlayer().getUuid(), null);
    }

    /**
     * The option text, which is all LowTalk offers to identify a choice. Kept for the log only: matching a check
     * on rendered text would break the moment someone rewords a station, so choices are not bound to checks.
     */
    @Override
    public void onChoice(@Nonnull DialogueContext ctx, @Nonnull String text) {
        getLogger().at(Level.FINE).log("[harness] %s chose: %s", ctx.getDialogueId(), text);
    }

    /**
     * A command that failed fails its check, without anyone having to notice.
     *
     * <p>This is the half no person should have to do. Three <<title>> commands threw during one run of station
     * 5 and the record showed a clean sheet, because a command failing does not stop the conversation and
     * nothing outside it could see. A verdict recorded by hand over that would have been wrong.
     */
    @Override
    public void onCommand(@Nonnull DialogueContext ctx, @Nonnull String command,
                          @Nonnull List<String> args, @Nullable String error) {
        // Every command our own tree runs, with its arguments. A check that judges itself can only report that
        // its assertion did not hold; it cannot say whether the feature or the assertion was wrong. This is
        // the difference between those two, and it costs one log line per option anyone picks.
        if (ctx.getDialogueId().startsWith("harness")) {
            getLogger().at(Level.INFO).log("[harness] ran <<%s%s>>%s", command,
                    args.isEmpty() ? "" : " " + String.join(" ", args),
                    error == null ? "" : "  -> ERROR: " + error);
        }
        if (error == null) return;
        Where where = whereabouts.get(ctx.getPlayer().getUuid());
        for (Checks.Check c : Checks.forPassage(ctx.getDialogueId(), where == null ? "" : where.node())) {
            record.decide(c.id(), RunRecord.Verdict.FAIL, "<<" + command + ">> failed: " + error);
            getLogger().at(Level.WARNING).log("[harness] %s failed: <<%s>> %s", c.id(), command, error);
        }
        record.flush();
        refreshHuds();
    }

    /** A dialogue that fell over is worth knowing about even when no single command owned the failure. */
    @Override
    public void onFailed(@Nonnull DialogueContext ctx, @Nonnull String message) {
        getLogger().at(Level.WARNING).log("[harness] dialogue %s failed: %s", ctx.getDialogueId(), message);
    }
}
