package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

public final class ModGameRules {
    /**
     * {@code arcane_spells:terrain_damage}: whether spells may break blocks at all. Off, every spell still hurts,
     * pushes and burns as usual but leaves terrain (and TNT) alone, as in Adventure mode. On by default.
     */
    public static final GameRule<Boolean> TERRAIN_DAMAGE = GameRuleBuilder.forBoolean(true)
            .category(GameRuleCategory.MISC)
            .buildAndRegister(ArcaneSpells.id("terrain_damage"));

    private ModGameRules() {
    }

    /** Loading this class registers the game rules. */
    public static void register() {
    }
}
