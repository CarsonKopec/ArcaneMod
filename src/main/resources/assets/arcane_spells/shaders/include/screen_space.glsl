#version 330

// Shared by the screen passes (PlanetfallShader's and SpaceTimeShader's): hashing, the ray through each pixel, and
// the geometry they draw with. Every position is relative to the camera. The shader importing this declares ProjMat
// (minecraft:projection.glsl) and its own uniform block holding ViewMat first.

const float FAR = 1.0e6;
const float PI = 3.14159265;
const float TAU = 6.2831853;

mat4 inverseViewProj;
vec3 rayOrigin;

// ---------------------------------------------------------------- hashing

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// ---------------------------------------------------------------- geometry

// 1 up to near, easing down to 0 at far: smoothstep the other way round, which GLSL leaves undefined.
float falloff(float near, float far, float x) {
    return 1.0 - smoothstep(near, far, x);
}

float luminance(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 rotateAbout(vec3 v, vec3 axis, float angle) {
    float c = cos(angle), s = sin(angle);
    return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

void basis(vec3 n, out vec3 u, out vec3 v) {
    u = normalize(cross(abs(n.y) < 0.99 ? vec3(0, 1, 0) : vec3(1, 0, 0), n));
    v = cross(n, u);
}

vec3 unproject(vec2 uv, float depth) {
    vec4 p = inverseViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

// Where p lands on screen; z is 0 if it is behind the camera.
vec3 project(vec3 p) {
    vec4 clip = ProjMat * (ViewMat * vec4(p, 1.0));
    if (clip.w <= 1.0e-3) return vec3(0.5, 0.5, 0.0);
    return vec3(clip.xy / clip.w * 0.5 + 0.5, 1.0);
}

// Sets up the ray through uv; returns how far along it the world's surface is (FAR for the sky).
float setupRay(vec2 uv, float depth, out vec3 dir) {
    inverseViewProj = inverse(ProjMat * ViewMat);
    rayOrigin = unproject(uv, 0.0);
    dir = normalize(unproject(uv, 1.0) - rayOrigin);
    return depth >= 0.99999 ? FAR : length(unproject(uv, depth) - rayOrigin);
}

// Where the ray enters and leaves a sphere, or (-1, -1).
vec2 sphereHits(vec3 dir, vec3 center, float radius) {
    vec3 oc = rayOrigin - center;
    float b = dot(oc, dir);
    float c = dot(oc, oc) - radius * radius;
    float h = b * b - c;
    if (h < 0.0) return vec2(-1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// A blackbody-like ramp: deep red embers through orange and yellow to white heat, 0 to 1.
vec3 heatColor(float t) {
    t = clamp(t, 0.0, 1.0);
    vec3 c = mix(vec3(0.35, 0.02, 0.0), vec3(1.0, 0.25, 0.02), smoothstep(0.0, 0.35, t));
    c = mix(c, vec3(1.0, 0.62, 0.12), smoothstep(0.3, 0.65, t));
    return mix(c, vec3(1.0, 0.95, 0.82), smoothstep(0.65, 1.0, t));
}
