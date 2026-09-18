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
    /** Where each player is, so a command that fails can be blamed on the passage that ran it. */
    private final Map<UUID, String> currentNode = new ConcurrentHashMap<>();

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

    @Override
    protected void setup() {
        String version = serverVersion();
        record = new RunRecord(dataFolder(), version);

        LowTalkApi api = LowTalkApi.get();
        api.addListener(this);
        getCommandRegistry().registerCommand(new HarnessCommand(this));

        int[] t = record.tally(Checks.ids());
        getLogger().at(Level.INFO).log(
                "LowTalk harness watching server %s: %d checks, %d seen, %d decided (%d pass, %d fail). Record: %s",
                version, Checks.ids().size(), t[0], t[1], t[2], t[3], record.file());
    }

    @Override
    protected void shutdown() {
        if (record != null) record.flush();
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
        currentNode.put(ctx.getPlayer().getUuid(), node);
        pointHudAt(ctx.getPlayer().getUuid(), ctx.getDialogueId());
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

    /** The panel follows the conversation: opening one points it at that station. */
    @Override
    public void onStart(@Nonnull DialogueContext ctx) {
        pointHudAt(ctx.getPlayer().getUuid(), ctx.getDialogueId());
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
        if (error == null) return;
        for (Checks.Check c : Checks.forPassage(ctx.getDialogueId(), currentNode.getOrDefault(ctx.getPlayer().getUuid(), ""))) {
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
