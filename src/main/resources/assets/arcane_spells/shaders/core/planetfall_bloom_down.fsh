#version 330

// Planetfall's bloom (see PlanetfallShader): one step down the chain, halving the size and blurring as it goes.

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 nudge = 0.5 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord - nudge).rgb;
    sum += texture(InSampler, texCoord + nudge).rgb;
    sum += texture(InSampler, texCoord + vec2(nudge.x, -nudge.y)).rgb;
    sum += texture(InSampler, texCoord - vec2(nudge.x, -nudge.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}
