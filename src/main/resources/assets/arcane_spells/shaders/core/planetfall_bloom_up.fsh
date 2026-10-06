#version 330

// Planetfall's bloom (see PlanetfallShader): one step back up the chain, added onto the level above, so every scale of
// glow ends up in the largest.

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 nudge = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord + vec2(-nudge.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-nudge.x, nudge.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, nudge.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(nudge.x, nudge.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(nudge.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(nudge.x, -nudge.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -nudge.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-nudge.x, -nudge.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0, 1.0);
}
