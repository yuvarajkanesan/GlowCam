package com.glowcam.gl

/**
 * A filter is pure data (parameters for the shader's colour grade), so adding one needs no new code.
 * temp/tint: -1..1, exposure in EV, contrast/saturation: -1..1 offsets, fade/vignette/glow: 0..1.
 */
data class FilterDef(
    val id: String,
    val name: String,
    val category: String,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val temp: Float = 0f,
    val tint: Float = 0f,
    val fade: Float = 0f,
    val vignette: Float = 0f,
    val shadow: Int = 0x000000,
    val highlight: Int = 0x000000,
    val bw: Float = 0f,
    val glow: Float = 0f,
    /** Subject-aware mode (needs the person mask): 0 none, 1 colour splash, 2 portrait blur, 3 spotlight,
     *  4 reverse splash, 5 neon duotone background, 6 high-key background, 7 warm subject / cool background. */
    val mode: Int = 0,
    val bgAmount: Float = 1f,
    val swatchOverride: Int = 0,
) {
    val needsMask get() = mode != 0
    val premium get() = category == "Premium"

    /** Approximate colour for the UI chip. */
    fun swatch(): Int {
        if (swatchOverride != 0) return swatchOverride
        var r = 0.55f
        var g = 0.55f
        var b = 0.55f
        r += temp * 0.30f; b -= temp * 0.30f; g += tint * 0.15f
        r += ((highlight shr 16 and 0xFF) / 255f) * 0.4f
        g += ((highlight shr 8 and 0xFF) / 255f) * 0.4f
        b += ((highlight and 0xFF) / 255f) * 0.4f
        r += ((shadow shr 16 and 0xFF) / 255f) * 0.15f
        g += ((shadow shr 8 and 0xFF) / 255f) * 0.15f
        b += ((shadow and 0xFF) / 255f) * 0.15f
        val l = (r + g + b) / 3f
        r = l + (r - l) * (1f + saturation); g = l + (g - l) * (1f + saturation); b = l + (b - l) * (1f + saturation)
        r = r + (l - r) * bw; g = g + (l - g) * bw; b = b + (l - b) * bw
        r = r * (1f + exposure * 0.3f) + fade * 0.2f
        g = g * (1f + exposure * 0.3f) + fade * 0.2f
        b = b * (1f + exposure * 0.3f) + fade * 0.2f
        fun c(x: Float) = (x.coerceIn(0f, 1f) * 255f).toInt()
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}

object Filters {
    val ORIGINAL = FilterDef("original", "Original", "Basic")

    val all: List<FilterDef> = listOf(
        ORIGINAL,
        // Premium: subject-aware (person stays sharp / in colour, the background changes)
        FilterDef("splash", "Color Splash", "Premium", contrast = 0.05f, mode = 1, swatchOverride = 0xFFFF4D6D.toInt()),
        FilterDef("portrait", "Portrait Blur", "Premium", exposure = 0.05f, mode = 2, bgAmount = 1f, swatchOverride = 0xFF8FB8DE.toInt()),
        FilterDef("spotlight", "Spotlight", "Premium", contrast = 0.10f, vignette = 0.20f, mode = 3, swatchOverride = 0xFF2B2B35.toInt()),
        FilterDef("reverse", "Reverse Splash", "Premium", mode = 4, swatchOverride = 0xFF7AD3C1.toInt()),
        FilterDef("neon", "Neon Duotone", "Premium", saturation = 0.10f, mode = 5, swatchOverride = 0xFFB04CE0.toInt()),
        FilterDef("highkey_bg", "Airy White", "Premium", exposure = 0.08f, mode = 6, swatchOverride = 0xFFF4EFEA.toInt()),
        FilterDef("warmcool", "Warm & Cool", "Premium", contrast = 0.08f, mode = 7, swatchOverride = 0xFFFFA45C.toInt()),
        // Premium colour grades
        FilterDef("cinematic", "Cinematic", "Premium", contrast = 0.25f, saturation = 0.05f, vignette = 0.28f, shadow = 0x00415A, highlight = 0xFFB070, fade = 0.04f),
        FilterDef("dreamy", "Dreamy", "Premium", exposure = 0.15f, contrast = -0.15f, saturation = 0.05f, tint = 0.08f, fade = 0.07f, highlight = 0xFFD6E8, glow = 0.65f),
        FilterDef("matte_black", "Matte Black", "Premium", exposure = -0.05f, contrast = 0.30f, saturation = -0.35f, fade = 0.12f, vignette = 0.25f, shadow = 0x10141C),
        // Warm
        FilterDef("sunrise", "Sunrise", "Warm", exposure = 0.05f, saturation = 0.10f, temp = 0.55f, fade = 0.02f, highlight = 0xFFD9A0),
        FilterDef("honey", "Honey", "Warm", contrast = 0.05f, saturation = 0.15f, temp = 0.70f, shadow = 0x7A3B00),
        FilterDef("golden", "Golden Hour", "Warm", exposure = 0.10f, saturation = 0.20f, temp = 0.80f, vignette = 0.15f, highlight = 0xFFC266),
        FilterDef("amber", "Amber", "Warm", contrast = 0.15f, saturation = -0.05f, temp = 0.50f, fade = 0.05f, shadow = 0x5A2A00),
        // Cool
        FilterDef("frost", "Frost", "Cool", exposure = 0.10f, saturation = -0.10f, temp = -0.50f, highlight = 0xC8E6FF),
        FilterDef("ocean", "Ocean", "Cool", contrast = 0.10f, saturation = 0.10f, temp = -0.60f, tint = -0.10f, shadow = 0x003B5C),
        FilterDef("moonlight", "Moonlight", "Cool", exposure = -0.10f, contrast = 0.15f, saturation = -0.20f, temp = -0.40f, vignette = 0.20f, shadow = 0x1A2A5A),
        // Film
        FilterDef("film_gold", "Film Gold", "Film", contrast = 0.15f, saturation = -0.10f, temp = 0.25f, fade = 0.06f, vignette = 0.15f, shadow = 0x1E3322),
        FilterDef("film_soft", "Film Soft", "Film", contrast = -0.10f, saturation = -0.10f, temp = 0.10f, fade = 0.08f),
        FilterDef("film_matte", "Film Matte", "Film", contrast = 0.05f, saturation = -0.20f, fade = 0.14f),
        FilterDef("cine_teal", "Cine Teal", "Film", contrast = 0.20f, saturation = 0.05f, shadow = 0x003A4A, highlight = 0xFFB070),
        // Vintage
        FilterDef("retro70", "Retro 70s", "Vintage", contrast = -0.05f, saturation = -0.15f, temp = 0.40f, fade = 0.12f, vignette = 0.25f, shadow = 0x5A3A1A),
        FilterDef("sepia", "Sepia", "Vintage", contrast = 0.05f, temp = 0.60f, fade = 0.05f, vignette = 0.20f, shadow = 0x3A2000, highlight = 0xFFE0B0, bw = 0.85f),
        FilterDef("polaroid", "Polaroid", "Vintage", contrast = -0.08f, saturation = -0.05f, temp = 0.20f, tint = 0.10f, fade = 0.10f, vignette = 0.20f, shadow = 0x003A5A),
        FilterDef("faded_rose", "Faded Rose", "Vintage", saturation = -0.10f, temp = 0.10f, tint = 0.25f, fade = 0.15f, highlight = 0xFFB6C8),
        // K-Beauty
        FilterDef("soft_glow", "Soft Glow", "K-Beauty", exposure = 0.12f, contrast = -0.12f, saturation = -0.05f, fade = 0.03f, highlight = 0xFFE8E8, glow = 0.50f),
        FilterDef("milk_tea", "Milk Tea", "K-Beauty", exposure = 0.10f, contrast = -0.15f, saturation = -0.20f, temp = 0.20f, fade = 0.08f, highlight = 0xFFE6CC, glow = 0.35f),
        FilterDef("peach", "Peach Glow", "K-Beauty", exposure = 0.12f, saturation = 0.05f, temp = 0.25f, tint = 0.10f, highlight = 0xFFC9B0, glow = 0.45f),
        FilterDef("cloud", "Cloud Skin", "K-Beauty", exposure = 0.20f, contrast = -0.20f, saturation = -0.12f, temp = -0.05f, fade = 0.06f, glow = 0.55f),
        // Black & white
        FilterDef("mono", "Mono", "B&W", contrast = 0.10f, bw = 1f),
        FilterDef("noir", "Noir", "B&W", exposure = -0.10f, contrast = 0.45f, vignette = 0.30f, bw = 1f),
        FilterDef("silver", "Silver", "B&W", exposure = 0.10f, contrast = 0.05f, fade = 0.06f, highlight = 0xEEF3FF, bw = 1f),
        FilterDef("high_key", "High Key", "B&W", exposure = 0.35f, contrast = -0.05f, fade = 0.02f, bw = 1f),
        // Vivid
        FilterDef("vivid", "Vivid", "Vivid", contrast = 0.15f, saturation = 0.35f),
        FilterDef("pop", "Pop", "Vivid", exposure = 0.05f, contrast = 0.25f, saturation = 0.50f),
        FilterDef("fresh", "Fresh", "Vivid", exposure = 0.10f, contrast = 0.08f, saturation = 0.20f, temp = -0.10f),
    )
}
