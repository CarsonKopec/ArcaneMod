package dev.arcane.api.client;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.api.effect.SpellEffectType;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Draws spell effects on the client. Register renderers in your client initializer, for your own effect types or to
 * add to the built-in ones (see {@code ModSpellEffects}); every renderer for a type runs, in registration order.
 *
 * <p>A renderer that throws is logged and switched off until the game restarts, rather than disconnecting the player.
 */
public final class SpellEffectRenderers {
    private static final Map<SpellEffectType, List<SpellEffectRenderer>> RENDERERS = new IdentityHashMap<>();

    private SpellEffectRenderers() {
    }

    public static void register(SpellEffectType type, SpellEffectRenderer renderer) {
        RENDERERS.computeIfAbsent(type, t -> new ArrayList<>()).add(renderer);
    }

    /** Not part of the API: Arcane Spells calls this for each effect the server sends. */
    public static void render(SpellEffect effect, ClientLevel level) {
        List<SpellEffectRenderer> renderers = RENDERERS.get(effect.type());
        if (renderers == null) return;
        Iterator<SpellEffectRenderer> iterator = renderers.iterator();
        while (iterator.hasNext()) {
            try {
                iterator.next().render(effect, level);
            } catch (RuntimeException | LinkageError e) {
                iterator.remove();
                ArcaneSpells.LOGGER.error("A renderer for spell effect {} failed and is switched off until the game restarts", effect.type(), e);
            }
        }
    }

    @FunctionalInterface
    public interface SpellEffectRenderer {
        /** Called on the client thread, as the effect arrives. */
        void render(SpellEffect effect, ClientLevel level);
    }
}
