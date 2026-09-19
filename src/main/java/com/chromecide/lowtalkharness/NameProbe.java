package com.chromecide.lowtalkharness;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * What an NPC is actually called, according to each of the three places a name lives.
 *
 * <p>Written for one question that could not be answered any other way: a renamed NPC came back with its old
 * name after a restart, and "it did not hold" does not say whether the new name failed to save or failed to
 * load. Those have opposite fixes.
 *
 * <p>Hytale keeps a name in three components. {@code PersistentDisplayName} is the saved one and the only one
 * with a codec behind it. {@code DisplayNameComponent} is the live one, rebuilt on load by
 * {@code DisplayNameSystems.HydrateDisplayName} from the persistent one, or by {@code RoleBuilderSystem} from
 * the role when there is no persistent one. {@code Nameplate} is the floating text, and nothing observed in the
 * load path puts it back — which is the reading this probe exists to confirm or kill.
 *
 * <p>Reading all three and printing them side by side turns a yes/no report from a person into a fact about
 * which component is missing.
 */
public final class NameProbe {

    private NameProbe() {}

    public record Found(String role, String nameplate, String persistent, String live, double distance) {
        public String line() {
            return String.format("%.1fm %s | nameplate: %s | persisted: %s | live: %s",
                    distance, role, nameplate, persistent, live);
        }
    }

    /**
     * Every NPC within {@code radius} of a point, with all three of its names.
     *
     * <p>Walks the store rather than asking for a sphere: the spatial index a sphere query reads is filled by a
     * ticking system and is still empty for entities that have only just loaded with their chunks, which is
     * exactly the moment after a restart that this is for.
     */
    public static List<Found> near(@Nonnull Store<EntityStore> store, @Nonnull Vector3d at, double radius) {
        List<Found> out = new ArrayList<>();
        double limit = radius * radius;
        store.forEachChunk(Query.and(NPCEntity.getComponentType(), TransformComponent.getComponentType()),
                (chunk, commandBuffer) -> {
                    for (int i = 0; i < chunk.size(); i++) {
                        Ref<EntityStore> ref = chunk.getReferenceTo(i);
                        if (ref == null || !ref.isValid()) continue;
                        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                        if (transform == null) continue;
                        Vector3d p = transform.getPosition();
                        double d2 = p.distanceSquared(at);
                        if (d2 > limit) continue;

                        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                        Nameplate plate = store.getComponent(ref, Nameplate.getComponentType());
                        PersistentDisplayName saved = store.getComponent(ref, PersistentDisplayName.getComponentType());
                        DisplayNameComponent live = store.getComponent(ref, DisplayNameComponent.getComponentType());
                        out.add(new Found(
                                npc == null ? "?" : String.valueOf(npc.getRoleName()),
                                plate == null ? "(no Nameplate component)" : String.valueOf(plate.getText()),
                                saved == null ? "(no PersistentDisplayName component)" : text(saved.getDisplayName()),
                                live == null ? "(no DisplayNameComponent)" : text(live.getDisplayName()),
                                Math.sqrt(d2)));
                    }
                });
        out.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        return out;
    }

    /** A message renders through the game's own formatting; for a log line the plain text is what is wanted. */
    private static String text(Object message) {
        if (message == null) return "(null)";
        try {
            var m = message.getClass().getMethod("getAnsiMessage");
            Object s = m.invoke(message);
            if (s != null && !String.valueOf(s).isBlank()) return String.valueOf(s);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not worth failing a diagnostic over; fall back to whatever toString gives
        }
        return String.valueOf(message);
    }
}
