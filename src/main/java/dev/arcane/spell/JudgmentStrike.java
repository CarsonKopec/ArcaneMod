package dev.arcane.spell;

import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One Celestial Judgment, simulated on the server. Clients are sent a message at each phase and draw the rest
 * themselves:
 * <ol>
 *   <li>Gather ({@link SpellTuning#JUDGMENT_GATHER_TICKS}): the sky darkens, circles form, lightning strikes around the
 *       mark.</li>
 *   <li>Beam ({@link SpellTuning#JUDGMENT_BEAM_TICKS}): a pillar of light bores a shaft from the surface to well below
 *       the mark, burning whatever stands in it.</li>
 *   <li>Detonation: a blast hurls everything nearby outwards, then the crater is carved from the centre outwards
 *       over {@link SpellTuning#JUDGMENT_CARVE_TICKS} ticks, and its floor and walls are left scorched.</li>
 * </ol>
 *
 * <p>Terrain follows the bolt's rules: blocks only break where the caster could break them by hand, and anything
 * unbreakable or as blast resistant as obsidian survives. Broken blocks are vaporised, not dropped; a container's
 * contents still spill out. The work is spread over many ticks, never more than a few hundred blocks in one.
 */
final class JudgmentStrike {
    private static final int BEAM_START = SpellTuning.JUDGMENT_GATHER_TICKS;
    private static final int BLAST_AT = BEAM_START + SpellTuning.JUDGMENT_BEAM_TICKS;
    private static final int END = BLAST_AT + SpellTuning.JUDGMENT_CARVE_TICKS;
    /** The shaft is finished this many ticks before the detonation. */
    private static final int SHAFT_SLACK = 4;

    private final ServerLevel level;
    private final ServerPlayer caster;
    private final Vec3 impact;
    /** What its damage is multiplied by, fixed at the cast. */
    private final float spellPower;
    private final int sourceId;
    private final double skyY;
    /** Read once at the cast, like the bolt: switching to Adventure mid-strike doesn't stop it. */
    private final boolean mayBuild;
    private final RandomSource random;
    private int age;
    private int shaftTop;
    private int shaftBottom;
    private int shaftRow;
    private List<BlockPos> crater = List.of();
    private List<BlockPos> shell = List.of();
    private int carved;

    JudgmentStrike(ServerLevel level, ServerPlayer caster, Vec3 impact, float spellPower, int sourceId) {
        this.level = level;
        this.caster = caster;
        this.impact = impact;
        this.spellPower = spellPower;
        this.sourceId = sourceId;
        this.skyY = Math.min(level.getMaxY() - 1, impact.y + SpellTuning.JUDGMENT_SKY_HEIGHT);
        this.mayBuild = SpellTerrain.mayBreak(level, caster);
        this.random = level.getRandom();
        SpellEffects.send(level, ModSpellEffects.JUDGMENT_MARK, impact, skyOffset(), 1, SpellTuning.JUDGMENT_GATHER_TICKS, sourceId);
        level.playSound(null, impact.x, impact.y, impact.z, ModSounds.JUDGMENT_GATHER, SoundSource.PLAYERS, 6.0f, 1.0f);
    }

    /** Advances the strike one tick. Returns true once it's over. */
    boolean tick() {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(impact.x), SectionPos.blockToSectionCoord(impact.z))) return true;
        age++;
        if (age < BEAM_START) {
            gather();
        } else if (age < BLAST_AT) {
            if (age == BEAM_START) startBeam();
            burn();
        } else if (age == BLAST_AT) {
            detonate();
        } else {
            carveCrater();
        }
        return age >= END;
    }

    private Vec3 skyOffset() {
        return new Vec3(0, skyY - impact.y, 0);
    }

    /** Lightning around the mark, quickening as the beam nears. */
    private void gather() {
        int interval = age > BEAM_START - 15 ? 4 : 8;
        if (age % interval != 0) return;
        double angle = random.nextDouble() * Mth.TWO_PI;
        double distance = SpellTuning.JUDGMENT_CRATER_RADIUS * (0.5 + random.nextDouble() * 0.5);
        lightning(impact.x + Math.cos(angle) * distance, impact.z + Math.sin(angle) * distance);
    }

    private void startBeam() {
        SpellEffects.send(level, ModSpellEffects.JUDGMENT_BEAM, impact, skyOffset(), 1, SpellTuning.JUDGMENT_BEAM_TICKS, sourceId);
        level.playSound(null, impact.x, impact.y, impact.z, ModSounds.JUDGMENT_BEAM, SoundSource.PLAYERS, 8.0f, 1.0f);
        lightning(impact.x, impact.z);
        // The shaft starts at the highest thing in the beam (a tree, a roof) and ends well below the mark.
        double radius = SpellTuning.JUDGMENT_BEAM_RADIUS;
        int top = Mth.floor(impact.y);
        for (BlockPos column : disc(Mth.floor(impact.y), radius)) {
            top = Math.max(top, level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()));
        }
        shaftTop = Math.min(top, level.getMaxY());
        // All the way down: bedrock itself is unbreakable, so the shaft ends on it.
        shaftBottom = level.getMinY();
        shaftRow = shaftTop;
    }

    /** Bores the shaft downwards, and burns everything in the beam every few ticks. */
    private void burn() {
        int burning = age - BEAM_START;
        int rows = shaftTop - shaftBottom + 1;
        int perTick = Mth.ceil(rows / (float) (SpellTuning.JUDGMENT_BEAM_TICKS - SHAFT_SLACK));
        carveShaft(perTick);
        if (burning % SpellTuning.JUDGMENT_BEAM_HIT_INTERVAL != 0) return;
        double reach = SpellTuning.JUDGMENT_BEAM_RADIUS + 1;
        AABB column = new AABB(impact.x - reach, shaftBottom, impact.z - reach, impact.x + reach, skyY, impact.z + reach);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, column, e -> SpellTargets.canHarm(caster, e))) {
            Vec3 at = victim.getBoundingBox().getCenter();
            if (Mth.square(at.x - impact.x) + Mth.square(at.z - impact.z) > reach * reach) continue;
            if (victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), SpellTuning.JUDGMENT_BEAM_DAMAGE * spellPower * smite(victim))) {
                victim.igniteForSeconds(SpellTuning.JUDGMENT_BEAM_FIRE_SECONDS);
            }
        }
    }

    private void carveShaft(int rows) {
        for (int i = 0; i < rows && shaftRow >= shaftBottom; i++, shaftRow--) {
            for (BlockPos pos : disc(shaftRow, SpellTuning.JUDGMENT_BEAM_RADIUS)) vaporise(pos);
        }
    }

    private void detonate() {
        carveShaft(Integer.MAX_VALUE);
        SpellEffects.send(level, ModSpellEffects.JUDGMENT_BLAST, impact, new Vec3(0, 1, 0), 1, 0, sourceId);
        level.playSound(null, impact.x, impact.y, impact.z, ModSounds.JUDGMENT_BLAST, SoundSource.PLAYERS, 16.0f, 1.0f);

        double radius = SpellTuning.JUDGMENT_BLAST_RADIUS;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(radius),
                e -> SpellTargets.canHarm(caster, e))) {
            Vec3 at = victim.getBoundingBox().getCenter();
            double distance = at.distanceTo(impact);
            if (distance > radius) continue;
            float falloff = (float) (1 - distance / radius);
            if (victim.hurtServer(level, level.damageSources().explosion(caster, caster),
                    SpellTuning.JUDGMENT_BLAST_DAMAGE * falloff * spellPower * smite(victim))) {
                Vec3 away = new Vec3(at.x - impact.x, 0, at.z - impact.z);
                away = away.lengthSqr() < 1.0e-6 ? Vec3.ZERO : away.normalize();
                double push = SpellTuning.JUDGMENT_BLAST_KNOCKBACK * falloff;
                victim.push(away.x * push, 0.4 + 0.8 * falloff, away.z * push);
            }
        }
        Crater plan = Crater.plan(level, impact, SpellTuning.JUDGMENT_CRATER_RADIUS, SpellTuning.JUDGMENT_CRATER_DEPTH, random);
        crater = plan.blocks;
        shell = plan.shell;
    }

    private void carveCrater() {
        int perTick = Mth.ceil(crater.size() / (float) SpellTuning.JUDGMENT_CARVE_TICKS);
        for (int i = 0; i < perTick && carved < crater.size(); i++) vaporise(crater.get(carved++));
        if (age == END) SpellTerrain.scorch(level, caster, mayBuild, shell, random);
    }

    private void vaporise(BlockPos pos) {
        if (SpellTerrain.mayVaporise(level, caster, mayBuild, pos, level.getBlockState(pos))) SpellTerrain.vaporise(level, pos);
    }

    /** The block positions in row {@code y} within {@code radius} of the beam's axis. */
    private List<BlockPos> disc(int y, double radius) {
        List<BlockPos> positions = new ArrayList<>();
        int reach = Mth.ceil(radius);
        int cx = Mth.floor(impact.x), cz = Mth.floor(impact.z);
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                if (Mth.square(x + 0.5 - impact.x) + Mth.square(z + 0.5 - impact.z) <= radius * radius) positions.add(new BlockPos(x, y, z));
            }
        }
        return positions;
    }

    /** Lightning that only looks and sounds the part: no fire, no damage, no charged creepers. */
    private void lightning(double x, double z) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt == null) return;
        bolt.snapTo(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)), z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /** Smite: the light of judgment burns the undead hardest. */
    private static float smite(LivingEntity victim) {
        return victim.getType().is(EntityTypeTags.UNDEAD) ? SpellTuning.JUDGMENT_SMITE_MULTIPLIER : 1;
    }
}
