package com.chromecide.lowtalkharness;

import com.chromecide.lowtalk.api.DialogueContext;
import com.chromecide.lowtalk.api.DialogueListener;
import com.chromecide.lowtalk.api.LowTalkApi;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.List;
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
        List<Checks.Check> hit = Checks.forPassage(ctx.getDialogueId(), node);
        if (hit.isEmpty()) return;
        for (Checks.Check c : hit) record.markSeen(c.id());
        record.flush();
    }

    /**
     * The option text, which is all LowTalk offers to identify a choice. Kept for the log only: matching a check
     * on rendered text would break the moment someone rewords a station, so choices are not bound to checks.
     */
    @Override
    public void onChoice(@Nonnull DialogueContext ctx, @Nonnull String text) {
        getLogger().at(Level.FINE).log("[harness] %s chose: %s", ctx.getDialogueId(), text);
    }
}
