package com.glowcam.gl

/** A blemish spot in source-image uv (origin bottom-left); [r] is the radius in image-height units. */
data class Blemish(val x: Float, val y: Float, val r: Float)

/** Crop rectangle in source uv (origin bottom-left). */
data class CropRect(val x: Float = 0f, val y: Float = 0f, val w: Float = 1f, val h: Float = 1f) {
    val isFull get() = x <= 0f && y <= 0f && w >= 1f && h >= 1f
}

/**
 * Everything the shader needs, as plain data. The same stack drives the live preview,
 * photo capture, video, the editor and export, so they all look identical.
 */
data class EffectParams(
    // Beauty (0..1)
    val smooth: Float = 0f,
    val brighten: Float = 0f,
    val slim: Float = 0f,
    val eyes: Float = 0f,
    val jaw: Float = 0f,
    // Filter
    val filter: FilterDef = Filters.ORIGINAL,
    val filterIntensity: Float = 1f,
    // Adjust (-1..1, blur/vignette 0..1)
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val blur: Float = 0f,
    val radialBlur: Boolean = false,
    val vignette: Float = 0f,
    // Retouch (0..1)
    val teeth: Float = 0f,
    val darkCircles: Float = 0f,
    val redEye: Float = 0f,
    val blemishes: List<Blemish> = emptyList(),
    // Makeup (intensity 0..1, colours as ARGB ints)
    val lip: Float = 0f,
    val lipColor: Int = 0xFFC2185B.toInt(),
    val blush: Float = 0f,
    val blushColor: Int = 0xFFFF7A9A.toInt(),
    val brow: Float = 0f,
    val browColor: Int = 0xFF3A2A22.toInt(),
    val eyeShadow: Float = 0f,
    val shadowColor: Int = 0xFF9C6FD6.toInt(),
    // Background replace: 0 none, 1 solid, 2 gradient, 3 blur (needs the person mask)
    val bgMode: Int = 0,
    val bgColor1: Int = 0xFFFFFFFF.toInt(),
    val bgColor2: Int = 0xFFFFFFFF.toInt(),
    val bgBlur: Float = 0.7f,
    // Geometry
    val crop: CropRect = CropRect(),
    val straightenDeg: Float = 0f,
) {
    /** True when any effect needs face landmarks. */
    val needsFaces: Boolean
        get() = smooth > 0f || brighten > 0f || slim > 0f || eyes > 0f || jaw > 0f ||
            teeth > 0f || darkCircles > 0f || redEye > 0f || hasMakeup

    val hasMakeup: Boolean
        get() = lip > 0f || blush > 0f || brow > 0f || eyeShadow > 0f

    /** True when the selected filter needs the person mask. */
    val needsMask: Boolean get() = (filter.needsMask && filterIntensity > 0.01f) || bgMode != 0

    /** True when nothing would change the photo, so the original pixels can be saved untouched. */
    val isIdentity: Boolean
        get() = beautyIsDefault && !hasMakeup && bgMode == 0 && (filter.id == "original" || filterIntensity <= 0.01f)

    val beautyIsDefault: Boolean
        get() = smooth == 0f && brighten == 0f && slim == 0f && eyes == 0f && jaw == 0f

    fun withoutGeometry() = copy(crop = CropRect(), straightenDeg = 0f)
}
