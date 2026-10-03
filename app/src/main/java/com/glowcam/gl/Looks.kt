package com.glowcam.gl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A one-tap style: beauty + makeup + filter + background in a single preset. */
data class Look(val id: String, val name: String, val colorA: Int, val colorB: Int, val apply: (EffectParams) -> EffectParams)

object Looks {
    private fun filter(id: String) = Filters.all.first { it.id == id }

    /** Clears everything a look controls (beauty, makeup, filter, background) but keeps edits like crop and retouch. */
    fun cleared(p: EffectParams) = p.copy(
        smooth = 0f, brighten = 0f, slim = 0f, eyes = 0f, jaw = 0f,
        filter = Filters.ORIGINAL, filterIntensity = 1f,
        lip = 0f, blush = 0f, brow = 0f, eyeShadow = 0f,
        bgMode = 0,
    )

    val presets: List<Look> = listOf(
        Look("natural", "Natural Glow", 0xFFFFD6C2.toInt(), 0xFFFF9EB5.toInt()) {
            cleared(it).copy(
                smooth = 0.35f, brighten = 0.25f, blush = 0.25f, blushColor = 0xFFFF9E80.toInt(),
                lip = 0.28f, lipColor = 0xFFC98B7B.toInt(), filter = filter("soft_glow"), filterIntensity = 0.5f,
            )
        },
        Look("korean", "Korean Soft", 0xFFFFE3E8.toInt(), 0xFFFFB3C6.toInt()) {
            cleared(it).copy(
                smooth = 0.5f, brighten = 0.35f, eyes = 0.2f, blush = 0.3f, blushColor = 0xFFFF7A9A.toInt(),
                lip = 0.35f, lipColor = 0xFFFF6F61.toInt(), filter = filter("peach"), filterIntensity = 0.6f,
            )
        },
        Look("glam", "Glam Night", 0xFF4A1942.toInt(), 0xFFB5179E.toInt()) {
            cleared(it).copy(
                smooth = 0.4f, slim = 0.2f, jaw = 0.2f, lip = 0.6f, lipColor = 0xFF8E1B4A.toInt(),
                eyeShadow = 0.45f, shadowColor = 0xFF6A1B5A.toInt(), brow = 0.4f, filter = filter("cinematic"), filterIntensity = 0.5f,
            )
        },
        Look("fresh", "Fresh Morning", 0xFFD8F3DC.toInt(), 0xFFB7E4C7.toInt()) {
            cleared(it).copy(
                smooth = 0.25f, brighten = 0.4f, blush = 0.2f, blushColor = 0xFFFF9E80.toInt(),
                lip = 0.2f, lipColor = 0xFFFF6F61.toInt(), filter = filter("fresh"), filterIntensity = 0.6f,
            )
        },
        Look("bold_red", "Bold Red", 0xFFFFC2C2.toInt(), 0xFFC62828.toInt()) {
            cleared(it).copy(smooth = 0.25f, lip = 0.8f, lipColor = 0xFFC62828.toInt(), brow = 0.3f, brighten = 0.15f)
        },
        Look("studio", "Studio Pro", 0xFFE9ECEF.toInt(), 0xFF868E96.toInt()) {
            cleared(it).copy(smooth = 0.3f, brighten = 0.2f, slim = 0.1f, filter = filter("film_soft"), filterIntensity = 0.45f)
        },
        Look("retro", "Retro Film", 0xFFFFE8CC.toInt(), 0xFFD4A24C.toInt()) {
            cleared(it).copy(smooth = 0.2f, lip = 0.3f, lipColor = 0xFFC62828.toInt(), filter = filter("film_gold"), filterIntensity = 0.8f)
        },
        Look("portrait", "Portrait Pop", 0xFFCFE8FF.toInt(), 0xFF6CA8E8.toInt()) {
            cleared(it).copy(smooth = 0.3f, brighten = 0.2f, filter = filter("portrait"), filterIntensity = 1f)
        },
    )
}

/** A look the user saved, kept as JSON in SharedPreferences. */
data class UserLook(val id: String, val name: String, val json: JSONObject)

object LookStore {
    private fun prefs(context: Context) = context.getSharedPreferences("glowcam_looks", Context.MODE_PRIVATE)

    fun toJson(p: EffectParams) = JSONObject().apply {
        put("smooth", p.smooth.toDouble()); put("brighten", p.brighten.toDouble()); put("slim", p.slim.toDouble())
        put("eyes", p.eyes.toDouble()); put("jaw", p.jaw.toDouble())
        put("filter", p.filter.id); put("filterIntensity", p.filterIntensity.toDouble())
        put("lip", p.lip.toDouble()); put("lipColor", p.lipColor)
        put("blush", p.blush.toDouble()); put("blushColor", p.blushColor)
        put("brow", p.brow.toDouble()); put("browColor", p.browColor)
        put("eyeShadow", p.eyeShadow.toDouble()); put("shadowColor", p.shadowColor)
        put("bgMode", p.bgMode); put("bgColor1", p.bgColor1); put("bgColor2", p.bgColor2); put("bgBlur", p.bgBlur.toDouble())
    }

    fun fromJson(base: EffectParams, o: JSONObject): EffectParams = Looks.cleared(base).copy(
        smooth = o.optDouble("smooth", 0.0).toFloat(), brighten = o.optDouble("brighten", 0.0).toFloat(),
        slim = o.optDouble("slim", 0.0).toFloat(), eyes = o.optDouble("eyes", 0.0).toFloat(), jaw = o.optDouble("jaw", 0.0).toFloat(),
        filter = Filters.all.firstOrNull { it.id == o.optString("filter") } ?: Filters.ORIGINAL,
        filterIntensity = o.optDouble("filterIntensity", 1.0).toFloat(),
        lip = o.optDouble("lip", 0.0).toFloat(), lipColor = o.optInt("lipColor", base.lipColor),
        blush = o.optDouble("blush", 0.0).toFloat(), blushColor = o.optInt("blushColor", base.blushColor),
        brow = o.optDouble("brow", 0.0).toFloat(), browColor = o.optInt("browColor", base.browColor),
        eyeShadow = o.optDouble("eyeShadow", 0.0).toFloat(), shadowColor = o.optInt("shadowColor", base.shadowColor),
        bgMode = o.optInt("bgMode", 0), bgColor1 = o.optInt("bgColor1", base.bgColor1),
        bgColor2 = o.optInt("bgColor2", base.bgColor2), bgBlur = o.optDouble("bgBlur", 0.7).toFloat(),
    )

    fun load(context: Context): List<UserLook> = try {
        val arr = JSONArray(prefs(context).getString("looks", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            UserLook(o.getString("id"), o.getString("name"), o.getJSONObject("look"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(context: Context, name: String, p: EffectParams) {
        val arr = JSONArray()
        for (l in load(context)) arr.put(JSONObject().put("id", l.id).put("name", l.name).put("look", l.json))
        arr.put(JSONObject().put("id", System.currentTimeMillis().toString()).put("name", name.take(24)).put("look", toJson(p)))
        prefs(context).edit().putString("looks", arr.toString()).apply()
    }

    fun delete(context: Context, id: String) {
        val arr = JSONArray()
        for (l in load(context)) if (l.id != id) arr.put(JSONObject().put("id", l.id).put("name", l.name).put("look", l.json))
        prefs(context).edit().putString("looks", arr.toString()).apply()
    }
}
