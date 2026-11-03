#version 330

// Keep evidence colors independent of environmental and render-distance fog.
// The vanilla line vertex shader still provides projection and pixel line width.
#moj_import <minecraft:dynamictransforms.glsl>
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    fragColor = vertexColor * ColorModulator;
}
