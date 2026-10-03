package com.glowcam.gl

/** Tiny row-major 3x3 affine helpers (applied to column vectors (u, v, 1)). */
object Mat3 {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    fun translate(tx: Float, ty: Float) = floatArrayOf(1f, 0f, tx, 0f, 1f, ty, 0f, 0f, 1f)

    fun scale(sx: Float, sy: Float) = floatArrayOf(sx, 0f, 0f, 0f, sy, 0f, 0f, 0f, 1f)

    fun rotate(rad: Float): FloatArray {
        val c = kotlin.math.cos(rad)
        val s = kotlin.math.sin(rad)
        return floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f)
    }

    /** a * b (b is applied first). */
    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(9)
        for (i in 0..2) for (j in 0..2) {
            var s = 0f
            for (k in 0..2) s += a[i * 3 + k] * b[k * 3 + j]
            r[i * 3 + j] = s
        }
        return r
    }

    /** Column-major layout for glUniformMatrix3fv. */
    fun toGl(m: FloatArray) =
        floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])

    /** Converts a SurfaceTexture 4x4 transform (column-major) to a row-major 3x3 affine. */
    fun fromSurfaceTexture(m: FloatArray) =
        floatArrayOf(m[0], m[4], m[12], m[1], m[5], m[13], 0f, 0f, 1f)

    /** Rotate by [deg] (counter-clockwise, y-up) around the centre of unit uv space. */
    fun rotateAroundCenter(deg: Int): FloatArray {
        val rad = Math.toRadians(deg.toDouble()).toFloat()
        return mul(translate(0.5f, 0.5f), mul(rotate(rad), translate(-0.5f, -0.5f)))
    }

    /** Horizontal mirror in unit uv space. */
    fun mirrorX() = mul(translate(1f, 0f), scale(-1f, 1f))

    /** Flip v: used for bitmap textures whose first row is the top. */
    fun flipV() = floatArrayOf(1f, 0f, 0f, 0f, -1f, 1f, 0f, 0f, 1f)
}
