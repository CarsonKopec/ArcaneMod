package dev.arcane.client.particle;

import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ParticleLimit;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Large, rolling impact billows. Uses the vanilla smoke atlas and normal translucent particle rendering. */
public final class PlanetfallCloud extends SingleQuadParticle {
    public enum Kind { DUST, SMOKE, FIRE }

    // ParticleLimit is a value record: distinct limits keep fire from consuming the smoke pool.
    private static final ParticleLimit DUST_LIMIT = new ParticleLimit(769);
    private static final ParticleLimit SMOKE_LIMIT = new ParticleLimit(389);
    private static final ParticleLimit FIRE_LIMIT = new ParticleLimit(385);
    private final SpriteSet sprites;
    private final Kind kind;
    private final float size, shade, turn;

    private PlanetfallCloud(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                            SpriteSet sprites, Kind kind, RandomSource random) {
        super(level, x, y, z, sprites.first());
        this.sprites = sprites;
        this.kind = kind;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = false;
        this.friction = kind == Kind.DUST ? 0.93f : 0.975f;
        this.lifetime = switch (kind) {
            case DUST -> 34 + random.nextInt(18);
            case SMOKE -> 70 + random.nextInt(36);
            case FIRE -> 32 + random.nextInt(20);
        };
        this.size = switch (kind) {
            case DUST -> 3.2f + random.nextFloat() * 3.4f;
            case SMOKE -> 5 + random.nextFloat() * 5;
            case FIRE -> 5 + random.nextFloat() * 4;
        };
        this.shade = 0.8f + random.nextFloat() * 0.2f;
        this.turn = (random.nextFloat() - 0.5f) * 0.035f;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        setAlpha(0);
        tint(0);
    }

    @Override public void tick() {
        super.tick();
        if (removed) return;
        oRoll = roll;
        roll += turn;
        if (kind != Kind.DUST) yd += 0.004;
        setSpriteFromAge(sprites);
        float p = age / (float) lifetime;
        tint(p);
        float opacity = kind == Kind.FIRE ? 0.9f : kind == Kind.SMOKE ? 0.66f : 0.5f;
        setAlpha(opacity * Math.min(1, age / 3f) * Math.min(1, (lifetime - age) / 20f));
    }

    private void tint(float p) {
        if (kind == Kind.FIRE) {
            // A combustion lobe cools from yellow-white through orange to soot as it expands.
            float cool = Mth.clamp((p - 0.2f) / 0.65f, 0, 1);
            setColor(Mth.lerp(cool, 1, 0.24f), Mth.lerp(cool, 0.67f - p * 0.28f, 0.20f),
                    Mth.lerp(cool, 0.14f, 0.18f));
        } else if (kind == Kind.SMOKE) {
            setColor(shade * 0.32f, shade * 0.30f, shade * 0.29f);
        } else {
            setColor(shade * 0.58f, shade * 0.51f, shade * 0.43f);
        }
    }

    @Override public float getQuadSize(float partialTick) {
        float p = Mth.clamp((age + partialTick) / lifetime, 0, 1);
        return size * (0.45f + 0.9f * (1 - (1 - p) * (1 - p)));
    }

    @Override protected int getLightColor(float partialTick) {
        int ambient = super.getLightColor(partialTick);
        if (kind != Kind.FIRE) return ambient;
        float p = (age + partialTick) / lifetime;
        int heat = Math.round(15 * Mth.clamp((0.9f - p) / 0.4f, 0, 1));
        return LightTexture.pack(Math.max(heat, LightTexture.block(ambient)), Math.max(heat, LightTexture.sky(ambient)));
    }

    @Override protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override public Optional<ParticleLimit> getParticleLimit() {
        return Optional.of(switch (kind) {
            case DUST -> DUST_LIMIT;
            case SMOKE -> SMOKE_LIMIT;
            case FIRE -> FIRE_LIMIT;
        });
    }

    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                  double vx, double vy, double vz, RandomSource random) {
            return new PlanetfallCloud(level, x, y, z, vx, vy, vz, sprites, kind, random);
        }
    }
}
