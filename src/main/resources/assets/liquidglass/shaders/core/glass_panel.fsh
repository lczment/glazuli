#version 150

// Blurred (or sharp) copy of the backdrop, same orientation as the main framebuffer.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform vec4 ColorModulator;

uniform vec2 RectSize;
uniform float Radius;
uniform vec4 Tint;
uniform float Refraction;
uniform float Bevel;
uniform float Rim;
uniform float Chromatic;
uniform float Saturation;
uniform float Shadow;
uniform float Opacity;

in vec2 localPos;

out vec4 fragColor;

// Must stay in sync with GlassRenderer.SHADOW_MARGIN.
const float SHADOW_EXTENT = 6.0;
const float SHADOW_OFFSET_Y = 1.5;
const vec2 LIGHT_DIR = vec2(-0.5, -0.866);

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    // Derivatives first: they are undefined inside non-uniform control flow.
    vec2 dx = dFdx(localPos);
    vec2 dy = dFdy(localPos);
    // Maps a framebuffer-pixel delta to a local delta; inverted to go back. Handles GUI scale,
    // PoseStack scale and the GUI-down / framebuffer-up Y flip in one go.
    mat2 localPerPixel = mat2(dx, dy);
    mat2 pixelPerLocal = inverse(localPerPixel);
    float px = max(length(dx) + length(dy), 1e-4) * 0.5;

    vec2 halfSize = RectSize * 0.5;
    float radius = min(Radius, min(halfSize.x, halfSize.y));
    vec2 p = localPos - halfSize;
    float d = sdRoundBox(p, halfSize, radius);
    float coverage = clamp(0.5 - d / px, 0.0, 1.0);

    float shadowA = 0.0;
    if (Shadow > 0.0) {
        float sd = sdRoundBox(p - vec2(0.0, SHADOW_OFFSET_Y), halfSize, radius);
        float s = 1.0 - clamp(sd / SHADOW_EXTENT, 0.0, 1.0);
        shadowA = Shadow * s * s * s * (1.0 - coverage);
    }

    vec3 col = vec3(0.0);
    if (coverage > 0.0) {
        vec2 e = vec2(0.25, 0.0);
        vec2 grad = vec2(
            sdRoundBox(p + e.xy, halfSize, radius) - sdRoundBox(p - e.xy, halfSize, radius),
            sdRoundBox(p + e.yx, halfSize, radius) - sdRoundBox(p - e.yx, halfSize, radius)
        );
        vec2 n = dot(grad, grad) > 1e-10 ? normalize(grad) : vec2(0.0);

        float depth = max(-d, 0.0);
        float bevel = max(min(Bevel, min(halfSize.x, halfSize.y)), 1e-3);
        float edge = 1.0 - clamp(depth / bevel, 0.0, 1.0);
        // Lens-like bevel: displacement grows steeply towards the rim.
        float bend = edge * edge * edge;

        vec2 uv = gl_FragCoord.xy / ScreenSize;
        vec2 refr = (pixelPerLocal * (n * Refraction * bend)) / ScreenSize;
        vec2 ca = (pixelPerLocal * (n * Chromatic * edge * edge)) / ScreenSize;

        // Sampling outward pulls the surroundings into the rim, like a thick glass edge.
        col.r = textureLod(Sampler0, uv + refr + ca, 0.0).r;
        col.g = textureLod(Sampler0, uv + refr, 0.0).g;
        col.b = textureLod(Sampler0, uv + refr - ca, 0.0).b;

        float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
        col = max(mix(vec3(luma), col, Saturation), 0.0);
        col = mix(col, Tint.rgb, Tint.a);

        float facing = dot(n, LIGHT_DIR);
        float lit = pow(max(facing, 0.0), 1.5) + 0.45 * pow(max(-facing, 0.0), 1.5);

        // Subtle inner shadow on the side facing away from the light.
        float inner = exp(-depth / (bevel * 0.45)) * (0.35 + 0.65 * max(-facing, 0.0));
        col *= 1.0 - 0.10 * inner * Rim;

        // Fresnel: glass reflects more towards grazing angles at the bevel.
        col += vec3(0.07) * edge * edge * Rim;

        float rimLine = exp(-depth / (1.1 * px));
        float glow = exp(-depth / (bevel * 0.35));
        col += vec3(Rim * (rimLine * (0.22 + 0.78 * lit) + glow * 0.22 * lit));

        col += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
        col = clamp(col, 0.0, 1.0);
    }

    float alphaMul = Opacity * ColorModulator.a;
    fragColor = vec4(col * coverage, coverage + shadowA) * alphaMul;
}
