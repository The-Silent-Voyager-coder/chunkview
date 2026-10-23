#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec3 cameraRelativePos;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;

out vec4 fragColor;

void main() {
    // ModelOffset.x carries the full-detail radius. Never let the coarse mesh
    // cover the near terrain that the vanilla chunk renderer owns.
    if (length(cameraRelativePos.xz) < ModelOffset.x) {
        discard;
    }

    vec4 color = vertexColor * ColorModulator;
    if (color.a == 0.0) {
        discard;
    }
    fragColor = apply_fog(
        color,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        FogColor
    );
}
