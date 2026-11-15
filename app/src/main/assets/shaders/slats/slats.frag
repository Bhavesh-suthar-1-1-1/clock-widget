#version 450
// Slats clock face, drawn entirely in the fragment shader (Vulkan).
// The white capsule always shows the real current HH MM; the two number
// rings spin at the reference video's speed (minute ring 6 deg/s, hour
// ring 0.5 deg/s) from the angles in the push constants.
//
// Glyphs come from a Canvas-rasterised atlas texture (binding 0): black
// text on transparent, so the sampled alpha IS the ink coverage. Atlas:
// 8 columns x 5 rows of square cells. Cells 0..11 hour numbers
// ("12","1".."11"), 12..23 minute numbers ("05".."60"), 24..33 digits
// "0".."9". v = 0 is the top of the bitmap (Android row order, copied
// straight into the Vulkan image).
//
// Output is premultiplied RGBA: the surface uses PRE_MULTIPLIED composite
// alpha, so transparent pixels outside the clock let the app UI show
// through exactly like the Canvas bitmap previews.

layout(set = 0, binding = 0) uniform sampler2D uAtlas;

// 7 floats = 28 bytes; must match SlatsPush in the JNI bridge exactly.
layout(push_constant) uniform Push {
    float hourAngle;    // degrees, 0..360
    float minuteAngle;  // degrees, 0..360
    float capHour;      // displayed hour in the capsule (24h or 12h)
    float capMinute;    // displayed minute in the capsule
    float aspect;       // surface width / height (unused, kept for clarity)
    float resX;         // surface width in px
    float resY;         // surface height in px
} pc;

layout(location = 0) out vec4 outColor;

// Colours match SlatsDial.kt (#D32F2F, #B71C1C, #111111).
const vec3 RED = vec3(0.827, 0.184, 0.184);
const vec3 RED_DARK = vec3(0.718, 0.110, 0.110);
const vec3 INK = vec3(0.067, 0.067, 0.067);

// Sample one atlas cell. `local` is the glyph-local position in units of
// the cell's half size (x right, y down), each in [-1, 1] for the full cell.
float glyph(int cell, vec2 local) {
    float col = float(cell % 8);
    float row = float(cell / 8);
    vec2 cellUV = vec2(col / 8.0, row / 5.0);
    vec2 uv = cellUV + (local * 0.5 + 0.5) * vec2(1.0 / 8.0, 1.0 / 5.0);
    return texture(uAtlas, uv).a;
}

// One rotating number ring. `ringR` is the ring radius, `baseAngle` its
// rotation in degrees (clockwise from the top, like Canvas), `cellBase`
// the first atlas cell of its 12 numbers. `halfW`/`halfH` are the glyph
// quad half sizes in face units.
float ringGlyph(vec2 p, float ringR, float baseAngle, int cellBase,
                float halfW, float halfH) {
    float r = length(p);
    float dr = r - ringR;
    if (abs(dr) > halfH) return 0.0;
    // Angle clockwise from the top, degrees (y-down space, like Canvas).
    float ang = degrees(atan(p.y, p.x)) + 90.0;
    float local = mod(ang - baseAngle, 360.0);
    float idx = floor(local / 30.0);
    // Offset from the number's centre, wrapped to [-15, 15] degrees.
    float d = mod(local - idx * 30.0 + 15.0, 30.0) - 15.0;
    float dt = radians(d) * ringR;
    if (abs(dt) > halfW) return 0.0;
    // Glyph-local coords: the digit's top points outward (gy up = -dr),
    // its right side points clockwise (gx = dt).
    vec2 gl = vec2(dt / halfW, -dr / halfH);
    return glyph(cellBase + int(idx), gl);
}

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

// "over" compositing in straight-alpha space.
void over(inout vec3 col, inout float a, vec3 src, float srcA) {
    col = src * srcA + col * (1.0 - srcA);
    a = srcA + a * (1.0 - srcA);
}

void main() {
    vec2 res = vec2(pc.resX, pc.resY);
    // y-down coords like Canvas, half-height = 1.
    vec2 p = (gl_FragCoord.xy - 0.5 * res) / (0.5 * res.y);
    p.y = -p.y;

    vec3 col = vec3(0.0);
    float a = 0.0;

    // Drop shadow: offset disc, soft edge.
    float shR = length(p - vec2(0.03, 0.04));
    over(col, a, vec3(0.0), 0.27 * (1.0 - smoothstep(0.88, 0.93, shR)));

    float r = length(p);

    // Red rim disc.
    over(col, a, RED, 1.0 - smoothstep(0.905, 0.915, r));
    // Darker red edge ring near the rim.
    float rimBand = 1.0 - smoothstep(0.0, 0.004,
        abs(r - 0.907) - 0.0055);
    over(col, a, RED_DARK, rimBand * step(r, 0.915));
    // White face.
    over(col, a, vec3(1.0), 1.0 - smoothstep(0.797, 0.803, r));
    // Faint dark stroke where the face meets the rim.
    float faceEdge = 1.0 - smoothstep(0.0, 0.003, abs(r - 0.80) - 0.0025);
    over(col, a, vec3(0.0), 0.35 * faceEdge * step(r, 0.81));

    // Faint grey ring circles.
    float rings = 0.0;
    rings += 1.0 - smoothstep(0.0, 0.003, abs(r - 0.704) - 0.0032);
    rings += 1.0 - smoothstep(0.0, 0.003, abs(r - 0.528) - 0.0032);
    rings += 1.0 - smoothstep(0.0, 0.003, abs(r - 0.336) - 0.0032);
    over(col, a, vec3(0.0), 0.27 * clamp(rings, 0.0, 1.0) * step(r, 0.81));

    // Rotating number rings (behind the slats).
    float hourInk = ringGlyph(p, 0.616, pc.hourAngle, 0, 0.070, 0.055);
    float minInk = ringGlyph(p, 0.432, pc.minuteAngle, 12, 0.060, 0.047);
    float ink = clamp(hourInk + minInk, 0.0, 1.0);
    over(col, a, INK, ink * step(r, 0.81));

    // Vertical red slats across the face: 30 stripes, 42% duty.
    // (pitch-barW)/2 = 0.29 of the pitch; the bar spans [0.29, 0.71].
    float pitch = 2.0 * 0.80 / 30.0;
    float fx = fract((p.x + 0.80) / pitch);
    float edge = min(abs(fx - 0.29), abs(fx - 0.71)) * pitch;
    float inSlat = 1.0 - smoothstep(0.0, 0.006, edge);
    over(col, a, RED, inSlat * step(r, 0.80));

    // Capsule window: white rounded rect, red rim, real HH MM digits.
    vec2 capC = vec2(0.348, 0.0);
    vec2 capHalf = vec2(0.412, 0.152);
    float capD = sdRoundBox(p - capC, capHalf, 0.152);
    float capAA = fwidth(capD) * 1.5 + 0.0005;
    over(col, a, vec3(1.0), 1.0 - smoothstep(-capAA, capAA, capD));
    float capBorder = smoothstep(0.0, capAA, capD) *
                      (1.0 - smoothstep(0.0176 - capAA, 0.0176 + capAA, capD));
    over(col, a, RED, capBorder);

    // Capsule digits: "HH MM", 5 slots, the middle one a space.
    int d0 = int(pc.capHour) / 10;
    int d1 = int(pc.capHour) - d0 * 10;
    int d3 = int(pc.capMinute) / 10;
    int d4 = int(pc.capMinute) - d3 * 10;
    float capInk = 0.0;
    vec2 q;
    q = vec2((p.x - (0.348 - 2.0 * 0.106)) / 0.048, p.y / 0.088);
    if (max(abs(q.x), abs(q.y)) <= 1.0) capInk += glyph(24 + d0, q);
    q = vec2((p.x - (0.348 - 1.0 * 0.106)) / 0.048, p.y / 0.088);
    if (max(abs(q.x), abs(q.y)) <= 1.0) capInk += glyph(24 + d1, q);
    q = vec2((p.x - (0.348 + 1.0 * 0.106)) / 0.048, p.y / 0.088);
    if (max(abs(q.x), abs(q.y)) <= 1.0) capInk += glyph(24 + d3, q);
    q = vec2((p.x - (0.348 + 2.0 * 0.106)) / 0.048, p.y / 0.088);
    if (max(abs(q.x), abs(q.y)) <= 1.0) capInk += glyph(24 + d4, q);
    over(col, a, INK, clamp(capInk, 0.0, 1.0) * step(capD, 0.0));

    // Premultiplied output for the PRE_MULTIPLIED swapchain.
    outColor = vec4(col * a, a);
}
