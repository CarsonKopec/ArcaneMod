#version 330

// Copies the frame for Planetfall's screen pass to read while it draws over the frame (see PlanetfallShader).

uniform sampler2D InSampler;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
}
