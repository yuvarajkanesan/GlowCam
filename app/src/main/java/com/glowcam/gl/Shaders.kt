package com.glowcam.gl

object Shaders {
    const val VERTEX = """#version 300 es
in vec2 aPos;
uniform float uFlipOut;
out vec2 vOut;
void main() {
    vOut = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos.x, aPos.y * uFlipOut, 0.0, 1.0);
}
"""

    /** [oes] = true samples the camera's external texture, false a regular 2D texture (stills). */
    fun fragment(oes: Boolean): String =
        "#version 300 es\n" + (if (oes) "#define USE_OES\n" else "") + FRAGMENT_BODY

    private const val FRAGMENT_BODY = """
#ifdef USE_OES
#extension GL_OES_EGL_image_external_essl3 : require
#endif
precision highp float;
precision highp int;
#ifdef USE_OES
uniform samplerExternalOES uTex;
#else
uniform sampler2D uTex;
#endif

in vec2 vOut;
out vec4 fragColor;

uniform mat3 uSrc;
uniform float uAspect;      // upright source width / height
uniform float uOutAspect;   // output width / height
uniform vec4 uCrop;         // x y w h in source uv
uniform float uStraighten;  // radians

uniform int uFaceN;   uniform vec4 uFace[3];
uniform int uMouthN;  uniform vec4 uMouth[3];
uniform int uPushN;   uniform vec4 uPush[32];  uniform vec2 uPushDir[32];
uniform int uEyeN;    uniform vec4 uEye[6];
uniform int uTeethN;  uniform vec4 uTeethE[3];
uniform int uBagN;    uniform vec4 uBag[6];
uniform int uRedN;    uniform vec4 uRed[6];
uniform int uBlemN;   uniform vec4 uBlem[16];

uniform float uSmooth;
uniform float uSmoothR;
uniform float uBright;
uniform float uTeethAmt;
uniform float uBagAmt;
uniform float uRedAmt;

uniform vec4 uFA;       // exposure, contrast, saturation, temp
uniform vec4 uFB;       // tint, fade, vignette, unused
uniform vec3 uShadow;
uniform vec3 uHigh;
uniform float uBW;
uniform float uGlow;
uniform float uFMix;

uniform sampler2D uMask;   // person mask, row 0 = top
uniform int uHasMask;
uniform int uMode;         // subject-aware filter mode, see FilterDef.mode
uniform float uBgAmt;

// background replace
uniform int uBgMode;
uniform vec3 uBgC1;
uniform vec3 uBgC2;
uniform float uBgBlur;

// makeup
uniform int uLipN;    uniform vec2 uLipOuter[60]; uniform vec2 uLipInner[60]; uniform vec4 uLipBox[3]; uniform vec4 uLipCol;
uniform int uBlushN;  uniform vec4 uBlush[6];  uniform vec4 uBlushCol;
uniform int uBrowN;   uniform vec2 uBrow[30];  uniform float uBrowW[3]; uniform vec4 uBrowCol;
uniform int uShadeN;  uniform vec4 uShade[6];  uniform vec4 uShadeCol;

uniform float uBrightness;
uniform float uContrast;
uniform float uSaturation;
uniform float uBlur;
uniform int uRadialBlur;
uniform float uVig;

const vec3 LUM = vec3(0.299, 0.587, 0.114);

vec2 asp() { return vec2(uAspect, 1.0); }

vec3 src(vec2 uv) {
    vec2 t = (uSrc * vec3(clamp(uv, 0.0, 1.0), 1.0)).xy;
    return texture(uTex, t).rgb;
}

vec2 disc(int i, int n) {
    float a = float(i) * 2.39996323;
    float r = sqrt((float(i) + 0.5) / float(n));
    return vec2(cos(a), sin(a)) * r;
}

float skinProb(vec3 c) {
    float cb = -0.169 * c.r - 0.331 * c.g + 0.5 * c.b + 0.5;
    float cr = 0.5 * c.r - 0.419 * c.g - 0.081 * c.b + 0.5;
    return smoothstep(0.25, 0.30, cb) * (1.0 - smoothstep(0.50, 0.55, cb))
         * smoothstep(0.48, 0.52, cr) * (1.0 - smoothstep(0.68, 0.74, cr));
}

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = (p - a) * asp();
    vec2 ba = (b - a) * asp();
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-8), 0.0, 1.0);
    return length(pa - ba * h);
}

/** 1 on the person, 0 on the background (softened so cut-out edges are not stair-stepped). */
float subjectAlpha(vec2 q) {
    vec2 mc = vec2(q.x, 1.0 - q.y);
    vec2 ts = 1.5 / vec2(textureSize(uMask, 0));
    float m = texture(uMask, mc).r * 0.36
            + 0.16 * (texture(uMask, mc + vec2(ts.x, 0.0)).r + texture(uMask, mc - vec2(ts.x, 0.0)).r
                    + texture(uMask, mc + vec2(0.0, ts.y)).r + texture(uMask, mc - vec2(0.0, ts.y)).r);
    return smoothstep(0.35, 0.65, m);
}

/** Treats the person and the background differently (needs uMask). */
vec3 subjectEffect(vec3 f, vec2 q) {
    float sub = subjectAlpha(q);
    float l = dot(f, LUM);
    vec3 gray = vec3(l);
    vec3 bg = f;
    vec3 fg = f;
    if (uMode == 1) {                       // colour splash: B&W background, vivid subject
        bg = (gray - 0.5) * 1.15 + 0.5;
        fg = mix(gray, f, 1.18);
    } else if (uMode == 2) {                // portrait blur: soft bokeh background
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 24; i++) {
            acc += src(q + disc(i, 24) * (0.03 * uBgAmt) / asp());
        }
        bg = acc / 24.0;
    } else if (uMode == 3) {                // spotlight: dark background, lifted subject
        bg = gray * 0.22;
        fg = f * 1.10 + 0.02;
    } else if (uMode == 4) {                // reverse splash: B&W subject, colour background
        fg = (gray - 0.5) * 1.15 + 0.5;
    } else if (uMode == 5) {                // neon duotone background
        bg = mix(vec3(0.10, 0.04, 0.30), vec3(1.0, 0.42, 0.72), smoothstep(0.1, 0.9, l));
    } else if (uMode == 6) {                // airy white background
        bg = mix(f, vec3(1.0), 0.78);
        fg = f * 1.05;
    } else if (uMode == 7) {                // warm subject, cool background
        bg = f * vec3(0.80, 0.95, 1.18);
        fg = f * vec3(1.10, 1.02, 0.88);
    }
    return mix(bg, fg, sub);
}

vec3 applyFilter(vec3 c) {
    c *= exp2(uFA.x);
    c.r += uFA.w * 0.10;
    c.b -= uFA.w * 0.10;
    c.g += uFB.x * 0.06;
    c = (c - 0.5) * (1.0 + uFA.y) + 0.5;
    float l = dot(c, LUM);
    c = mix(vec3(l), c, 1.0 + uFA.z);
    c += uShadow * (1.0 - l) * 0.28 + uHigh * l * 0.20;
    c = c * (1.0 - uFB.y) + vec3(uFB.y * 0.55);
    c = mix(c, vec3(dot(c, LUM)), uBW);
    return clamp(c, 0.0, 1.0);
}

void main() {
    // ---- geometry: output uv -> source uv (crop + straighten) ----
    // The crop rectangle lives in the straightened frame: rotate the whole image about its centre,
    // then crop. This lets the editor draw the crop box over what the user actually sees.
    vec2 p = uCrop.xy + vOut * uCrop.zw;
    if (uStraighten != 0.0) {
        vec2 q = (p - 0.5) * asp();
        float cs = cos(uStraighten);
        float sn = sin(uStraighten);
        q = vec2(cs * q.x - sn * q.y, sn * q.x + cs * q.y);
        p = q / asp() + 0.5;
    }

    // ---- face warps (slim, jaw, eyes) ----
    vec2 q = p;
    for (int i = 0; i < uPushN; i++) {
        float d = length((p - uPush[i].xy) * asp());
        float w = 1.0 - smoothstep(0.0, uPush[i].z, d);
        q -= uPushDir[i] * w;
    }
    for (int i = 0; i < uEyeN; i++) {
        float r = uEye[i].z;
        float d = length((p - uEye[i].xy) * asp());
        if (d < r && uEye[i].w > 0.0) {
            float t = d / r;
            float f = 1.0 - uEye[i].w * (1.0 - t * t);
            q = uEye[i].xy + (q - uEye[i].xy) * f;
        }
    }

    // ---- base colour (+ optional blur) ----
    vec3 c = src(q);
    if (uBlur > 0.001) {
        float bm = 1.0;
        if (uRadialBlur == 1) {
            float dist = length((vOut - 0.5) * vec2(uOutAspect, 1.0));
            bm = smoothstep(0.18, 0.55, dist);
        }
        float rad = uBlur * 0.035 * bm;
        if (rad > 0.0004) {
            vec3 acc = c;
            for (int i = 0; i < 16; i++) {
                acc += src(q + disc(i, 16) * rad / asp());
            }
            c = acc / 17.0;
        }
    }

    // ---- face masks ----
    float fm = 0.0;
    for (int i = 0; i < uFaceN; i++) {
        vec2 e = (q - uFace[i].xy) / uFace[i].zw;
        fm = max(fm, 1.0 - smoothstep(0.65, 1.0, length(e)));
    }
    float prot = 0.0;
    for (int i = 0; i < uEyeN; i++) {
        float d = length((p - uEye[i].xy) * asp());
        prot = max(prot, 1.0 - smoothstep(uEye[i].z * 0.40, uEye[i].z * 0.65, d));
    }
    for (int i = 0; i < uMouthN; i++) {
        vec2 e = (q - uMouth[i].xy) / uMouth[i].zw;
        prot = max(prot, 1.0 - smoothstep(0.7, 1.0, length(e)));
    }
    fm *= (1.0 - prot);

    // ---- skin smoothing (masked bilateral filter) ----
    if (uSmooth > 0.001 && fm > 0.01) {
        float sm = uSmooth * fm * mix(0.3, 1.0, skinProb(c));
        float k = mix(70.0, 18.0, uSmooth);
        vec3 acc = c;
        float ws = 1.0;
        for (int i = 0; i < 16; i++) {
            vec3 s = src(q + disc(i, 16) * uSmoothR / asp());
            vec3 d = s - c;
            float w = exp(-dot(d, d) * k);
            acc += s * w;
            ws += w;
        }
        c = mix(c, acc / ws, clamp(sm * 1.15, 0.0, 1.0));
    }

    // ---- brighten ----
    if (uBright > 0.001) {
        float bm = mix(0.35, 1.0, fm);
        c = pow(max(c, vec3(0.0)), vec3(1.0 - 0.30 * uBright * bm)) + uBright * 0.04 * bm;
    }

    // ---- retouch: teeth whitening ----
    if (uTeethAmt > 0.001) {
        for (int i = 0; i < uTeethN; i++) {
            vec2 e = (q - uTeethE[i].xy) / uTeethE[i].zw;
            float m = 1.0 - smoothstep(0.6, 1.0, length(e));
            float l = dot(c, LUM);
            float notLip = 1.0 - smoothstep(0.10, 0.22, c.r - c.g);
            float bright = smoothstep(0.22, 0.45, l);
            m *= notLip * bright;
            vec3 wh = vec3(l * 1.18 + 0.05);
            wh = mix(wh, c, 0.35);
            c = mix(c, wh, clamp(m * uTeethAmt, 0.0, 1.0));
        }
    }

    // ---- retouch: dark circles ----
    if (uBagAmt > 0.001) {
        for (int i = 0; i < uBagN; i++) {
            vec2 e = (q - uBag[i].xy) / uBag[i].zw;
            float m = 1.0 - smoothstep(0.5, 1.0, length(e));
            if (m > 0.0) {
                vec2 off = vec2(0.0, -uBag[i].w * 2.6); // sample healthy cheek below the bag
                vec3 ref = (src(q + off) + src(q + off + vec2(0.02, 0.0)) + src(q + off - vec2(0.02, 0.0))) / 3.0;
                float lift = max(dot(ref, LUM) - dot(c, LUM), 0.0);
                vec3 fix = mix(c, ref, 0.6) + lift * 0.35;
                c = mix(c, fix, clamp(m * uBagAmt, 0.0, 1.0));
            }
        }
    }

    // ---- retouch: red-eye ----
    if (uRedAmt > 0.001) {
        for (int i = 0; i < uRedN; i++) {
            float d = length((p - uRed[i].xy) * asp());
            float m = 1.0 - smoothstep(uRed[i].z * 0.6, uRed[i].z, d);
            float redness = c.r - max(c.g, c.b);
            if (m > 0.0 && redness > 0.08) {
                float k2 = clamp(redness * 4.0, 0.0, 1.0) * m * uRedAmt;
                float healed = (c.g + c.b) * 0.5 * 0.85;
                c.r = mix(c.r, healed, k2);
            }
        }
    }

    // ---- retouch: blemish removal (heal from surrounding ring) ----
    for (int i = 0; i < uBlemN; i++) {
        float r = uBlem[i].z;
        float d = length((q - uBlem[i].xy) * asp());
        if (d < r * 1.25) {
            vec3 ring = vec3(0.0);
            for (int j = 0; j < 8; j++) {
                float a = float(j) * 0.785398;
                ring += src(uBlem[i].xy + vec2(cos(a), sin(a)) * r * 1.9 / asp());
            }
            ring /= 8.0;
            float m = 1.0 - smoothstep(r * 0.7, r * 1.2, d);
            c = mix(c, ring, m);
        }
    }

    // ---- makeup ----
    if (uLipCol.a > 0.001) {
        for (int f = 0; f < uLipN; f++) {
            vec4 bb = uLipBox[f];
            if (q.x > bb.x && q.x < bb.z && q.y > bb.y && q.y < bb.w) {
                bool inO = false;
                bool inI = false;
                float dO = 1e9;
                float dI = 1e9;
                for (int i = 0; i < 20; i++) {
                    int j = (i + 1) % 20;
                    vec2 a = uLipOuter[f * 20 + i];
                    vec2 b = uLipOuter[f * 20 + j];
                    if (((a.y > q.y) != (b.y > q.y)) && (q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x)) inO = !inO;
                    dO = min(dO, segDist(q, a, b));
                    a = uLipInner[f * 20 + i];
                    b = uLipInner[f * 20 + j];
                    if (((a.y > q.y) != (b.y > q.y)) && (q.x < (b.x - a.x) * (q.y - a.y) / (b.y - a.y) + a.x)) inI = !inI;
                    dI = min(dI, segDist(q, a, b));
                }
                float mo = inO ? smoothstep(0.0, 0.004, dO) : 0.0;
                float mi = inI ? smoothstep(0.0, 0.004, dI) : 0.0;
                float lm = mo * (1.0 - mi);
                float l = dot(c, LUM);
                vec3 lipC = uLipCol.rgb * (0.50 + 0.95 * l) + max(l - 0.62, 0.0) * 0.55;
                c = mix(c, lipC, lm * uLipCol.a * 0.88);
            }
        }
    }
    if (uBlushCol.a > 0.001) {
        for (int i = 0; i < uBlushN; i++) {
            float d = length((q - uBlush[i].xy) * asp());
            float g = exp(-(d * d) / (uBlush[i].z * uBlush[i].z * 0.55));
            vec3 tinted = mix(c, uBlushCol.rgb * (0.65 + 0.7 * dot(c, LUM)), 0.6);
            c = mix(c, tinted, g * uBlushCol.a * 0.85);
        }
    }
    if (uShadeCol.a > 0.001) {
        for (int i = 0; i < uShadeN; i++) {
            vec2 e = (q - uShade[i].xy) / uShade[i].zw;
            float m = 1.0 - smoothstep(0.3, 1.0, length(e));
            c = mix(c, uShadeCol.rgb * (0.45 + 0.9 * dot(c, LUM)), m * uShadeCol.a * 0.65);
        }
    }
    if (uBrowCol.a > 0.001) {
        for (int f = 0; f < uBrowN; f++) {
            float md = 1e9;
            for (int k = 0; k < 9; k++) {
                if (k == 4) continue;
                md = min(md, segDist(q, uBrow[f * 10 + k], uBrow[f * 10 + k + 1]));
            }
            float m = 1.0 - smoothstep(uBrowW[f] * 0.55, uBrowW[f], md);
            c = mix(c, uBrowCol.rgb * (0.5 + 0.8 * dot(c, LUM)), m * uBrowCol.a * 0.75);
        }
    }

    // ---- filter ----
    if (uFMix > 0.001) {
        vec3 f = applyFilter(c);
        if (uGlow > 0.001) {
            vec3 g = vec3(0.0);
            for (int i = 0; i < 12; i++) {
                g += src(q + disc(i, 12) * 0.022 / asp());
            }
            g /= 12.0;
            f = 1.0 - (1.0 - f) * (1.0 - g * uGlow * 0.9);
        }
        if (uMode != 0 && uHasMask == 1) {
            f = subjectEffect(f, q);
        }
        c = mix(c, f, uFMix);
    }

    // ---- background replace ----
    if (uBgMode != 0 && uHasMask == 1) {
        float sub = subjectAlpha(q);
        vec3 bgc = uBgC1;
        if (uBgMode == 2) {
            bgc = mix(uBgC1, uBgC2, 1.0 - vOut.y);
        } else if (uBgMode == 3) {
            vec3 acc = vec3(0.0);
            for (int i = 0; i < 24; i++) {
                acc += src(q + disc(i, 24) * (0.045 * uBgBlur) / asp());
            }
            bgc = acc / 24.0;
        }
        c = mix(bgc, c, sub);
    }

    // ---- user adjustments ----
    c += uBrightness * 0.35;
    c = (c - 0.5) * (1.0 + uContrast) + 0.5;
    float lum = dot(c, LUM);
    c = mix(vec3(lum), c, 1.0 + uSaturation);

    // ---- vignette ----
    float vig = uVig + uFB.z * uFMix;
    if (vig > 0.001) {
        float vd = length((vOut - 0.5) * vec2(uOutAspect, 1.0));
        float v = smoothstep(0.28, 0.95, vd);
        c *= 1.0 - clamp(vig, 0.0, 1.0) * v * 0.95;
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
}
