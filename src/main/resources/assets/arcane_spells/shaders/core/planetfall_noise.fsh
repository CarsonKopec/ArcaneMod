#version 330

// A 256 by 256 tile of random values for Planetfall's noise (see planetfall_common.glsl), drawn once: red at each
// texel, and green the red of the texel 37 across and 17 up, so one filtered lookup gives two neighbouring slices of
// 3D noise at once.

out vec4 fragColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec2 at = floor(gl_FragCoord.xy);
    fragColor = vec4(hash(at), hash(mod(at + vec2(37.0, 17.0), 256.0)), 0.0, 1.0);
}
