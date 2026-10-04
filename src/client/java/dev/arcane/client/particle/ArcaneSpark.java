package dev.arcane.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** Full-bright soft sprite, with no gravity and a quick fade to keep runes legible. */
public final class ArcaneSpark extends SimpleAnimatedParticle {
    ArcaneSpark(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, int color) {
        super(level, x, y, z, sprites, 0);
        xd = vx;
        yd = vy;
        zd = vz;
        // Moving sparks linger; stationary ones (ring outlines) vanish fast so shapes stay crisp.
        lifetime = (vx * vx + vy * vy + vz * vz > 0.03) ? 24 : 10;
        quadSize = 0.11f;
        friction = 0.94f;
        hasPhysics = false;
        setColor(color);
        setSpriteFromAge(sprites);
    }

    @Override public void tick() {
        super.tick();
        setAlpha(Math.max(0, 1f - age / (float) lifetime));
    }

    public record Provider(SpriteSet sprites, int color) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                                 double vx, double vy, double vz, RandomSource random) {
            return new ArcaneSpark(level, x, y, z, vx, vy, vz, sprites, color);
        }
    }
}
