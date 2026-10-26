#version 330

// Shared by Planetfall's screen passes (see PlanetfallShader): their view, noise, and the rift and planet's frames.
// Every position is relative to the camera. Anything on the far side of the rift's plane is in the other universe,
// seen only through the rift.

#moj_import <minecraft:projection.glsl>

layout(std140) uniform PlanetfallView {
    mat4 ViewMat;
    vec4 Clock;        // x: seconds; y: how far the sky is to space; z: Screen Effect Scale; w: 1 if flashes are allowed
    vec4 RiftCenter;   // xyz: centre; w: how open, 0 to 1
    vec4 RiftAxis;     // xyz: unit facing, out of the rift towards the mark; w: radius when fully open
    vec4 PlanetCenter; // xyz: centre; w: radius, 0 for none
    vec4 PlanetSpin;   // xyz: spin axis; w: angle turned
    vec4 PlanetHeat;   // x: entry heat, 0 to 1; y: how far the crust has split, 0 to 1; w: seed
    vec4 Shock;        // xyz: impact point; w: shock front radius, 0 for none
    vec4 ShockState;   // x: dome brightness; y: flash; z: impact frame; w: crater heat
    vec4 Lens;         // x: vignette; y: chromatic aberration; z: rush towards the planet; w: heat haze
    vec4 Grade;        // rgb: colour grade; a: how much
    vec4 Glow;         // ground lit by x: the planet; y: the rift; z: the impact's flash; w: the crater
    vec4 Blast;        // x: ticks since the impact, below 0 before it; y: fire; z: smoke; w: dust
    vec4 Post;         // x: bloom; y: lens streaks; z: film grain; w: the impact's scale (see impactScale)
};

// How many times the size the impact's fireball, cloud and glow were shaped for (a 72-block blast) it is drawn at,
// to match the server's blast.
float impactScale() {
    return Post.w > 0.0 ? Post.w : 1.0;
}

#moj_import <arcane_spells:screen_space.glsl>

// Random values for valueNoise; bound wherever noise is used.
uniform sampler2D NoiseTile;

// ---------------------------------------------------------------- noise

// Smooth 3D value noise, 0 to 1, from one lookup in the noise tile (planetfall_noise.fsh): each z slice is the tile
// shifted by (37, 17), the tile's green holds the next slice, and the filtering does the rest. Far cheaper than
// hashing the eight corners of a cell.
float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = i.xy + vec2(37.0, 17.0) * i.z + f.xy;
    vec2 slices = textureLod(NoiseTile, (uv + 0.5) / 256.0, 0.0).rg;
    return mix(slices.x, slices.y, f.z);
}

// Fractal noise of a fixed number of octaves. Each count is its own function with a constant loop, which every
// driver can unroll; a loop that breaks out on a count it is given has hung integrated graphics.
float fbm2(vec3 p) {
    return (valueNoise(p) * 0.5 + valueNoise(p * 2.03 + vec3(17.1, 9.2, 4.7)) * 0.25) / 0.75;
}

float fbm3(vec3 p) {
    float sum = 0.0, amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amplitude * valueNoise(p);
        p = p * 2.03 + vec3(17.1, 9.2, 4.7);
        amplitude *= 0.5;
    }
    return sum / 0.875;
}

float fbm4(vec3 p) {
    float sum = 0.0, amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        sum += amplitude * valueNoise(p);
        p = p * 2.03 + vec3(17.1, 9.2, 4.7);
        amplitude *= 0.5;
    }
    return sum / 0.9375;
}

float fbm5(vec3 p) {
    float sum = 0.0, amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amplitude * valueNoise(p);
        p = p * 2.03 + vec3(17.1, 9.2, 4.7);
        amplitude *= 0.5;
    }
    return sum / 0.96875;
}

// Fractal noise, 0 to 1, of 2 to 5 octaves. octaves is always a constant where it is called, so this resolves at
// compile time to one of the fixed versions above.
float fbm(vec3 p, int octaves) {
    if (octaves <= 2) return fbm2(p);
    if (octaves == 3) return fbm3(p);
    if (octaves == 4) return fbm4(p);
    return fbm5(p);
}

float ridgedOctave(inout vec3 p, inout float amplitude) {
    float n = 1.0 - abs(valueNoise(p) * 2.0 - 1.0);
    float value = amplitude * n * n;
    p = p * 2.1 + vec3(5.3, 1.7, 9.1);
    amplitude *= 0.5;
    return value;
}

// Sharp-crested noise: bright, branching lines where the noise crosses its middle, like cracks or veins. 3 or 4
// octaves, unrolled like fbm.
float ridged(vec3 p, int octaves) {
    float amplitude = 0.5;
    float sum = ridgedOctave(p, amplitude) + ridgedOctave(p, amplitude) + ridgedOctave(p, amplitude);
    if (octaves <= 3) return sum / 0.875;
    return (sum + ridgedOctave(p, amplitude)) / 0.9375;
}

// ---------------------------------------------------------------- the rift and the planet

// Which side of the rift a point is: below 0 is the far side, the other universe.
float riftSide(vec3 p) {
    return dot(p - RiftCenter.xyz, RiftAxis.xyz);
}

// The light the planet is lit by: a distant star off to the side of its fall, so it shows a lit face and a dark one.
vec3 starLight() {
    vec3 across = normalize(cross(RiftAxis.xyz, vec3(0.0, 1.0, 0.0)) + vec3(1.0e-4, 0.0, 0.0));
    return normalize(across * 0.85 - RiftAxis.xyz * 0.25 + vec3(0.0, 0.35, 0.0));
}
