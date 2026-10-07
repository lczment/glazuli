#version 150

// Framebuffer size in pixels.
uniform vec2 Resolution;
uniform float Time;
uniform float Alpha;
// xy = center in framebuffer pixels (origin bottom-left), z = radius in pixels, w = intensity.
uniform vec4 Glow;

out vec4 fragColor;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 blob(vec2 p, vec2 center, vec2 radius, vec3 color) {
    vec2 d = (p - center) / radius;
    return color * exp(-dot(d, d));
}

void main() {
    vec2 uv = gl_FragCoord.xy / Resolution;
    float aspect = Resolution.x / Resolution.y;
    // Height-normalized, centered coordinates so blobs keep their shape at any aspect ratio.
    vec2 p = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float t = Time;

    vec3 col = mix(vec3(0.043, 0.051, 0.071), vec3(0.078, 0.094, 0.141), smoothstep(-0.1, 1.1, uv.y));

    // Slow domain warp turns round blobs into drifting aurora-like veils.
    vec2 q = p + 0.07 * vec2(sin(p.y * 3.1 + t * 0.23), sin(p.x * 2.4 - t * 0.19));
    q += 0.03 * vec2(sin(q.y * 7.0 - t * 0.31), cos(q.x * 6.0 + t * 0.27));

    vec3 aurora = blob(q, vec2(-0.55 + 0.22 * sin(t * 0.11), 0.20 + 0.10 * cos(t * 0.13)), vec2(0.62, 0.34), vec3(0.055, 0.095, 0.230))
            + blob(q, vec2(0.52 + 0.18 * cos(t * 0.09 + 1.3), 0.26 + 0.12 * sin(t * 0.12 + 0.7)), vec2(0.50, 0.30), vec3(0.120, 0.070, 0.210))
            + blob(q, vec2(0.18 * sin(t * 0.07 + 2.1), -0.40 + 0.08 * cos(t * 0.10)), vec2(0.75, 0.26), vec3(0.025, 0.120, 0.135))
            + blob(q, vec2(-0.20 + 0.28 * cos(t * 0.05), -0.02 + 0.14 * sin(t * 0.08 + 2.0)), vec2(0.36, 0.30), vec3(0.050, 0.050, 0.130));
    // Pull the veils towards gray so they read as tinted light, not saturated color.
    float auroraLuma = dot(aurora, vec3(0.2126, 0.7152, 0.0722));
    col += mix(vec3(auroraLuma), aurora, 0.8) * 0.8;

    vec2 g = (gl_FragCoord.xy - Glow.xy) / max(Glow.z, 1.0);
    col += vec3(0.30, 0.45, 0.95) * Glow.w * exp(-dot(g, g) * 2.2);

    float v = length(p * vec2(0.8, 1.0));
    col *= mix(1.0, 0.5, smoothstep(0.3, 1.05, v));

    // Dither: dark low-saturation gradients band badly in 8-bit.
    col += (hash12(gl_FragCoord.xy + fract(t * 7.0) * 97.0) - 0.5) * (1.5 / 255.0);
    fragColor = vec4(max(col, 0.0), Alpha);
}
