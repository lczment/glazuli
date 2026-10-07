#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec2 RectSize;
uniform float EdgeCut;
// The panel body inside the blit (x0, y0, x1, y1, same units as RectSize); anything outside it is dropped and EdgeCut
// is measured from its edges. Covers the whole blit for vanilla art.
uniform vec4 BodyRect;
// Opacity of coloured art, so large coloured areas can let the glass through.
uniform float ArtAlpha;

in vec2 texCoord0;
in vec2 localPos;

out vec4 fragColor;

// Vanilla GUI panel grey (#C6C6C6). Neutral greys are remapped relative to it: the panel itself vanishes so the glass
// behind shows through, lighter greys become white highlights and darker ones (slot wells, outlines, the empty
// furnace arrow) translucent shade. Coloured art is left untouched.
const float PANEL = 198.0 / 255.0;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    vec2 p = localPos * RectSize;
    if (p.x < BodyRect.x + EdgeCut || p.y < BodyRect.y + EdgeCut || p.x > BodyRect.z - EdgeCut || p.y > BodyRect.w - EdgeCut) {
        discard;
    }
    float hi = max(color.r, max(color.g, color.b));
    float lo = min(color.r, min(color.g, color.b));
    if (hi - lo >= 0.06) {
        color.a *= ArtAlpha;
    } else {
        float l = (color.r + color.g + color.b) / 3.0;
        if (abs(l - PANEL) < 0.02) {
            discard;
        }
        color = l > PANEL
                ? vec4(1.0, 1.0, 1.0, (l - PANEL) / (1.0 - PANEL) * 0.22 * color.a)
                : vec4(0.0, 0.0, 0.0, min((PANEL - l) / PANEL * 0.5, 0.26) * color.a);
    }
    fragColor = color * ColorModulator;
}
