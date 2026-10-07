#version 150

// Private copy of the vanilla chunk vertex shader for the title screen cave, so shader packs (Iris) and shader
// modifying mods can't swap or patch it. Includes are inlined for the same reason.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

float fog_distance(vec3 pos, int shape) {
    if (shape == 0) {
        return length(pos);
    }
    return max(length(pos.xz), abs(pos.y));
}

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    vertexDistance = fog_distance(pos, FogShape);
    vertexColor = Color * texture(Sampler2, clamp(UV2 / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    texCoord0 = UV0;
}
