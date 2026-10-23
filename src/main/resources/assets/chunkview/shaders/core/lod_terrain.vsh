#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;
out vec3 cameraRelativePos;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;

void main() {
    // Position is absolute world space. CameraBlockPos/CameraOffset are bound
    // by RenderSystem.bindDefaultUniforms() and let us compute fog distance
    // without a second per-frame vertex buffer.
    vec3 cameraRelative = Position - vec3(CameraBlockPos) - CameraOffset;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    cameraRelativePos = cameraRelative;
    sphericalVertexDistance = fog_spherical_distance(cameraRelative);
    cylindricalVertexDistance = fog_cylindrical_distance(cameraRelative);
}
