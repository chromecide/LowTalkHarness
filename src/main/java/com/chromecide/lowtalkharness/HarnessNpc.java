package com.chromecide.lowtalkharness;

import com.chromecide.lowtalk.api.LowTalkApi;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * The one NPC the harness needs.
 *
 * <p>Most checks do not need a body: a title or a toast can be driven from a dialogue opened with no NPC at all.
 * Some cannot. Renaming an NPC, its attitude, an animation played on it, a sound at its position — those need
 * something standing there, and the honest way to test them is on an NPC rather than by asserting the command
 * did not throw.
 *
 * <p>So: one NPC, tagged {@code harness}, carrying the whole tree. Not a corridor of fifteen. The corridor is
 * LowTalk's own demo for creators and stays that way; walking sixty blocks to re-check one title style after a
 * version bump is a cost with nothing on the other side of it.
 */
public final class HarnessNpc {

    /** What the harness dialogue binds to. The dialogue says {@code npc: @harness}. */
    public static final String TAG = "harness";
    public static final String ROLE = "LowTalk_Tester";
    public static final String NAME = "LowTalk harness";

    /**
     * The one NPC that can fight back.
     *
     * <p>The tester's own role is built on Template_Temple, whose attitude sensors are Neutral and Friendly:
     * no hostile branch, no attack, no combat. Setting it hostile changed a value nothing read, so
     * "does a hostile NPC behave like one" could not be asked of it at all. A goblin scavenger can.
     *
     * <p>It is spawned friendly, by overriding its attitude towards whoever spawned it, or it would set about
     * them instead of talking. Its own dialogue is what turns it hostile, briefly, and puts it back.
     */
    public static final String FIGHTER_TAG = "harness_fighter";
    public static final String FIGHTER_NAME = "LowTalk harness (fighter)";

    /**
     * Roles that will fight, tried in order until one spawns.
     *
     * <p>It was a single hard-coded {@code Goblin_Scavenger}, which exists on the release line and not on
     * 0.7.0-pre.3.1, so the check simply did not start there and said so only in the tester's chat. A list
     * survives a role being renamed between versions; every one of these is a concrete role that fights on
     * both lines as of 0.6.8 and 0.7.0-pre.3.1.
     */
    private static final java.util.List<String> FIGHTER_ROLES = java.util.List.of(
            "Goblin_Scrapper", "Goblin_Thief", "Trork_Sentry", "Outlander_Hunter", "Scarak_Defender");

    private HarnessNpc() {}

    /**
     * Put a tester where the player is standing, facing them, and tag it so the harness dialogue binds.
     *
     * <p>Tagging goes through LowTalk's own API rather than its variable store, both because that is the
     * supported way in and because the harness exists partly to use the API as a consumer would.
     */
    public static boolean spawnFacing(@Nonnull Store<EntityStore> store, @Nonnull Vector3d at,
                                      @Nonnull Consumer<String> out) {
        return spawn(store, at, ROLE, NAME, TAG, null, out);
    }

    /**
     * Put a goblin where the player is standing, calm, and tag it so the fighter dialogue binds.
     *
     * <p>Calm matters: its role's default attitude to a player is hostile, so without the override it would
     * attack rather than talk and the check could never be started.
     */
    public static boolean spawnFighter(@Nonnull Store<EntityStore> store, @Nonnull Vector3d at,
                                       @Nonnull Ref<EntityStore> player, @Nonnull Consumer<String> out) {
        for (String role : FIGHTER_ROLES) {
            if (spawn(store, at, role, FIGHTER_NAME, FIGHTER_TAG, player, out)) return true;
        }
        out.accept("None of these roles would spawn: " + String.join(", ", FIGHTER_ROLES)
                + ". They may have been renamed on this server version.");
        return false;
    }

    private static boolean spawn(@Nonnull Store<EntityStore> store, @Nonnull Vector3d at, @Nonnull String role,
                                 @Nonnull String name, @Nonnull String tag,
                                 @Nullable Ref<EntityStore> friendlyTo, @Nonnull Consumer<String> out) {
        NPCPlugin npcs = NPCPlugin.get();
        if (npcs == null) {
            out.accept("The NPC plugin is not loaded, so there is nothing to spawn with.");
            return false;
        }
        // Two blocks in front of where the player stands, turned to face back at them: the game's CanInteract
        // sensor only accepts a player inside the NPC's front view sector, so an NPC facing away cannot be talked to.
        Vector3d pos = new Vector3d(at.x, at.y, at.z);
        Rotation3f facing = new Rotation3f(0.0f, Rotation3f.lookAt(pos, new Vector3d(at.x, at.y, at.z - 2.0)).yaw(), 0.0f);
        var pair = npcs.spawnNPC(store, role, null, pos, facing);
        if (pair == null) {
            // Quiet: spawnFighter works down a list and only the last word matters to the tester.
            HarnessPlugin plugin = HarnessPlugin.get();
            if (plugin != null) {
                plugin.getLogger().at(java.util.logging.Level.INFO).log("[harness] role %s would not spawn", role);
            }
            return false;
        }
        Ref<EntityStore> ref = pair.first();
        DisplayNameSupport.setDisplayName(ref, name, store);
        if (friendlyTo != null) {
            try {
                com.hypixel.hytale.server.npc.role.support.WorldSupport.get(ref, store)
                        .overrideAttitude(friendlyTo, com.hypixel.hytale.server.core.asset.type.attitude.Attitude.FRIENDLY, 1.0e9);
            } catch (RuntimeException e) {
                out.accept("Spawned, but could not calm it down: " + e + ". Expect to be attacked.");
            }
        }
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null) {
            out.accept("The NPC spawned but has no id, so it cannot be tagged. Remove it and try again.");
            return false;
        }
        LowTalkApi.get().tagNpc(uuid.getUuid(), tag);
        out.accept(name + " spawned as " + role + ", tagged '" + tag + "'. Talk to it to run the checks.");
        HarnessPlugin plugin = HarnessPlugin.get();
        if (plugin != null) {
            // In the log as well as the chat: a spawn that quietly did not happen is how the fighter check
            // failed on the pre-release line with nothing written down anywhere.
            plugin.getLogger().at(java.util.logging.Level.INFO).log("[harness] spawned %s as %s tagged @%s",
                    name, role, tag);
        }
        return true;
    }
}
