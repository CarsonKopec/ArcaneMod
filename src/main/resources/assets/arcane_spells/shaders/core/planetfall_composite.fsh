#version 330

// Planetfall's last step (see PlanetfallShader): the drawn frame with its bloom and lens streaks, highlights rolled off
// rather than clipped, and what the moment does to the eye: aberration, rush, colour grade, flash, the impact frame,
// vignette and grain. Written back over the frame.

#moj_import <arcane_spells:planetfall_common.glsl>

uniform sampler2D InSampler;
uniform sampler2D Bloom;
uniform sampler2D Streak;

in vec2 texCoord;
out vec4 fragColor;

vec3 decode(vec2 uv) {
    vec4 c = texture(InSampler, clamp(uv, vec2(0.0005), vec2(0.9995)));
    return c.rgb * c.a * 8.0;
}

// Everything up to the knee is left exactly as the world drew it; brighter light eases towards white.
vec3 shoulder(vec3 c) {
    const float knee = 0.9;
    vec3 over = max(c - knee, 0.0);
    return min(c, vec3(knee)) + (1.0 - knee) * (1.0 - exp(-over / (1.0 - knee)));
}

void main() {
    vec2 uv = texCoord;
    inverseViewProj = inverse(ProjMat * ViewMat);
    float aberration = Lens.y * Clock.z * 0.007;
    vec2 away = uv - 0.5;
    vec3 c = aberration > 0.0
            ? vec3(decode(uv + away * aberration).r, decode(uv).g, decode(uv - away * aberration).b)
            : decode(uv);
    float rush = Lens.z * Clock.z;
    if (rush > 0.0 && PlanetCenter.w > 0.0) {
        vec3 target = project(PlanetCenter.xyz);
        if (target.z > 0.0) {
            vec2 toward = (target.xy - uv) * rush * 0.05;
            vec3 sum = c;
            for (int i = 1; i <= 6; i++) sum += decode(uv + toward * float(i) / 6.0);
            c = sum / 7.0;
        }
    }
    c += texture(Bloom, uv).rgb * 8.0 * Post.x * 0.55;
    c += texture(Streak, uv).rgb * 8.0 * Post.y * 0.6 * vec3(0.62, 0.7, 1.0);

    // The impact frame inks whatever was bright, on white, before the flash washes it out.
    float ink = step(0.3, luminance(c));
    c = shoulder(c);
    c = mix(c, c * Grade.rgb, Grade.a);
    c = mix(c, vec3(1.0, 0.94, 0.84), clamp(ShockState.y * Clock.w, 0.0, 1.0));
    if (ShockState.z > 0.0 && Clock.w > 0.0) {
        vec3 impactFrame = mix(vec3(1.0, 0.96, 0.9), vec3(0.06, 0.0, 0.1), ink);
        c = mix(c, impactFrame, clamp(ShockState.z, 0.0, 1.0));
    }
    float vignette = smoothstep(0.28, 0.9, length((uv - 0.5) * vec2(1.3, 1.0)));
    c *= 1.0 - vignette * clamp(Lens.x * Clock.z, 0.0, 0.95);
    c += (hash12(gl_FragCoord.xy + fract(Clock.x * 13.7) * 311.0) - 0.5) * 0.045 * Post.z;
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
