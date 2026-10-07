#version 150

uniform sampler2D DiffuseSampler;
uniform float Alpha;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // The main target's alpha channel is not meaningful (GUI draws write arbitrary alpha), so it is replaced.
    fragColor = vec4(texture(DiffuseSampler, texCoord).rgb, Alpha);
}
