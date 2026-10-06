package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.spell.CasterState;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
    /** Legacy save key, retained only to import existing player data into the CCA progression component. */
    public static final AttachmentType<CasterState> CASTER = AttachmentRegistry.create(ArcaneSpells.id("caster"),
            builder -> builder.persistent(CasterState.CODEC));

    private ModAttachments() {
    }

    /** Loading this class registers the attachments. */
    public static void register() {
    }
}
