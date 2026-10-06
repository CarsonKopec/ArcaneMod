#version 330

// Planetfall's volumes, marched at half resolution (see PlanetfallShader): the plasma sheath that burns round the
// falling planet, and after the impact the fireball, the mushroom cloud it rolls up into, and the base surge of dust
// racing out over the ground. Out: the light gathered (a quarter of it, so it can exceed 1) and how opaque it is.

#moj_import <arcane_spells:planetfall_common.glsl>

uniform sampler2D SceneDepth;

in vec2 texCoord;
out vec4 fragColor;

// ---------------------------------------------------------------- the plasma sheath

// The burning air wrapped round the planet: a thin skin at its nose, thickening and torn into flames past its limb.
// Returns the density; heat comes back in 0 (cool, at its edge) to 1 (white hot, at the nose).
float sheath(vec3 p, out float heat) {
    float radius = PlanetCenter.w;
    vec3 rel = p - PlanetCenter.xyz;
    float r = length(rel);
    vec3 n = rel / max(r, 1.0e-3);
    float ahead = dot(n, RiftAxis.xyz);
    float thickness = 0.06 + 0.42 * (1.0 - ahead) * 0.5;
    float x = (r / radius - 1.0) / thickness;
    // Flames stream back over it from the nose, and flicker.
    vec3 q = n * 3.2 - RiftAxis.xyz * (r / radius) * 1.5 + RiftAxis.xyz * Clock.x * 2.6;
    float flames = fbm(q + vec3(0.0, 0.0, PlanetHeat.w * 0.01), 4);
    float edge = flames * 1.15 - x * 0.75;
    float density = smoothstep(0.0, 0.08, x + 0.04) * smoothstep(0.15, 0.45, edge);
    // Nothing burns until it is through the rift and into our air.
    density *= smoothstep(0.0, radius, riftSide(p));
    heat = clamp((1.0 - x) * (0.35 + 0.65 * (ahead * 0.5 + 0.5)), 0.0, 1.0);
    return density * PlanetHeat.x * PlanetHeat.x;
}

vec4 marchSheath(vec3 dir, float sceneDistance) {
    float radius = PlanetCenter.w;
    if (radius <= 0.0 || PlanetHeat.x < 0.05) return vec4(0.0);
    vec2 span = sphereHits(dir, PlanetCenter.xyz, radius * 1.5);
    if (span.y <= 0.0) return vec4(0.0);
    // The planet itself hides the sheath's far side.
    vec2 body = sphereHits(dir, PlanetCenter.xyz, radius);
    float start = max(span.x, 0.0);
    float end = min(min(span.y, sceneDistance), body.x > 0.0 ? body.x : FAR);
    if (!(end > start)) return vec4(0.0);
    const int STEPS = 24;
    float stepLength = (end - start) / float(STEPS);
    float t = start + stepLength * hash12(gl_FragCoord.xy + Clock.x);
    vec3 light = vec3(0.0);
    float through = 1.0;
    for (int i = 0; i < STEPS; i++) {
        float heat;
        float density = sheath(rayOrigin + dir * t, heat);
        if (density > 0.001) {
            float absorbed = 1.0 - exp(-density * stepLength * 0.15);
            vec3 flame = mix(heatColor(heat), vec3(0.85, 0.3, 1.0), falloff(0.0, 0.35, heat) * 0.6);
            light += through * absorbed * flame * (0.7 + 2.2 * heat);
            through *= 1.0 - absorbed;
        }
        if (through < 0.02) break;
        t += stepLength;
    }
    return vec4(light, 1.0 - through);
}

// ---------------------------------------------------------------- the blast

float sdTorus(vec3 p, float major, float minor, float squash) {
    vec2 q = vec2(length(p.xz) - major, p.y * squash);
    return length(q) - minor;
}

float smoothUnion(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

// Where the cloud's parts are, ticks after the impact. The fireball swells and rises, rolling up into a toroidal cap;
// a stem of dust follows it up; and a ring of dust, the base surge, races out along the ground.
float blastShape(vec3 p, float t, out float core, out float surge) {
    float rise = 10.0 + 52.0 * (1.0 - exp(-t / 45.0)) + 0.1 * t;
    float swell = 1.0 - exp(-t / 28.0);
    vec3 cap = vec3(0.0, rise, 0.0);
    float fireball = length(p - vec3(0.0, 4.0 + 0.6 * min(t, 20.0), 0.0)) - (6.0 + 24.0 * (1.0 - exp(-t / 5.0)));
    float roll = sdTorus(p - cap, 8.0 + 24.0 * swell, 7.0 + 11.0 * swell, 1.3);
    float dome = length((p - cap - vec3(0.0, 7.0 + 6.0 * swell, 0.0)) / vec3(1.0, 0.78, 1.0)) - (6.0 + 21.0 * swell);
    float head = smoothUnion(roll, dome, 6.0);
    head = mix(fireball, head, smoothstep(6.0, 34.0, t));
    float stemRadius = (3.0 + 5.0 * swell) * (1.0 + 0.6 * smoothstep(rise * 0.6, rise, p.y));
    float stem = max(length(p.xz) - stemRadius, abs(p.y - rise * 0.5) - rise * 0.5);
    stem += falloff(6.0, 18.0, t) * 40.0;
    core = smoothUnion(head, stem, 5.0);
    float reach = 10.0 + 84.0 * (1.0 - exp(-t / 22.0));
    surge = sdTorus(p - vec3(0.0, 5.0, 0.0), reach, 6.0 + 9.0 * (1.0 - exp(-t / 25.0)), 1.35);
    return min(core, surge);
}

// Billows: rounded puffs with creases between them, rather than the soft blobs plain noise gives.
float billow(vec3 p, int octaves) {
    return 1.0 - abs(fbm(p, octaves) * 2.0 - 1.0);
}

// Density at p (relative to the impact point), given its shape there, and how hot it is.
float blast(vec3 p, float t, float core, float surge, out float heat) {
    vec3 rise = vec3(0.0, -t * 0.015, 0.0);
    // Broad heaving lobes shape the cloud; at its edges, finer cauliflower knots eat into it.
    float broad = billow(p * 0.04 + rise + vec3(PlanetHeat.w * 0.01, 0.0, 0.0), 3);
    float knots = billow(p * 0.13 + rise * 2.5 + 5.0, 3);
    float coreShape = core + (0.55 - broad) * 16.0;
    float coreDensity = falloff(-1.5, 2.5, coreShape + (0.6 - knots) * 7.0 * smoothstep(-8.0, 2.0, coreShape)) * Blast.z;
    // The base surge: a ragged wall of dust, thick in places and torn through in others.
    float angle = atan(p.z, p.x + 1.0e-4);
    float ragged = fbm(vec3(cos(angle) * 3.0, sin(angle) * 3.0, t * 0.02 + 2.0), 3);
    float surgeShape = surge + (0.55 - broad) * 12.0 + (0.5 - ragged) * 12.0;
    float surgeDensity = falloff(-1.5, 2.5, surgeShape + (0.6 - knots) * 6.0) * Blast.w * 0.8;
    // Hot at the heart while there is fire, in churning tongues, cooling outwards and upwards.
    heat = Blast.y * falloff(-14.0, 10.0, core) * clamp(0.2 + 1.3 * knots * knots, 0.0, 1.0) * falloff(40.0, 110.0, p.y + t * 0.2);
    return max(coreDensity, surgeDensity);
}

vec4 marchBlast(vec3 dir, float sceneDistance) {
    float t = Blast.x;
    if (t < 0.0 || Blast.z + Blast.w <= 0.001) return vec4(0.0);
    vec3 base = Shock.xyz;
    float bound = 130.0;
    vec2 span = sphereHits(dir, base + vec3(0.0, 40.0, 0.0), bound);
    if (span.y <= 0.0) return vec4(0.0);
    float start = max(span.x, 0.0);
    float end = min(span.y, sceneDistance);
    if (!(end > start)) return vec4(0.0);
    const int STEPS = 32;
    float stepLength = (end - start) / float(STEPS);
    float march = start + stepLength * hash12(gl_FragCoord.xy + Clock.x * 7.0);
    vec3 sun = normalize(vec3(0.35, 1.0, 0.25));
    vec3 sky = mix(vec3(0.42, 0.46, 0.55), vec3(0.16, 0.1, 0.26), Clock.y);
    vec3 light = vec3(0.0);
    float through = 1.0;
    for (int i = 0; i < 56; i++) {
        if (!(march <= end) || through < 0.02) break;
        vec3 p = rayOrigin + dir * march - base;
        float core, surge;
        float shape = blastShape(p, t, core, surge);
        if (shape > 18.0) {
            // Well clear of every part of the cloud: leap ahead by the distance to it, less the furthest its billows
            // reach.
            march += max(stepLength, shape - 17.0);
        } else {
            float heat;
            float density = blast(p, t, core, surge, heat);
            if (density > 0.002) {
                // Self-shadowing from the bare shape a little way towards the sun: no billows, which cost too much twice.
                float shadowCore, shadowSurge;
                blastShape(p + sun * 7.0, t, shadowCore, shadowSurge);
                float shade = exp(-max(falloff(-3.0, 4.0, shadowCore) * Blast.z, falloff(-2.0, 3.0, shadowSurge) * Blast.w * 0.7) * 2.6);
                // Sunlit tops over shadowed undersides. The powder term darkens the thin edges facing the light, as
                // real clouds' are, which gives them their rounded, fluffy look.
                float powder = 1.0 - exp(-density * 4.0);
                vec3 smoke = vec3(0.3, 0.28, 0.27) * (sky * (0.25 + 0.3 * shade) + vec3(1.0, 0.95, 0.88) * shade * powder * 2.4);
                // The fire beneath lights the column's underside, near the stem.
                smoke += vec3(1.0, 0.42, 0.12) * Blast.y * falloff(0.0, 60.0, p.y) * falloff(10.0, 40.0, length(p.xz)) * 0.6;
                vec3 glow = heatColor(heat) * heat * 5.0;
                float absorbed = 1.0 - exp(-density * stepLength * 0.3);
                light += through * absorbed * (smoke + glow);
                through *= 1.0 - absorbed;
            }
            march += stepLength;
        }
    }
    return vec4(light, 1.0 - through);
}

void main() {
    vec2 uv = texCoord;
    vec3 dir;
    float sceneDistance = setupRay(uv, texture(SceneDepth, uv).r, dir);
    vec4 volume = marchSheath(dir, sceneDistance);
    vec4 cloud = marchBlast(dir, sceneDistance);
    volume = vec4(volume.rgb + cloud.rgb * (1.0 - volume.a), 1.0 - (1.0 - volume.a) * (1.0 - cloud.a));
    fragColor = vec4(volume.rgb / 4.0, volume.a);
}
