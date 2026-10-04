package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.spell.CasterState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
    /** Mana and cooldowns. Saved with the player, and synced to that player alone for the HUD and spell wheel. */
    public static final AttachmentType<CasterState> CASTER = AttachmentRegistry.create(ArcaneSpells.id("caster"),
            builder -> builder.persistent(CasterState.CODEC).syncWith(CasterState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private ModAttachments() {
    }

    /** Loading this class registers the attachments. */
    public static void register() {
    }
}
