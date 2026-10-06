#version 330

// Planetfall's bloom, first step (see PlanetfallShader): the light brighter than the world itself reaches, at half size.
// In: the frame, RGBM-encoded. Out: linear light, an eighth of it, so it can exceed 1.

uniform sampler2D InSampler;

out vec4 fragColor;

vec3 decode(ivec2 at) {
    vec4 c = texelFetch(InSampler, clamp(at, ivec2(0), textureSize(InSampler, 0) - 1), 0);
    return c.rgb * c.a * 8.0;
}

// A soft threshold: nothing below 0.85, all of it above 1.15, eased between.
vec3 bright(vec3 c) {
    float peak = max(max(c.r, c.g), c.b);
    float soft = clamp(peak - 0.85, 0.0, 0.3);
    soft = soft * soft / 1.2;
    return c * max(soft, peak - 1.0) / max(peak, 1.0e-4);
}

void main() {
    ivec2 at = ivec2(gl_FragCoord.xy) * 2;
    vec3 sum = bright(decode(at)) + bright(decode(at + ivec2(1, 0))) + bright(decode(at + ivec2(0, 1))) + bright(decode(at + ivec2(1, 1)));
    fragColor = vec4(sum / 4.0 / 8.0, 1.0);
}
