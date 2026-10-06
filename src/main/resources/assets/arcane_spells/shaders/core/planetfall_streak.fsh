#version 330

// Planetfall's lens streaks (see PlanetfallShader): the bloom's light smeared sideways, as an anamorphic lens does,
// a wider spread on each of a few passes.

uniform sampler2D InSampler;

layout(std140) uniform PlanetfallStep {
    vec4 Step; // x: spread, in texels
};

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float texel = Step.x / float(textureSize(InSampler, 0).x);
    vec3 sum = vec3(0.0);
    float weights = 0.0;
    for (int i = -8; i <= 8; i++) {
        float w = exp(-float(i * i) / 24.0);
        sum += texture(InSampler, texCoord + vec2(float(i) * texel, 0.0)).rgb * w;
        weights += w;
    }
    fragColor = vec4(sum / weights, 1.0);
}
