#version 150

// Backdrop copy (sharp for glass, blurred for films), same orientation as the main framebuffer.
uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform vec4 ColorModulator;

uniform vec2 RectSize;
uniform float Radius;
// rgb = accent (glass) or body colour (film); a = colour mix (glass) or body opacity (film).
uniform vec4 Tint;
uniform float Shift;
uniform float Falloff;
uniform float RimFrac;
// 0 = refracting glass, 1 = film.
uniform float Material;
uniform float Shadow;
uniform float Opacity;
// Glass only: specular rim / fill light strength, and pointer hover 0..1.
uniform float Specular;
uniform float Highlight;

in vec2 localPos;

out vec4 fragColor;

// Must stay in sync with GlassRenderer.SHADOW_MARGIN.
const float SHADOW_EXTENT = 6.0;
const float SHADOW_OFFSET_Y = 2.0;

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
    // Maps a local delta back to framebuffer pixels. Handles GUI scale, PoseStack scale and the Y flip.
    mat2 pixelPerLocal = inverse(mat2(dx, dy));
    float px = max(length(dx) + length(dy), 1e-4) * 0.5;

    vec2 halfSize = RectSize * 0.5;
    float halfH = min(halfSize.x, halfSize.y);
    float radius = min(Radius, halfH);
    vec2 p = localPos - halfSize;
    float d = sdRoundBox(p, halfSize, radius);
    float coverage = clamp(0.5 - d / px, 0.0, 1.0);

    float shadowA = 0.0;
    if (Shadow > 0.0) {
        float sd = sdRoundBox(p - vec2(0.0, SHADOW_OFFSET_Y), halfSize, radius);
        float s = 1.0 - clamp(sd / SHADOW_EXTENT, 0.0, 1.0);
        shadowA = Shadow * s * s * (1.0 - coverage);
    }

    vec3 col = vec3(0.0);
    if (coverage > 0.0) {
        vec2 e = vec2(0.25, 0.0);
        vec2 grad = vec2(
            sdRoundBox(p + e.xy, halfSize, radius) - sdRoundBox(p - e.xy, halfSize, radius),
            sdRoundBox(p + e.yx, halfSize, radius) - sdRoundBox(p - e.yx, halfSize, radius)
        );
        vec2 n = dot(grad, grad) > 1e-10 ? normalize(grad) : vec2(0.0);
        vec2 uv = gl_FragCoord.xy / ScreenSize;

        if (Material < 0.5) {
            // Spec §3 refraction model, verbatim.
            float radial = halfH + d;
            float rho = clamp(radial / halfH, 0.0, 1.0);
            float edge0 = 1.0 - RimFrac;
            float t = clamp((rho - edge0) / max(RimFrac, 1e-4), 0.0, 1.0);
            float f = pow(t, Falloff);
            float k = 1.0 + Shift * f;
            float disp = max(radial, 0.0) * (1.0 - 1.0 / k);
            vec2 sampleUv = uv + (pixelPerLocal * (-n * disp)) / ScreenSize;
            col = textureLod(Sampler0, sampleUv, 0.0).rgb;
            // iOS 26 glass keeps the backdrop vivid through the frost.
            float luma = dot(col, vec3(0.2126, 0.7152, 0.0722));
            col = mix(vec3(luma), col, 1.0 + 0.25 * Specular);
            float a = Tint.a * (1.0 - 0.5 * f);
            col = mix(col, Tint.rgb, a);
            if (Specular > 0.0) {
                // Fill light, stronger under the pointer.
                col = mix(col, vec3(1.0), Specular * (0.05 + 0.10 * Highlight));
                // Specular rim lit from the top left, with a weaker bounce on the opposite edge.
                float depth = max(-d, 0.0);
                float band = 1.0 - smoothstep(0.0, 1.2, depth);
                float glow = 1.0 - smoothstep(0.0, 5.0, depth);
                vec2 light = normalize(vec2(-0.55, -1.0));
                float key = pow(max(dot(n, light), 0.0), 1.5);
                float bounce = 0.45 * pow(max(dot(n, -light), 0.0), 1.5);
                float rim = band * (0.25 + 0.75 * (key + bounce)) * (0.55 + 0.35 * Highlight);
                col = mix(col, vec3(1.0), Specular * clamp(rim + 0.06 * glow * (key + bounce), 0.0, 1.0));
            }
        } else {
            col = mix(textureLod(Sampler0, uv, 0.0).rgb, Tint.rgb, Tint.a);
            // --glass-ring: inset 0 1px 1px rgba(255,255,255,.95), i.e. a soft 1px line along the top edge.
            float depth = max(-d, 0.0);
            float ring = (1.0 - smoothstep(0.0, 1.5, depth)) * clamp(-n.y, 0.0, 1.0);
            // RimFrac 0 turns the ring off, for films tiled into seamless rows.
            col = mix(col, vec3(1.0), 0.95 * ring * RimFrac);
        }

        col += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
        col = clamp(col, 0.0, 1.0);
    }

    float alphaMul = Opacity * ColorModulator.a;
    fragColor = vec4(col * coverage, coverage + shadowA) * alphaMul;
}
