#version 330

// Planetfall, drawn into the frame (see PlanetfallShader): deep space, the rift, the planet, the shock dome and the
// light they shed, over the world as it was drawn. The scene's depth decides what is in front of what. Out: high
// dynamic range colour, RGBM-encoded (rgb scaled into range, alpha the scale), for the bloom and the final composite.

#moj_import <arcane_spells:planetfall_common.glsl>

uniform sampler2D InputColor;
uniform sampler2D SceneDepth;
uniform sampler2D Volume;

in vec2 texCoord;
out vec4 fragColor;

// ---------------------------------------------------------------- deep space

// One layer of stars: cells of sky holding at most one star each, of its own colour, twinkling.
float stars(vec3 dir, float scale, float density, float size, float seed, out vec3 tint) {
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    float h = hash13(cell + seed);
    tint = vec3(1.0);
    if (h > density) return 0.0;
    vec3 star = cell + 0.2 + 0.6 * hash33(cell + seed * 1.7);
    float d = length(p - star);
    float twinkle = 0.65 + 0.35 * sin(Clock.x * (1.5 + 6.0 * h) + h * 50.0);
    float warmth = hash13(cell + seed + 9.1);
    tint = warmth < 0.5 ? mix(vec3(0.62, 0.76, 1.0), vec3(1.0), warmth * 2.0) : mix(vec3(1.0), vec3(1.0, 0.76, 0.52), warmth * 2.0 - 1.0);
    return falloff(0.0, size, d) * twinkle * (0.4 + 0.6 * h / density);
}

// The few brightest stars, with the four-pointed glare a lens gives them, its spikes square to the screen.
vec3 brightStars(vec3 dir) {
    float scale = 26.0;
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    float h = hash13(cell + 41.0);
    if (h > 0.05) return vec3(0.0);
    vec3 star = cell + 0.25 + 0.5 * hash33(cell + 17.0);
    vec3 offset = p - star;
    offset -= dir * dot(offset, dir);
    vec3 right = vec3(ViewMat[0][0], ViewMat[1][0], ViewMat[2][0]);
    vec3 up = vec3(ViewMat[0][1], ViewMat[1][1], ViewMat[2][1]);
    float x = dot(offset, right), y = dot(offset, up);
    float core = exp(-(x * x + y * y) * 2400.0);
    float halo = exp(-(x * x + y * y) * 260.0) * 0.25;
    float spikes = exp(-abs(x) * 260.0) * exp(-abs(y) * 9.0) + exp(-abs(y) * 260.0) * exp(-abs(x) * 9.0);
    float warmth = hash13(cell + 3.3);
    vec3 tint = mix(vec3(0.7, 0.82, 1.0), vec3(1.0, 0.85, 0.66), warmth);
    float twinkle = 0.75 + 0.25 * sin(Clock.x * 3.0 + h * 90.0);
    return tint * (core * 4.0 + halo + spikes * 0.6) * twinkle * (0.5 + 10.0 * h);
}

// Glowing gas, domain-warped into filaments, crossed by dark lanes of dust (returned, to dim the stars behind them).
vec3 nebula(vec3 dir, float richness, out float dust) {
    float seed = PlanetHeat.w;
    vec3 base = dir * 1.9 + vec3(seed * 0.013, seed * 0.004, seed * 0.007);
    vec3 warp = vec3(fbm(base + 1.7, 3), fbm(base + 9.2, 3), fbm(base + 4.4, 3)) - 0.5;
    vec3 q = base + warp * 1.8;
    float gas = fbm(q, 5);
    float glowing = smoothstep(0.47, 0.82, gas);
    float hue = fbm(q * 1.7 + 13.0, 3);
    vec3 col = mix(vec3(0.9, 0.12, 0.42), vec3(0.06, 0.55, 0.78), smoothstep(0.35, 0.7, hue));
    col = mix(col, vec3(0.58, 0.24, 1.0), smoothstep(0.6, 0.85, fbm(q * 0.7 + 3.0, 2)) * 0.6);
    float filaments = ridged(q * 2.2, 3);
    dust = smoothstep(0.52, 0.72, fbm(q * 2.4 + 21.0, 4)) * min(richness, 1.0);
    return col * (glowing * (0.22 + 0.7 * filaments * filaments) + 0.03 * gas) * richness * (1.0 - 0.8 * dust);
}

// A spiral galaxy, small and far, its arms wound round a bright core.
vec3 spiralGalaxy(vec3 dir) {
    float seed = PlanetHeat.w;
    vec3 at = normalize(vec3(0.55 + 0.3 * cos(seed * 1.3), 0.5, 0.62 + 0.3 * sin(seed * 0.7)));
    if (dot(dir, at) < 0.99) return vec3(0.0);
    vec3 u, v;
    basis(at, u, v);
    vec2 p = vec2(dot(dir, u), dot(dir, v)) / 0.07;
    p = vec2(p.x * 0.94 + p.y * 0.34, (p.y * 0.94 - p.x * 0.34) / 0.45);
    float r = length(p);
    float angle = atan(p.y, p.x);
    float arms = pow(0.5 + 0.5 * cos(2.0 * angle - log(r + 0.05) * 4.5), 3.0);
    float disc = exp(-r * 2.6);
    float core = exp(-r * r * 60.0);
    float clumps = fbm(vec3(p * 6.0, 2.0), 3);
    return vec3(0.75, 0.7, 1.0) * arms * disc * (0.4 + 0.8 * clumps) * 0.9 + vec3(1.0, 0.92, 0.8) * core * 2.5;
}

// A ringed giant in the other sky, banded, lit from one side, with an atmosphere glowing at its rim. rgb, coverage.
vec4 giant(vec3 dir) {
    float seed = PlanetHeat.w;
    vec3 at = normalize(vec3(-0.6 + 0.3 * sin(seed), 0.42, -0.66 + 0.3 * cos(seed)));
    if (dot(dir, at) <= 0.8) return vec4(0.0);
    float size = 0.19;
    vec3 u, v;
    basis(at, u, v);
    float x = dot(dir, u) / size, y = dot(dir, v) / size;
    vec2 ring = vec2(x * 0.96 + y * 0.28, (y * 0.96 - x * 0.28) / 0.3);
    float ringRadius = length(ring);
    float ringMask = smoothstep(1.38, 1.45, ringRadius) * falloff(2.18, 2.3, ringRadius)
            * (0.55 + 0.45 * sin(ringRadius * 41.0)) * (1.0 - 0.85 * smoothstep(1.86, 1.89, ringRadius) * falloff(1.95, 1.98, ringRadius));
    vec3 lightDir = normalize(vec3(0.75, 0.25, 0.6));
    vec3 ringColor = vec3(0.82, 0.74, 0.66);
    float r2 = x * x + y * y;
    if (r2 < 1.0) {
        float z = sqrt(1.0 - r2);
        vec3 n = vec3(x, y, z);
        float latitude = n.y * 0.96 - n.x * 0.28;
        float turbulence = fbm(n * 3.0 + seed * 0.01, 3);
        float stripes = fbm(vec3(latitude * 9.0 + turbulence * 1.5, n.x * 0.8, seed * 0.01), 4);
        vec3 albedo = mix(vec3(0.74, 0.54, 0.4), vec3(0.4, 0.3, 0.56), stripes);
        albedo = mix(albedo, vec3(0.95, 0.88, 0.76), smoothstep(0.62, 0.8, stripes) * 0.55);
        float lit = smoothstep(-0.15, 0.45, dot(n, lightDir));
        // The ring's shadow across the planet's face.
        float shadowed = abs(latitude + 0.1) < 0.06 ? 0.45 : 1.0;
        vec3 colour = albedo * (0.02 + 0.95 * lit * shadowed);
        colour += vec3(0.3, 0.5, 1.0) * pow(1.0 - z, 3.0) * (0.25 + 0.6 * lit);
        if (ring.y < 0.0) colour = mix(colour, ringColor * (0.3 + 0.7 * lit), ringMask);
        return vec4(colour, 1.0);
    }
    float limb = exp(-(sqrt(r2) - 1.0) * 18.0) * 0.6;
    return vec4(ringColor * 0.8 * ringMask + vec3(0.3, 0.5, 1.0) * limb, max(ringMask * 0.8, limb * 0.5));
}

// Everything that fills the sky once it has turned to space. richness above 1 is the other universe, more vivid.
vec3 space(vec3 dir, float richness) {
    float dust;
    vec3 col = nebula(dir, richness, dust);
    vec3 pole = normalize(vec3(0.35, 0.55, 0.76));
    float across = dot(dir, pole);
    float band = exp(-across * across / 0.014);
    float lanes = smoothstep(0.38, 0.62, fbm(dir * 7.0 + 3.0, 4));
    col += vec3(0.72, 0.62, 0.86) * band * (0.06 + 0.32 * lanes) * richness * (1.0 - 0.6 * dust);
    vec3 tint;
    vec3 starLight = vec3(0.0);
    starLight += stars(dir, 70.0, 0.035, 0.22, 3.0, tint) * tint * 1.6;
    starLight += stars(dir, 160.0, 0.09, 0.26, 1.0, tint) * tint;
    starLight += stars(dir, 320.0, 0.12, 0.3, 7.0, tint) * tint * 0.6;
    starLight += stars(dir, 420.0, 0.3, 0.3, 5.0, tint) * tint * band * 0.7;
    col += starLight * (1.0 - 0.85 * dust);
    col += brightStars(dir) * (1.0 - 0.6 * dust);
    col += spiralGalaxy(dir);
    return col;
}

// ---------------------------------------------------------------- the rift

// The view into the other universe: its sky wheeling round towards a light at the end of the throat.
vec3 otherUniverse(vec3 dir) {
    vec3 n = RiftAxis.xyz;
    float straight = clamp(-dot(dir, n), 0.0, 1.0);
    float swirl = Clock.x * 0.35 + (1.0 - straight) * 6.0;
    vec3 col = space(rotateAbout(dir, n, swirl), 2.2) * 1.2;
    vec3 u, v;
    basis(n, u, v);
    float angle = atan(dot(dir, v), dot(dir, u));
    float spiral = angle * 3.0 + (1.0 - straight) * 30.0 - Clock.x * 2.0;
    float arms = pow(0.5 + 0.5 * sin(spiral), 3.0) * smoothstep(0.6, 0.995, straight);
    col += mix(vec3(0.45, 0.15, 1.0), vec3(0.25, 0.85, 1.0), 0.5 + 0.5 * sin(spiral * 0.5)) * arms * 0.9;
    col += vec3(0.9, 0.85, 1.0) * pow(straight, 260.0) * 3.0;
    return col;
}

// The accretion disc's light where the ray crosses its plane: rr the radius in disc radii, angle round it, along the
// direction the matter there is moving, and the ray's own direction. rgb emitted, a opacity.
vec4 disc(float rr, float angle, vec3 along, vec3 dir) {
    float inner = 0.36;
    if (rr < inner || rr > 1.15) return vec4(0.0);
    float spin = Clock.x * 1.4 / (rr * sqrt(rr));
    float a = angle - spin;
    float seed = PlanetHeat.w * 0.01;
    float streaks = fbm(vec3(cos(a) * 3.0, sin(a) * 3.0, rr * 24.0 + seed), 4);
    float clumps = fbm(vec3(cos(a) * 9.0, sin(a) * 9.0, rr * 7.0 + seed), 3);
    float ragged = 1.0 + (fbm(vec3(cos(angle) * 2.0, sin(angle) * 2.0, Clock.x * 0.1 + seed), 3) - 0.5) * 0.35;
    float edge = smoothstep(inner, inner + 0.05, rr) * falloff(0.72 * ragged, 1.0 * ragged, rr);
    float temperature = pow(clamp(1.0 - (rr - inner) / (1.0 - inner), 0.0, 1.0), 1.6);
    // Matter moving towards the eye is brighter and bluer; away, dimmer and redder.
    float approach = dot(along, -dir);
    float beam = pow(1.0 + 0.5 * approach, 2.0);
    vec3 col = mix(vec3(0.72, 0.18, 1.0), vec3(1.0, 0.92, 1.0), temperature);
    col *= mix(vec3(1.15, 0.8, 0.85), vec3(0.8, 0.95, 1.3), approach * 0.5 + 0.5);
    float density = edge * (0.3 + 0.7 * streaks) * (0.6 + 0.4 * clumps);
    return vec4(col * density * (0.5 + 2.0 * temperature) * beam, clamp(density * 0.9, 0.0, 1.0));
}

struct RiftView {
    vec3 light;      // what the disc and the photon ring give off along the ray
    float through;   // how much of what lies beyond still shows
    vec3 exitDir;    // the way the ray leaves, bent
    bool swallowed;  // the throat took it: it looks into the other universe
    bool near;       // the ray came near enough to be marched
};

// The ray, marched past the rift and bent round it as round a black hole. Its disc lies in the rift's plane; lensing
// wraps the disc's far side over and under the throat, and a thin ring of light hugs the throat's edge.
RiftView marchRift(vec3 dir, float sceneDistance) {
    RiftView view;
    view.light = vec3(0.0);
    view.through = 1.0;
    view.exitDir = dir;
    view.swallowed = false;
    view.near = false;
    float size = RiftAxis.w * RiftCenter.w;
    if (size < 1.0) return view;
    vec3 c = RiftCenter.xyz, n = RiftAxis.xyz;
    float horizon = size * 0.3;
    float bound = size * 1.7;
    vec2 span = sphereHits(dir, c, bound);
    if (span.y <= 0.0 || span.x > sceneDistance) return view;
    view.near = true;
    vec3 u, v;
    basis(n, u, v);
    float t = max(span.x, 0.0);
    vec3 p = rayOrigin + dir * t;
    vec3 ray = dir;
    float stepLength = bound * 2.0 / 40.0;
    float pull = horizon * 1.25;
    float nearest = FAR;
    float side = dot(p - c, n);
    for (int i = 0; i < 48; i++) {
        vec3 rel = p - c;
        float r = length(rel);
        nearest = min(nearest, r);
        if (r < horizon) {
            view.swallowed = true;
            break;
        }
        // Finer steps close in, where the bending is strongest.
        float h = stepLength * clamp(r / (horizon * 3.0), 0.25, 1.0);
        ray = normalize(ray - rel / r * (pull * h / (r * r)));
        vec3 next = p + ray * h;
        float nextSide = dot(next - c, n);
        if ((side > 0.0) != (nextSide > 0.0)) {
            vec3 crossing = p + (next - p) * (side / (side - nextSide));
            vec3 local = crossing - c;
            float rr = length(local) / size;
            float angle = atan(dot(local, v), dot(local, u));
            vec4 glow = disc(rr, angle, normalize(cross(n, local)), ray);
            view.light += view.through * glow.rgb;
            view.through *= 1.0 - glow.a;
        }
        side = nextSide;
        p = next;
        t += h;
        if (t > sceneDistance || view.through < 0.01) break;
        if (r > bound && dot(rel, ray) > 0.0) break;
    }
    // The photon ring: light that skims the throat's edge, a crisp bright line round the dark.
    float photon = exp(-pow((nearest - horizon * 1.18) / (horizon * 0.05), 2.0));
    if (!view.swallowed) view.light += view.through * vec3(0.95, 0.85, 1.0) * photon * 1.4;
    view.exitDir = ray;
    return view;
}

// Cracks of light running out across the sky from the rift, as if it were a pane of glass. plane: blocks from the
// centre, on the rift's plane; radius: the disc's size, in blocks.
float skyCracks(vec2 plane, float radius) {
    float r = length(plane);
    if (r < radius * 0.75) return 0.0;
    float angle = atan(plane.y, plane.x);
    float seed = PlanetHeat.w;
    float grown = smoothstep(0.35, 1.0, RiftCenter.w);
    float glow = 0.0;
    for (int i = 0; i < 11; i++) {
        float fi = float(i);
        float reach = radius * (1.6 + 2.2 * hash13(vec3(fi, seed, 2.0))) * grown;
        if (r > reach) continue;
        float base = fi * (TAU / 11.0) + hash13(vec3(fi, seed, 1.0)) * 0.5;
        float beyond = (r - radius) / radius;
        float wander = (fbm(vec3(r * 0.05, fi * 3.1, seed * 0.01), 3) - 0.5) * 1.2 * max(beyond, 0.0);
        float delta = mod(angle - base - wander + PI, TAU) - PI;
        float across = abs(delta) * r;
        float taper = 1.0 - max(r - radius, 0.0) / max(reach - radius, 0.001);
        float width = 0.08 + 0.6 * taper;
        glow += falloff(0.0, width, across) * (0.5 + 0.8 * taper) + exp(-across / (width * 6.0)) * 0.18 * taper;
        float forkAt = radius + (reach - radius) * 0.4;
        if (r > forkAt) {
            float turn = (hash13(vec3(fi, seed, 3.0)) > 0.5 ? 1.0 : -1.0) * (r - forkAt) / r * 1.1;
            float forkDelta = mod(delta - turn + PI, TAU) - PI;
            glow += falloff(0.0, width * 0.6, abs(forkDelta) * r) * 0.6;
        }
    }
    return glow;
}

// ---------------------------------------------------------------- the planet

// Height of the planet's surface in its own frame, 0 to 1: domain-warped continents. Fewer octaves are enough for
// the slope that gives the relief.
float planetHeight(vec3 q, int octaves) {
    vec3 warp = vec3(fbm(q * 1.7 + 1.3, 2), fbm(q * 1.7 + 7.1, 2), fbm(q * 1.7 + 3.7, 2)) - 0.5;
    return fbm(q * 2.3 + warp * 1.6 + PlanetHeat.w * 0.0131, octaves);
}

// The planet's face at unit normal n (world frame), seen along dir. A broken alien world: glassy obsidian seas, basalt
// and ash continents in relief, amethyst highlands glowing faintly on the night side, drifting clouds and their
// shadows, and molten fissures that branch and widen as it heats, white-hot on the side it falls towards.
vec3 planetSurface(vec3 n, vec3 dir) {
    float heat = PlanetHeat.x, split = PlanetHeat.y;
    vec3 axis = PlanetSpin.xyz;
    float turn = PlanetSpin.w;
    vec3 q = rotateAbout(n, axis, -turn);
    vec3 light = rotateAbout(starLight(), axis, -turn);
    vec3 view = rotateAbout(-dir, axis, -turn);

    // Relief, from the height's slope across the surface.
    vec3 t1, t2;
    basis(q, t1, t2);
    float e = 0.012;
    float height = planetHeight(q, 5);
    float base = planetHeight(q, 3);
    float slopeA = planetHeight(normalize(q + t1 * e), 3) - base;
    float slopeB = planetHeight(normalize(q + t2 * e), 3) - base;
    float land = smoothstep(0.43, 0.47, height);
    vec3 normal = normalize(q - (t1 * slopeA + t2 * slopeB) / e * 0.45 * land);

    vec3 sea = vec3(0.03, 0.015, 0.06);
    vec3 basalt = vec3(0.17, 0.14, 0.18), ash = vec3(0.36, 0.32, 0.34), crystal = vec3(0.55, 0.3, 1.0);
    float detail = fbm(q * 11.0 + 3.1, 3);
    vec3 albedo = mix(sea, basalt, land);
    albedo = mix(albedo, ash, smoothstep(0.52, 0.7, height) * (0.6 + 0.4 * detail));
    float crystals = smoothstep(0.64, 0.7, height) * step(0.5, detail);
    albedo = mix(albedo, crystal * 0.6, crystals);

    float diffuse = max(dot(normal, light), 0.0);
    float day = smoothstep(-0.05, 0.2, dot(q, light));
    // Clouds drift faster than the ground turns, and shade it.
    vec3 drift = rotateAbout(q, axis, -Clock.x * 0.04);
    float cloud = smoothstep(0.56, 0.74, fbm(drift * 3.4 + 9.0, 4)) * (1.0 - split * 0.8);
    float shadow = 1.0 - 0.55 * smoothstep(0.56, 0.74, fbm(rotateAbout(q - light * 0.03, axis, -Clock.x * 0.04) * 3.4 + 9.0, 4));
    vec3 colour = albedo * (0.025 + 1.15 * diffuse * shadow * day);
    // Glassy seas catch the star.
    vec3 halfway = normalize(light + view);
    colour += vec3(1.0, 0.9, 0.95) * pow(max(dot(normal, halfway), 0.0), 90.0) * (1.0 - land) * day * 1.4;
    colour = mix(colour, vec3(0.9, 0.86, 1.0) * (0.04 + 1.0 * diffuse), cloud * 0.85);
    // On the night side, the highlands' crystal veins glow.
    float veins = smoothstep(0.82, 0.92, ridged(q * 7.0, 3)) * smoothstep(0.55, 0.68, height);
    colour += vec3(0.5, 0.3, 1.0) * veins * (1.0 - day) * 0.9;

    // Fissures: a branching web of cracks between the plates, opening as it heats.
    vec3 warped = q * 3.0 + (vec3(fbm(q * 2.0, 2), fbm(q * 2.0 + 5.0, 2), fbm(q * 2.0 + 9.0, 2)) - 0.5) * 1.4;
    float web = ridged(warped * 1.4 + PlanetHeat.w * 0.017, 4);
    float width = 0.05 + 0.2 * split;
    float molten = smoothstep(1.0 - width, 1.0 - width * 0.3, web) * (0.25 + 0.75 * smoothstep(0.0, 0.25, split + 0.12));
    float core = smoothstep(1.0 - width * 0.35, 1.0, web);
    vec3 magma = mix(vec3(1.0, 0.28, 0.03), vec3(1.0, 0.85, 0.5), core);
    colour = mix(colour, magma * (1.1 + 2.0 * split), molten * (1.0 - cloud * 0.6));

    // The rim of its air, violet by day and orange where day turns to night; ablaze on its leading face.
    float facing = max(dot(normal, view), 0.0);
    float rim = pow(1.0 - facing, 3.0);
    vec3 air = mix(vec3(1.0, 0.45, 0.2), vec3(0.45, 0.35, 1.0), day);
    colour += air * rim * (0.25 + 0.9 * max(dot(q, light) + 0.25, 0.0));
    float lead = max(dot(n, RiftAxis.xyz), 0.0);
    colour = mix(colour, vec3(1.0, 0.5, 0.18) * 1.5, heat * pow(lead, 2.5) * 0.6);
    colour += vec3(1.0, 0.85, 0.6) * heat * heat * pow(lead, 7.0) * 1.1;
    return colour;
}

// The glow of the planet's atmosphere round its limb, for a ray that passes within reach of it.
vec3 atmosphere(vec3 dir, float limit, bool portal, float portalT) {
    float radius = PlanetCenter.w;
    if (radius <= 0.0) return vec3(0.0);
    float along = dot(PlanetCenter.xyz - rayOrigin, dir);
    vec3 closest = rayOrigin + dir * along;
    float miss = length(closest - PlanetCenter.xyz);
    float outer = radius * 1.09;
    if (along <= 0.0 || miss > outer || along - radius > limit) return vec3(0.0);
    if (riftSide(closest) < 0.0 && !(portal && portalT < along)) return vec3(0.0);
    float chord = sqrt(max(outer * outer - miss * miss, 0.0)) - (miss < radius ? sqrt(radius * radius - miss * miss) : 0.0);
    float lit = max(dot(normalize(closest - PlanetCenter.xyz), starLight()) + 0.3, 0.0);
    vec3 air = mix(vec3(1.0, 0.45, 0.15), vec3(0.42, 0.4, 1.0), smoothstep(0.1, 0.6, lit));
    air = mix(air, vec3(1.0, 0.5, 0.2), PlanetHeat.x * 0.7);
    return air * chord / radius * (0.6 + 1.6 * lit + 2.0 * PlanetHeat.x);
}

// The planet's rings, tilted with its spin, in the planet's shadow where it falls on them: rgb, and how opaque.
vec4 planetRing(vec3 dir, out float t) {
    t = -1.0;
    float radius = PlanetCenter.w;
    vec3 n = PlanetSpin.xyz;
    float denom = dot(dir, n);
    if (radius <= 0.0 || abs(denom) < 1.0e-4) return vec4(0.0);
    t = dot(PlanetCenter.xyz - rayOrigin, n) / denom;
    if (t <= 0.0) return vec4(0.0);
    vec3 rel = rayOrigin + dir * t - PlanetCenter.xyz;
    float r = length(rel) / radius;
    if (r < 1.45 || r > 2.35) return vec4(0.0);
    float bands = fbm(vec3(r * 14.0, PlanetHeat.w * 0.01, 0.0), 3);
    float fine = 0.75 + 0.25 * sin(r * 160.0);
    float alpha = smoothstep(1.45, 1.52, r) * falloff(2.22, 2.35, r) * smoothstep(0.3, 0.6, bands)
            * (1.0 - 0.7 * smoothstep(1.83, 1.86, r) * falloff(1.89, 1.92, r)) * (1.0 - PlanetHeat.y * 0.5) * fine;
    vec3 light = starLight();
    float b = dot(rel, light);
    float shade = (b < 0.0 && length(rel - light * b) < radius) ? 0.15 : 1.0;
    vec3 colour = mix(vec3(0.55, 0.35, 0.95), vec3(0.95, 0.9, 1.0), bands) * (0.55 + 0.45 * bands) * shade;
    return vec4(colour, alpha * 0.85);
}

// The wake of plasma streaming back the way the planet came, twisting as it trails.
vec3 wake(vec3 dir, float sceneDistance, bool portal) {
    float radius = PlanetCenter.w;
    float heat = PlanetHeat.x;
    if (radius <= 0.0 || heat <= 0.02) return vec3(0.0);
    vec3 c = PlanetCenter.xyz;
    vec3 h = RiftAxis.xyz;
    vec3 w0 = rayOrigin - c;
    float b = dot(dir, h);
    float d = dot(dir, w0), e = dot(h, w0);
    float denom = max(1.0 - b * b, 1.0e-3);
    float tRay = (b * e - d) / denom;
    float sAxis = (e - b * d) / denom;
    if (tRay <= 0.0 || sAxis >= 0.0) return vec3(0.0);
    vec3 onRay = rayOrigin + dir * tRay;
    float visible = smoothstep(0.0, radius * 0.5, sceneDistance - tRay)
            * max(smoothstep(-radius * 0.3, radius * 0.3, riftSide(onRay)), portal ? 1.0 : 0.0);
    if (visible <= 0.0) return vec3(0.0);
    vec3 rel = onRay - (c + h * sAxis);
    float perp = length(rel);
    float behind = -sAxis / radius;
    float width = radius * (0.85 + 0.25 * behind);
    vec3 u, v;
    basis(h, u, v);
    float around = atan(dot(rel, v), dot(rel, u)) + behind * 0.6 - Clock.x * 2.0;
    float streaks = 0.35 + 0.65 * fbm(vec3(cos(around) * 2.5, sin(around) * 2.5, behind * 0.7 - Clock.x * 4.5), 4);
    float tail = exp(-pow(perp / width, 2.0) * 1.6) * exp(-behind / (1.5 + 6.0 * heat)) * streaks;
    vec3 hot = mix(vec3(1.0, 0.88, 0.6), vec3(1.0, 0.36, 0.08), smoothstep(0.0, 2.0, behind));
    vec3 colour = mix(hot, vec3(0.75, 0.2, 1.0), smoothstep(2.0, 7.0, behind));
    return colour * tail * heat * 1.5 * visible;
}

// ---------------------------------------------------------------- the impact

// The shock front, seen as a dome of condensing air: misty, brightest where seen edge on, bending light as it passes.
vec2 shockDome(vec3 dir, float sceneDistance, out vec3 light) {
    light = vec3(0.0);
    float radius = Shock.w;
    if (radius <= 0.0 || ShockState.x <= 0.0) return vec2(0.0);
    vec2 hits = sphereHits(dir, Shock.xyz, radius);
    float t = hits.x > 0.0 ? hits.x : hits.y;
    if (t <= 0.0 || t > sceneDistance) return vec2(0.0);
    vec3 hit = rayOrigin + dir * t;
    if (hit.y < Shock.y - 2.0) return vec2(0.0);
    vec3 normal = (hit - Shock.xyz) / radius;
    float edge = pow(1.0 - abs(dot(dir, normal)), 3.0);
    float mist = 0.55 + 0.45 * fbm(normal * 6.0 + vec3(0.0, -Clock.x * 0.5, 0.0), 3);
    float height = clamp((hit.y - Shock.y) / radius, 0.0, 1.0);
    light = mix(vec3(1.0, 0.68, 0.38), vec3(0.85, 0.92, 1.0), height) * edge * mist * ShockState.x * 1.6;
    vec3 viewNormal = (ViewMat * vec4(normal, 0.0)).xyz;
    vec2 screen = length(viewNormal.xy) > 1.0e-4 ? normalize(viewNormal.xy) : vec2(0.0);
    return screen * edge * ShockState.x * 0.03 * (0.4 + 0.6 * Clock.z);
}

// ---------------------------------------------------------------- light on the ground

// One light's share at a surface point: how squarely the face turns to it, fading to nothing at reach.
float lightShare(vec3 point, vec3 normal, vec3 at, float reach) {
    vec3 to = at - point;
    float d = length(to);
    float fade = max(0.0, 1.0 - d / reach);
    return max(dot(normal, to / max(d, 1.0e-3)), 0.0) * fade * fade;
}

// The coloured light falling on the world: the burning planet as it nears, the rift's violet glow from high above,
// the impact's flash, and the crater's molten heart.
vec3 groundLight(vec3 point, vec3 normal) {
    vec3 light = vec3(0.0);
    if (Glow.x > 0.0 && PlanetCenter.w > 0.0) {
        light += vec3(1.0, 0.45, 0.12) * lightShare(point, normal, PlanetCenter.xyz, PlanetCenter.w * 10.0) * Glow.x;
    }
    if (Glow.y > 0.0) light += vec3(0.45, 0.25, 1.0) * max(dot(normal, normalize(RiftCenter.xyz - point)), 0.0) * Glow.y;
    if (Glow.z > 0.0) light += vec3(1.0, 0.8, 0.5) * lightShare(point, normal, Shock.xyz + vec3(0.0, 12.0, 0.0), 150.0) * Glow.z;
    if (Glow.w > 0.0) light += vec3(1.0, 0.35, 0.08) * lightShare(point, normal, Shock.xyz - vec3(0.0, 8.0, 0.0), 70.0) * Glow.w;
    return light;
}

vec2 heatHaze(vec2 uv, vec3 dir, float sceneDistance) {
    float strength = Lens.w;
    if (strength <= 0.0) return vec2(0.0);
    float mask = 0.0;
    if (PlanetCenter.w > 0.0) {
        float along = dot(PlanetCenter.xyz - rayOrigin, dir);
        float miss = length(rayOrigin + dir * along - PlanetCenter.xyz);
        if (along > 0.0) mask = exp(-max(miss - PlanetCenter.w, 0.0) / PlanetCenter.w);
    }
    if (ShockState.w > 0.0 && sceneDistance < FAR) {
        vec3 point = rayOrigin + dir * sceneDistance;
        mask = max(mask, ShockState.w * falloff(15.0, 55.0, length((point - Shock.xyz).xz)));
    }
    vec2 wobble = vec2(valueNoise(vec3(uv * 55.0, Clock.x * 3.0)), valueNoise(vec3(uv * 55.0 + 17.0, Clock.x * 3.0 + 5.0))) - 0.5;
    return wobble * 0.007 * strength * mask;
}

vec4 encodeRGBM(vec3 c) {
    c = max(c, vec3(0.0));
    float m = clamp(max(max(c.r, c.g), max(c.b, 1.0e-4)) / 8.0, 1.0 / 255.0, 1.0);
    m = ceil(m * 255.0) / 255.0;
    return vec4(c / (m * 8.0), m);
}

// ---------------------------------------------------------------- composition

void main() {
    vec2 uv = texCoord;
    vec3 dir;
    float sceneDistance = setupRay(uv, texture(SceneDepth, uv).r, dir);
    bool sky = sceneDistance >= FAR;
    // Each surface's face, from how its position changes across neighbouring pixels (outside any branch, as
    // derivatives must be).
    vec3 scenePoint = rayOrigin + dir * min(sceneDistance, 4096.0);
    vec3 sceneNormal = normalize(cross(dFdx(scenePoint), dFdy(scenePoint)) + vec3(0.0, 1.0e-6, 0.0));
    if (dot(sceneNormal, dir) > 0.0) sceneNormal = -sceneNormal;

    // The rift, marched: the bent ray, the disc's light, and whether the throat swallowed it.
    RiftView rift = marchRift(dir, sceneDistance);

    // The world: rippled by the shock front, shimmering in the heat, and lit by what burns above it.
    vec3 domeLight;
    vec2 offset = shockDome(dir, sceneDistance, domeLight) + heatHaze(uv, dir, sceneDistance);
    vec2 sampleUv = clamp(uv + offset, vec2(0.001), vec2(0.999));
    vec3 color = texture(InputColor, sampleUv).rgb;
    if (!sky) {
        vec3 lit = groundLight(scenePoint, sceneNormal);
        color += color * lit * 1.2 + lit * 0.02;
    }

    // The sky, through whatever bending the rift gives it: the frame's own sky where the bent ray lands on sky, and
    // the stars of space along the bent ray.
    if (sky || texture(SceneDepth, sampleUv).r >= 0.99999) {
        bool bent = rift.near && sky;
        vec3 skyDir = bent ? rift.exitDir : normalize(unproject(sampleUv, 1.0) - rayOrigin);
        if (bent) {
            vec3 landed = project(rayOrigin + skyDir * 400.0);
            vec2 at = clamp(landed.xy, 0.001, 0.999);
            if (landed.z > 0.0 && texture(SceneDepth, at).r >= 0.99999) color = texture(InputColor, at).rgb;
        }
        if (Clock.y > 0.0) {
            vec4 world = giant(skyDir);
            color += space(skyDir, 1.0) * Clock.y;
            color = mix(color, world.rgb, world.a * Clock.y);
        }
    }

    // The rift itself: the other universe in its throat, its disc and ring over whatever lies beyond, and the sky
    // cracking round it.
    bool portal = rift.swallowed;
    if (portal) color = otherUniverse(dir);
    color = color * rift.through + rift.light;
    if (RiftCenter.w > 0.0) {
        float denom = dot(dir, RiftAxis.xyz);
        if (abs(denom) > 1.0e-4) {
            float t = dot(RiftCenter.xyz - rayOrigin, RiftAxis.xyz) / denom;
            if (t > 0.0 && t < sceneDistance) {
                vec3 u, v;
                basis(RiftAxis.xyz, u, v);
                vec3 hit = rayOrigin + dir * t - RiftCenter.xyz;
                vec2 plane = vec2(dot(hit, u), dot(hit, v));
                float size = RiftAxis.w * RiftCenter.w;
                color += vec3(0.85, 0.72, 1.0) * skyCracks(plane, size) * smoothstep(0.2, 0.8, RiftCenter.w) * 1.4;
                float r = length(plane) / size;
                float angle = atan(plane.y, plane.x);
                float stream = pow(0.5 + 0.5 * sin(angle * 2.0 - log(max(r, 1.0)) * 5.0 + Clock.x * 1.4), 8.0);
                color += vec3(0.5, 0.3, 1.0) * stream * exp(-max(r - 1.0, 0.0) * 1.1) * step(1.0, r) * 0.3 * RiftCenter.w;
            }
        }
    }

    // The planet, its rings, its air and the fire of its entry. Whatever of it is still beyond the rift shows only
    // through the throat.
    float portalT = portal ? length(RiftCenter.xyz - rayOrigin) : FAR;
    vec2 body = sphereHits(dir, PlanetCenter.xyz, max(PlanetCenter.w, 1.0e-3));
    float planetT = PlanetCenter.w > 0.0 && body.x > 0.0 ? body.x : -1.0;
    bool planetShown = false;
    if (planetT > 0.0) {
        vec3 point = rayOrigin + dir * planetT;
        planetShown = riftSide(point) >= 0.0 ? planetT < sceneDistance : portal;
        if (planetShown) color = planetSurface((point - PlanetCenter.xyz) / PlanetCenter.w, dir);
    }
    color += atmosphere(dir, planetShown ? planetT : sceneDistance, portal, portalT);
    float ringT;
    vec4 ring = planetRing(dir, ringT);
    if (ring.a > 0.0 && ringT < sceneDistance && (!planetShown || ringT < planetT)) {
        vec3 point = rayOrigin + dir * ringT;
        if (riftSide(point) >= 0.0 || portal) color = mix(color, ring.rgb, ring.a);
    }
    color += wake(dir, sceneDistance, portal);

    // The volumes: the planet's burning sheath, and the fireball, cloud and surge after the impact.
    vec4 volume = texture(Volume, uv);
    color = color * (1.0 - volume.a) + volume.rgb * 4.0;

    // The impact: the shock dome, and a ring of light running over the ground beneath it.
    color += domeLight;
    if (Shock.w > 0.0 && !sky) {
        float off = length(scenePoint - Shock.xyz) - Shock.w;
        color += vec3(1.0, 0.68, 0.4) * exp(-off * off / 8.0) * ShockState.x * 0.8;
    }
    fragColor = encodeRGBM(color);
}
