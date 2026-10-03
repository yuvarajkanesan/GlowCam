package com.glowcam.face

/**
 * 468 face-mesh points as normalized uv in the upright (and, for selfies, mirrored) image,
 * origin bottom-left: [xy] = x0, y0, x1, y1, ...
 */
class FaceLandmarks(val xy: FloatArray) {
    fun x(i: Int) = xy[i * 2]
    fun y(i: Int) = xy[i * 2 + 1]

    companion object {
        const val COUNT = 468

        // Indices of the MediaPipe/ML Kit face mesh topology used by the effects.
        const val FOREHEAD = 10
        const val CHIN = 152
        const val CHEEK_L = 234
        const val CHEEK_R = 454
        val LOWER_CHEEK = intArrayOf(132, 361)
        val JAW_SIDES = intArrayOf(172, 397, 136, 365)
        const val EYE_L_OUTER = 33
        const val EYE_L_INNER = 133
        const val EYE_L_TOP = 159
        const val EYE_L_BOTTOM = 145
        const val EYE_R_OUTER = 263
        const val EYE_R_INNER = 362
        const val EYE_R_TOP = 386
        const val EYE_R_BOTTOM = 374
        const val MOUTH_L = 61
        const val MOUTH_R = 291
        const val LIP_TOP = 0
        const val LIP_BOTTOM = 17
        const val INNER_L = 78
        const val INNER_R = 308
        const val INNER_TOP = 13
        const val INNER_BOTTOM = 14
    }
}
