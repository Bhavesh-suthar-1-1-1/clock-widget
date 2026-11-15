#version 450
// Fullscreen triangle: the engine draws 3 vertices with no vertex buffer,
// so positions come from gl_VertexIndex.
void main() {
    vec2 pos = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));
    gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
}
