attribute vec2 vPosition;
attribute vec2 vCoord;

varying vec2 vPreviewCoord;
uniform mat4 matTransform;

void main() {
    gl_Position = vec4(vPosition, 0, 1);
    vPreviewCoord = (matTransform * vec4(vCoord.xy, 0, 1)).xy;
}