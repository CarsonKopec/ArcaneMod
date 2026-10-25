#version 330

// Singularity's black holes and Chrono Field's stopped time, drawn into the frame (see SpaceTimeShader). A black hole
// bends light as a real one does: each ray is traced past it, the frame is read again wherever the bent ray lands,
// and so the world behind the hole wraps round it. Its accretion disc glows with the heat of its orbit, the side
// coming towards the viewer brighter and bluer. Inside a Chrono Field the world loses its colour, and the hands of a
// stopped clock lie on the ground. Out: the frame.

#moj_import <minecraft:projection.glsl>

layout(std140) uniform SpaceTimeView {
    mat4 ViewMat;
    vec4 Clock;          // x: seconds; z: Screen Effect Scale
    vec4 HoleCenter[4];  // xyz: centre; w: the event horizon's radius, 0 for none
    vec4 HoleDisc[4];    // xyz: the disc's axis, which it turns round anticlockwise; w: its outer radius
    vec4 HoleState[4];   // x: the disc's brightness, 0 to 1; y: how far its inner edge has turned, in radians; w: seed
    vec4 FieldCenter[4]; // xyz: centre; w: radius, 0 for none
    vec4 FieldState[4];  // x: how much colour has drained, 0 to 1; y: the returning colour's front, 0 for none;
                         // z: the second hand, radians clockwise from twelve; w: the hands' brightness, 0 to 1
};

#moj_import <arcane_spells:screen_space.glsl>

uniform sampler2D InputColor;
uniform sampler2D SceneDepth;

in vec2 texCoord;
out vec4 fragColor;

// Lengths in a black hole's own frame are in horizon radii. Light circles it at 1.5 and nothing orbits inside 3.
const float PHOTON_SPHERE = 1.5;
const float DISC_INNER = 3.0;

// ---------------------------------------------------------------- the accretion disc

float hashNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x);
    float b = mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x);
    float c = mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x);
    float d = mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

// Three octaves, in a loop of fixed length (see fbm in planetfall_common.glsl for why).
float discNoise(vec3 p) {
    float sum = 0.0, amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amplitude * hashNoise(p);
        p = p * 2.07 + vec3(11.3, 4.1, 7.7);
        amplitude *= 0.5;
    }
    return sum / 0.875;
}

// How hot the disc is r horizon radii out, 0 to 1: a thin disc's profile, peaking just outside its inner edge. (The
// true profile is shallow; it is steepened here so the disc runs from white heat through gold to deep orange.)
float discHeat(float r) {
    float x = r / DISC_INNER;
    return pow(pow(max(1.0 - inversesqrt(x), 0.0) / (x * x * x), 0.25) / 0.4879, 1.4);
}

// Colour of light at a seen heat: embers to white, and on to blue-white where it is blueshifted past white.
vec3 discColor(float heat) {
    return mix(heatColor(heat), vec3(0.72, 0.84, 1.0), smoothstep(1.0, 1.6, heat));
}

// How much of the disc there is r horizon radii out, 0 to 1: a sharp inner edge, thinning away outwards.
float discEdges(float r, float outer) {
    return smoothstep(DISC_INNER * 0.9, DISC_INNER * 1.1, r) * falloff(outer * 0.6, outer, r);
}

// The shift of the disc's light at q seen along ray v, below 1 redder, above 1 bluer. The gas orbits at a good
// fraction of light's speed: where it comes towards the viewer its light is blueshifted and beamed brighter, where
// it goes away, redshifted and dimmed. Climbing out of the hole's well reddens it too, most near the inner edge.
float discShift(vec3 q, float r, vec3 v, vec3 axis) {
    vec3 orbit = normalize(cross(axis, q));
    float beta = min(sqrt(0.5 / max(r - 1.0, 0.5)), 0.7);
    float gamma = inversesqrt(1.0 - beta * beta);
    float towards = dot(orbit, -normalize(v));
    return sqrt(1.0 - 1.0 / r) / (gamma * (1.0 - beta * towards));
}

// The light the disc gives off r horizon radii out, its light shifted by shift. Beaming goes as the shift cubed;
// softened a little, as a camera's exposure would.
vec3 discGlow(float r, float shift) {
    float heat = discHeat(r) * shift;
    return discColor(heat) * pow(shift, 3.0) * (0.6 + 3.5 * heat * heat);
}

// The disc's light and opacity where the ray crosses its plane at q (in horizon radii from the centre), the ray
// heading along v: its streaks and clumps of gas over the glow.
vec4 discLight(int i, vec3 q, vec3 v, vec3 axis, float outer) {
    float r = length(q);
    if (r < DISC_INNER * 0.9 || r > outer) return vec4(0.0);
    vec3 u, w;
    basis(axis, u, w);
    float angle = atan(dot(q, w), dot(q, u));
    // Each ring turns at its own orbital rate, so the inner disc outruns the outer and winds its streaks into spirals.
    float turned = angle - HoleState[i].y * pow(DISC_INNER / r, 1.5);
    vec3 p = vec3(cos(turned) * 3.0, sin(turned) * 3.0, log(r) * 9.0) + HoleState[i].w;
    float streaks = discNoise(p);
    float clumps = discNoise(p * 2.3 + 5.0);
    float density = (0.5 + 0.5 * smoothstep(0.2, 0.85, streaks)) * (0.75 + 0.5 * clumps) * discEdges(r, outer);
    float alpha = clamp(density * 1.4, 0.0, 0.97) * HoleState[i].x;
    return vec4(discGlow(r, discShift(q, r, v, axis)) * density * HoleState[i].x * 2.6, alpha);
}

// ---------------------------------------------------------------- the black hole

struct Bend {
    vec3 light;      // the disc's light gathered along the way
    float through;   // how much of what lies beyond still shows
    bool swallowed;  // the ray fell in: nothing beyond shows
    bool landed;     // it reached the world while still being bent
    vec3 end;        // where the march stopped
    vec3 exitDir;    // which way the ray was heading then
    float travelled; // how far it had come, in blocks
};

// Radius, in blocks, of the sphere hole i bends light within.
float reach(int i) {
    return max(HoleDisc[i].w * 1.3, HoleCenter[i].w * 12.0);
}

// The ray through this pixel, traced past hole i. Light follows a Schwarzschild black hole's null geodesics,
// integrated in the hole's own units as a particle pulled by -1.5 h^2 x / r^5 (h: the ray's angular momentum),
// which bends it exactly as much as the hole would; the bending is eased off towards the edge of the hole's
// reach, so nothing shows where tracing starts.
Bend marchHole(int i, vec3 dir, float sceneDistance) {
    Bend bend;
    bend.light = vec3(0.0);
    bend.through = 1.0;
    bend.swallowed = false;
    bend.landed = false;
    bend.exitDir = dir;
    float horizon = HoleCenter[i].w;
    vec3 c = HoleCenter[i].xyz;
    vec3 axis = HoleDisc[i].xyz;
    float bound = reach(i) / horizon;
    float outer = HoleDisc[i].w / horizon;
    vec2 span = sphereHits(dir, c, reach(i));
    float t = max(span.x, 0.0);
    vec3 p = (rayOrigin + dir * t - c) / horizon;
    vec3 v = dir;
    vec3 spin = cross(p, v);
    float h2 = dot(spin, spin);
    float side = dot(p, axis);
    bend.travelled = t;
    bend.end = c + p * horizon;
    for (int s = 0; s < 220; s++) {
        float r2 = dot(p, p);
        float r = sqrt(r2);
        if (r < 1.0) {
            bend.swallowed = true;
            break;
        }
        // Fine steps close in, where light bends hardest; long ones out where it hardly bends at all. Near the disc,
        // steps no longer than its glow is thick, so the glow is sampled smoothly.
        float puff = 0.06 * r + 0.1;
        float h = clamp(0.07 * r, 0.02, 0.9);
        if (r < outer) h = min(h, max(abs(side) * 0.5, puff * 0.6));
        float ease = falloff(bound * 0.5, bound, r);
        v += -1.5 * h2 * p / (r2 * r2 * r) * ease * h;
        vec3 next = p + v * h;
        float nextSide = dot(next, axis);
        if ((side > 0.0) != (nextSide > 0.0)) {
            vec3 q = mix(p, next, side / (side - nextSide));
            vec4 glow = discLight(i, q, v, axis, outer);
            bend.light += bend.through * glow.rgb;
            bend.through *= 1.0 - glow.a;
        }
        // The disc isn't quite flat: a glow puffs up off both its faces, thicker farther out, so seen edge on it is a
        // blazing band rather than a line.
        if (r > DISC_INNER * 0.9 && r < outer && abs(side) < puff * 3.0) {
            float mist = exp(-side * side / (puff * puff)) * h / puff * 0.2 * discEdges(r, outer) * HoleState[i].x;
            bend.light += bend.through * discGlow(r, discShift(p, r, v, axis)) * mist;
            bend.through *= 1.0 - clamp(mist * 0.6, 0.0, 0.5);
        }
        bend.travelled += length(next - p) * horizon;
        side = nextSide;
        p = next;
        if (bend.travelled > sceneDistance) {
            bend.landed = true;
            break;
        }
        if (bend.through < 0.01) break;
        if (r > bound && dot(p, v) > 0.0) break;
    }
    bend.end = c + p * horizon;
    bend.exitDir = normalize(v);
    return bend;
}

// What a bent ray sees beyond the hole: the frame, read again where the ray lands. Where that is off the screen,
// or behind the viewer, there is nothing to read, so it shows the nearest edge of the frame, dimmed.
vec3 beyond(Bend bend, float sceneDistance, bool sky) {
    vec3 target = bend.landed ? bend.end
            : bend.end + bend.exitDir * (sky ? 2000.0 : max(sceneDistance - bend.travelled, 1.0));
    vec3 at = project(target);
    vec2 inside = clamp(at.xy, vec2(0.001), vec2(0.999));
    float off = at.z > 0.0 ? length(at.xy - inside) * 3.0 : 1.0;
    vec3 seen = texture(InputColor, inside).rgb;
    return seen * mix(1.0, 0.25, clamp(off, 0.0, 1.0));
}

// Bright light rolled off smoothly towards white rather than clipped.
vec3 rollOff(vec3 light) {
    float l = luminance(light);
    if (l <= 0.0) return light;
    return light * (1.0 + l / 16.0) / (1.0 + l);
}

// ---------------------------------------------------------------- stopped time

// How far p, on the ground relative to a clock's centre, is from a hand pointing angle radians clockwise from twelve
// (north, -z) and running from start to end blocks out.
float handDistance(vec2 p, float angle, float start, float end) {
    vec2 along = vec2(sin(angle), -cos(angle));
    return length(p - along * clamp(dot(p, along), start, end));
}

// A hand halfWidth blocks either side of its line, d blocks from what this pixel sees, laid over color with a faint
// glow round it. However far off, it stays at least a pixel and a half across, dimmer where it has to be widened.
vec3 hand(vec3 color, float d, float halfWidth, vec3 tint, float strength) {
    float pixel = max(fwidth(d), 1.0e-4);
    float width = max(halfWidth, pixel * 0.75);
    float line = 1.0 - smoothstep(width - pixel * 0.5, width + pixel * 0.5, d);
    strength *= mix(0.6, 1.0, halfWidth / width);
    return mix(color, tint, line * 0.9 * strength) + tint * exp(-d / (width * 3.0)) * 0.12 * strength;
}

// Chrono Field i's clock hands, on the ground at its centre and keeping their colour in the drained field: hour and
// minute stopped at ten past ten (as ChronoFx's sparks draw them without this pass), the second hand where it says.
// The pixel's ray is followed to the clock face's plane; anything nearer hides the hands.
vec3 clockHands(int i, vec3 color, vec3 dir, float sceneDistance) {
    vec3 c = FieldCenter[i].xyz;
    float radius = FieldCenter[i].w;
    float strength = FieldState[i].w;
    // Worked out for every pixel, whether its ray meets the plane or not, so the derivatives that smooth the hands'
    // edges are defined everywhere.
    float dy = abs(dir.y) < 1.0e-4 ? 1.0e-4 : dir.y;
    float t = (c.y + 0.06 - rayOrigin.y) / dy;
    vec2 p = rayOrigin.xz + dir.xz * t - c.xz;
    if (!(t > 0.0 && t < sceneDistance)) strength = 0.0;
    vec3 cyan = vec3(0.53, 0.96, 1.0);
    color = hand(color, handDistance(p, -PI / 3.0, radius * 0.22, radius * 0.45), radius * 0.015, cyan, strength);
    color = hand(color, handDistance(p, PI / 3.0, radius * 0.22, radius * 0.75), radius * 0.011, cyan, strength);
    return hand(color, handDistance(p, FieldState[i].z, radius * 0.22, radius * 0.88), radius * 0.005, vec3(0.95, 0.71, 1.0), strength);
}

// Drains the colour from whatever of the world lies inside each Chrono Field, lays its clock's hands on the ground,
// and outlines the field's dome faintly.
vec3 stopTime(vec3 color, vec3 dir, float sceneDistance, bool sky) {
    vec3 point = rayOrigin + dir * min(sceneDistance, 4096.0);
    for (int i = 0; i < 4; i++) {
        float radius = FieldCenter[i].w;
        if (radius <= 0.0) continue;
        vec3 c = FieldCenter[i].xyz;
        float drain = FieldState[i].x;
        float front = FieldState[i].y;
        float d = length(point - c);
        if (!sky) {
            float caught = falloff(radius - 0.5, radius + 0.25, d);
            // When time resumes, colour floods back out from the centre behind a bright front.
            if (front > 0.0) caught *= smoothstep(front - 1.5, front, d);
            float grey = luminance(color);
            vec3 still = vec3(grey * 0.9, grey * 1.02, grey * 1.0);
            color = mix(color, still, caught * drain * 0.92);
            if (front > 0.0) {
                float edge = exp(-pow((d - front) / 0.7, 2.0)) * falloff(radius * 0.9, radius * 1.3, front);
                color += vec3(0.55, 1.0, 0.9) * edge * 0.7;
            }
        }
        color = clockHands(i, color, dir, sceneDistance);
        // The dome's skin, brightest seen edge on.
        vec2 span = sphereHits(dir, c, radius);
        if (span.y > 0.0 && drain > 0.0) {
            for (int k = 0; k < 2; k++) {
                float t = k == 0 ? span.x : span.y;
                if (t <= 0.0 || t >= sceneDistance) continue;
                vec3 n = normalize(rayOrigin + dir * t - c);
                float rim = pow(1.0 - abs(dot(n, dir)), 5.0);
                float shimmer = 0.75 + 0.25 * sin(dot(n, vec3(13.0, 17.0, 11.0)) + Clock.x * 1.3);
                color += vec3(0.45, 1.0, 0.88) * rim * shimmer * 0.22 * drain;
            }
        }
    }
    return color;
}

void main() {
    vec2 uv = texCoord;
    vec3 dir;
    float sceneDistance = setupRay(uv, texture(SceneDepth, uv).r, dir);
    bool sky = sceneDistance >= FAR;
    vec3 color = texture(InputColor, uv).rgb;
    color = stopTime(color, dir, sceneDistance, sky);

    // The nearest black hole this ray passes before reaching the world.
    int nearest = -1;
    float nearestT = FAR;
    for (int i = 0; i < 4; i++) {
        if (HoleCenter[i].w <= 0.0) continue;
        vec2 span = sphereHits(dir, HoleCenter[i].xyz, reach(i));
        if (span.y > 0.0 && span.x < sceneDistance && span.x < nearestT) {
            nearest = i;
            nearestT = span.x;
        }
    }
    if (nearest >= 0) {
        Bend bend = marchHole(nearest, dir, sceneDistance);
        vec3 background = bend.swallowed ? vec3(0.0) : beyond(bend, sceneDistance, sky);
        color = background * bend.through + rollOff(bend.light);
    }
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
