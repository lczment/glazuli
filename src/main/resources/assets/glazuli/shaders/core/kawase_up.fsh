#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 HalfPixel;
uniform float Offset;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 o = HalfPixel * Offset;
    vec3 sum = texture(DiffuseSampler, texCoord + vec2(-o.x * 2.0, 0.0)).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(-o.x, o.y)).rgb * 2.0;
    sum += texture(DiffuseSampler, texCoord + vec2(0.0, o.y * 2.0)).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(o.x, o.y)).rgb * 2.0;
    sum += texture(DiffuseSampler, texCoord + vec2(o.x * 2.0, 0.0)).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(o.x, -o.y)).rgb * 2.0;
    sum += texture(DiffuseSampler, texCoord + vec2(0.0, -o.y * 2.0)).rgb;
    sum += texture(DiffuseSampler, texCoord + vec2(-o.x, -o.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0, 1.0);
}
