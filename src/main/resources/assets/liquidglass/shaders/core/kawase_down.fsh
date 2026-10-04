#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 HalfPixel;
uniform float Offset;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 o = HalfPixel * Offset;
    vec3 sum = texture(DiffuseSampler, texCoord).rgb * 4.0;
    sum += texture(DiffuseSampler, texCoord - o).rgb;
    sum += texture(DiffuseSampler, texCoord + o).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(o.x, -o.y)).rgb;
    sum += texture(DiffuseSampler, texCoord - vec2(o.x, -o.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}
