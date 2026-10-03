package com.glowcam.gl

import com.glowcam.face.FaceLandmarks as L
import com.glowcam.face.FaceLandmarks
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Uniform data derived from face landmarks (all positions in source uv, lengths noted per field). */
class FaceUniforms {
    var faceN = 0; val face = FloatArray(3 * 4)
    var mouthN = 0; val mouth = FloatArray(3 * 4)
    var pushN = 0; val push = FloatArray(32 * 4); val pushDir = FloatArray(32 * 2)
    var eyeN = 0; val eye = FloatArray(6 * 4)
    var teethN = 0; val teeth = FloatArray(3 * 4)
    var bagN = 0; val bag = FloatArray(6 * 4)
    var redN = 0; val red = FloatArray(6 * 4)
    var smoothR = 0.02f

    // makeup (up to 3 faces)
    var lipN = 0; val lipOuter = FloatArray(3 * 20 * 2); val lipInner = FloatArray(3 * 20 * 2); val lipBox = FloatArray(3 * 4)
    var blushN = 0; val blush = FloatArray(6 * 4)
    var browN = 0; val brow = FloatArray(3 * 10 * 2); val browW = FloatArray(3)
    var shadeN = 0; val shade = FloatArray(6 * 4)

    fun clear() {
        faceN = 0; mouthN = 0; pushN = 0; eyeN = 0; teethN = 0; bagN = 0; redN = 0
        lipN = 0; blushN = 0; browN = 0; shadeN = 0
    }
}

object FaceGeometry {
    private const val MAX_FACES = 3

    // MediaPipe / ML Kit face-mesh indices
    private val LIPS_OUTER = intArrayOf(61, 185, 40, 39, 37, 0, 267, 269, 270, 409, 291, 375, 321, 405, 314, 17, 84, 181, 91, 146)
    private val LIPS_INNER = intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95)
    private val BROW_L_UP = intArrayOf(70, 63, 105, 66, 107)
    private val BROW_L_LOW = intArrayOf(46, 53, 52, 65, 55)
    private val BROW_R_UP = intArrayOf(336, 296, 334, 293, 300)
    private val BROW_R_LOW = intArrayOf(276, 283, 282, 295, 285)

    /** [aspect] = source width / height. Distances named "_h" are in image-height units. */
    fun fill(u: FaceUniforms, faces: List<FaceLandmarks>, p: EffectParams, aspect: Float) {
        u.clear()
        val wantMask = p.smooth > 0f || p.brighten > 0f
        val wantEyes = wantMask || p.eyes > 0f || p.redEye > 0f

        for (face in faces.take(MAX_FACES)) {
            fun dh(a: Int, b: Int) = hypot((face.x(a) - face.x(b)) * aspect, face.y(a) - face.y(b))

            val fw = dh(L.CHEEK_L, L.CHEEK_R)
            val fh = dh(L.FOREHEAD, L.CHIN)
            if (fw < 0.02f || fh < 0.02f) continue
            val cx = (face.x(L.CHEEK_L) + face.x(L.CHEEK_R)) / 2f
            val cy = (face.y(L.FOREHEAD) + face.y(L.CHIN)) / 2f

            if (wantMask && u.faceN < 3) {
                put4(u.face, u.faceN++, cx, cy, abs(face.x(L.CHEEK_R) - face.x(L.CHEEK_L)) / 2f,
                    abs(face.y(L.FOREHEAD) - face.y(L.CHIN)) / 2f * 1.05f)
                put4(
                    u.mouth, u.mouthN++,
                    (face.x(L.MOUTH_L) + face.x(L.MOUTH_R)) / 2f,
                    (face.y(L.LIP_TOP) + face.y(L.LIP_BOTTOM)) / 2f,
                    abs(face.x(L.MOUTH_R) - face.x(L.MOUTH_L)) / 2f * 1.2f,
                    max(abs(face.y(L.LIP_TOP) - face.y(L.LIP_BOTTOM)) / 2f * 1.4f, 0.006f),
                )
                u.smoothR = fh * 0.035f
            }

            // Eye geometry (also used for protection masks, enlarge, red-eye, dark circles).
            val eyes = arrayOf(
                intArrayOf(L.EYE_L_OUTER, L.EYE_L_INNER, L.EYE_L_TOP, L.EYE_L_BOTTOM),
                intArrayOf(L.EYE_R_OUTER, L.EYE_R_INNER, L.EYE_R_TOP, L.EYE_R_BOTTOM),
            )
            for (e in eyes) {
                val ex = (face.x(e[0]) + face.x(e[1]) + face.x(e[2]) + face.x(e[3])) / 4f
                val ey = (face.y(e[0]) + face.y(e[1]) + face.y(e[2]) + face.y(e[3])) / 4f
                val ew = dh(e[0], e[1])
                if (wantEyes && u.eyeN < 6) {
                    put4(u.eye, u.eyeN++, ex, ey, ew * 1.15f, p.eyes * 0.28f)
                }
                if (p.redEye > 0f && u.redN < 6) {
                    put4(u.red, u.redN++, ex, ey, ew * 0.22f, 0f)
                }
                if (p.darkCircles > 0f && u.bagN < 6) {
                    put4(
                        u.bag, u.bagN++,
                        ex, face.y(e[3]) - ew * 0.40f,
                        ew * 0.62f / aspect, ew * 0.30f,
                    )
                }
            }

            if (p.teeth > 0f && u.teethN < 3) {
                put4(
                    u.teeth, u.teethN++,
                    (face.x(L.INNER_L) + face.x(L.INNER_R)) / 2f,
                    (face.y(L.INNER_TOP) + face.y(L.INNER_BOTTOM)) / 2f,
                    abs(face.x(L.INNER_R) - face.x(L.INNER_L)) / 2f * 0.92f,
                    max(abs(face.y(L.INNER_TOP) - face.y(L.INNER_BOTTOM)) / 2f * 1.1f, 0.005f),
                )
            }

            // ---- makeup geometry ----
            if (p.hasMakeup) {
                val fi = minOf(u.lipN, 2)
                if (p.lip > 0f && u.lipN < 3) {
                    var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
                    for (k in 0 until 20) {
                        val xo = face.x(LIPS_OUTER[k]); val yo = face.y(LIPS_OUTER[k])
                        u.lipOuter[(u.lipN * 20 + k) * 2] = xo; u.lipOuter[(u.lipN * 20 + k) * 2 + 1] = yo
                        u.lipInner[(u.lipN * 20 + k) * 2] = face.x(LIPS_INNER[k]); u.lipInner[(u.lipN * 20 + k) * 2 + 1] = face.y(LIPS_INNER[k])
                        if (xo < minX) minX = xo; if (xo > maxX) maxX = xo; if (yo < minY) minY = yo; if (yo > maxY) maxY = yo
                    }
                    val padX = 0.01f; val padY = 0.01f
                    u.lipBox[u.lipN * 4] = minX - padX; u.lipBox[u.lipN * 4 + 1] = minY - padY
                    u.lipBox[u.lipN * 4 + 2] = maxX + padX; u.lipBox[u.lipN * 4 + 3] = maxY + padY
                    u.lipN++
                }
                if (p.blush > 0f && u.blushN + 2 <= 6) {
                    val r = fw * 0.17f
                    for (idx in intArrayOf(50, 280)) {
                        put4(u.blush, u.blushN++, face.x(idx), face.y(idx), r, 0f)
                    }
                }
                if (p.brow > 0f && u.browN < 3) {
                    val bi = u.browN
                    var thick = 0f
                    for (k in 0 until 5) {
                        val lx = (face.x(BROW_L_UP[k]) + face.x(BROW_L_LOW[k])) / 2f
                        val ly = (face.y(BROW_L_UP[k]) + face.y(BROW_L_LOW[k])) / 2f
                        val rx = (face.x(BROW_R_UP[k]) + face.x(BROW_R_LOW[k])) / 2f
                        val ry = (face.y(BROW_R_UP[k]) + face.y(BROW_R_LOW[k])) / 2f
                        u.brow[(bi * 10 + k) * 2] = lx; u.brow[(bi * 10 + k) * 2 + 1] = ly
                        u.brow[(bi * 10 + 5 + k) * 2] = rx; u.brow[(bi * 10 + 5 + k) * 2 + 1] = ry
                        thick += dh(BROW_L_UP[k], BROW_L_LOW[k]) + dh(BROW_R_UP[k], BROW_R_LOW[k])
                    }
                    u.browW[bi] = (thick / 10f * 0.75f).coerceIn(0.004f, 0.03f)
                    u.browN++
                }
                if (p.eyeShadow > 0f && u.shadeN + 2 <= 6) {
                    val pairs = arrayOf(intArrayOf(33, 133, 159, 52), intArrayOf(263, 362, 386, 282))
                    for (q in pairs) {
                        val ew = dh(q[0], q[1])
                        val cx2 = (face.x(q[0]) + face.x(q[1])) / 2f
                        val topY = face.y(q[2])
                        val browY = face.y(q[3])
                        val cy2 = topY + (browY - topY) * 0.38f
                        put4(u.shade, u.shadeN++, cx2, cy2, ew * 0.62f / aspect, max(abs(browY - topY) * 0.34f, 0.006f))
                    }
                }
                @Suppress("UNUSED_VARIABLE") val unused = fi
            }

            // Slim face + jaw: local push handles that move content towards the face axis.
            if ((p.slim > 0f || p.jaw > 0f) && u.pushN + 9 <= 32) {
                if (p.slim > 0f) {
                    val disp = 0.075f * p.slim * fw
                    val rad = 0.40f * fw
                    addPush(u, face, L.CHEEK_L, cx, disp, 0f, rad, aspect)
                    addPush(u, face, L.CHEEK_R, cx, disp, 0f, rad, aspect)
                    for (i in L.LOWER_CHEEK) addPush(u, face, i, cx, disp * 0.9f, 0f, rad, aspect)
                }
                if (p.jaw > 0f) {
                    val inward = 0.08f * p.jaw * fw
                    val up = 0.035f * p.jaw * fh
                    val rad = 0.34f * fw
                    for (i in L.JAW_SIDES) addPush(u, face, i, cx, inward, up, rad, aspect)
                    // chin: lift to shorten / sharpen
                    addPush(u, face, L.CHIN, cx, 0f, 0.07f * p.jaw * fh, 0.30f * fw, aspect)
                }
            }
        }
    }

    private fun addPush(
        u: FaceUniforms, face: FaceLandmarks, idx: Int, axisX: Float,
        inwardH: Float, upH: Float, radiusH: Float, aspect: Float,
    ) {
        val x = face.x(idx)
        val sign = if (x < axisX) 1f else -1f
        val i = u.pushN++
        put4(u.push, i, x, face.y(idx), radiusH, 0f)
        u.pushDir[i * 2] = sign * inwardH / aspect // height-units -> uv x
        u.pushDir[i * 2 + 1] = upH
    }

    private fun put4(a: FloatArray, i: Int, x: Float, y: Float, z: Float, w: Float) {
        a[i * 4] = x; a[i * 4 + 1] = y; a[i * 4 + 2] = z; a[i * 4 + 3] = w
    }
}
