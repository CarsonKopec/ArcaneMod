package dev.arcane.client.gui;

import dev.arcane.spell.Spell;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** How each spell looks in the wheel and HUD: a stand-in vanilla item icon and an ARGB accent colour. */
public final class SpellIcons {
    private static final ItemStack BOLT = new ItemStack(Items.AMETHYST_SHARD);
    private static final ItemStack CHAIN = new ItemStack(Items.LIGHTNING_ROD);
    private static final ItemStack BLINK = new ItemStack(Items.ENDER_PEARL);
    private static final ItemStack WARD = new ItemStack(Items.SHIELD);
    private static final ItemStack NOVA = new ItemStack(Items.BLUE_ICE);

    private SpellIcons() {
    }

    public static ItemStack icon(Spell spell) {
        return switch (spell) {
            case ARCANE_BOLT -> BOLT;
            case CHAIN_LIGHTNING -> CHAIN;
            case BLINK -> BLINK;
            case ARCANE_WARD -> WARD;
            case FROST_NOVA -> NOVA;
        };
    }

    public static int color(Spell spell) {
        return switch (spell) {
            case ARCANE_BOLT -> 0xFFB46CFF;
            case CHAIN_LIGHTNING -> 0xFF7EE8FF;
            case BLINK -> 0xFFF3B5FF;
            case ARCANE_WARD -> 0xFFFFD86B;
            case FROST_NOVA -> 0xFFCFF6FF;
        };
    }
}
