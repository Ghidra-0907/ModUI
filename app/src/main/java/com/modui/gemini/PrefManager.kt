package com.modui.gemini

import android.content.Context
import android.content.SharedPreferences
import java.io.File

enum class BgType {
    STATIC,
    GIF,
    VIDEO
}

enum class BgSource {
    PRESET,
    CUSTOM
}

data class BgPreset(
    val id: String,
    val title: String,
    val assetFileName: String,
    val type: BgType
) {
    val assetUri: String
        get() = "file:///android_asset/presets/$assetFileName"
}

data class LedColorOption(
    val hex: String,
    val label: String,
    val needsDarkCheck: Boolean = false
)

class PrefManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var isModEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MOD_ENABLED, value).apply()

    var bgType: BgType
        get() {
            val raw = prefs.getString(KEY_BG_TYPE, BgType.STATIC.name) ?: BgType.STATIC.name
            return runCatching { BgType.valueOf(raw) }.getOrDefault(BgType.STATIC)
        }
        set(value) = prefs.edit().putString(KEY_BG_TYPE, value.name).apply()

    var bgSource: BgSource
        get() {
            val raw = prefs.getString(KEY_BG_SOURCE, BgSource.PRESET.name) ?: BgSource.PRESET.name
            return runCatching { BgSource.valueOf(raw) }.getOrDefault(BgSource.PRESET)
        }
        set(value) = prefs.edit().putString(KEY_BG_SOURCE, value.name).apply()

    var selectedPresetId: String
        get() = prefs.getString(KEY_SELECTED_PRESET_ID, STATIC_PRESETS.first().id)
            ?: STATIC_PRESETS.first().id
        set(value) = prefs.edit().putString(KEY_SELECTED_PRESET_ID, value).apply()

    /** Glass blur radius in px: 0..30 */
    var glassBlurPx: Int
        get() = prefs.getInt(KEY_GLASS_BLUR, DEFAULT_GLASS_BLUR).coerceIn(0, 30)
        set(value) = prefs.edit().putInt(KEY_GLASS_BLUR, value.coerceIn(0, 30)).apply()

    /** Overlay dim percentage: 0..80 */
    var overlayDimPercent: Int
        get() = prefs.getInt(KEY_OVERLAY_DIM, DEFAULT_OVERLAY_DIM).coerceIn(0, 80)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_DIM, value.coerceIn(0, 80)).apply()

    var isAmbientGlowEnabled: Boolean
        get() = prefs.getBoolean(KEY_AMBIENT_GLOW, true)
        set(value) = prefs.edit().putBoolean(KEY_AMBIENT_GLOW, value).apply()

    /** Selected LED hex colors stored as comma-separated string, e.g. "#38bdf8,#a855f7,#ec4899" */
    var ledColors: List<String>
        get() {
            val raw = prefs.getString(KEY_LED_COLORS, DEFAULT_LED_COLORS_CSV) ?: DEFAULT_LED_COLORS_CSV
            val parsed = raw.split(",")
                .map { it.trim().lowercase() }
                .filter { it.matches(HEX_COLOR_REGEX) }
                .distinct()
            return if (parsed.isEmpty()) DEFAULT_LED_COLORS else parsed
        }
        set(value) {
            val cleaned = value.map { it.trim().lowercase() }
                .filter { it.matches(HEX_COLOR_REGEX) }
                .distinct()
            val toSave = if (cleaned.isEmpty()) listOf(DEFAULT_LED_FALLBACK_COLOR) else cleaned
            prefs.edit().putString(KEY_LED_COLORS, toSave.joinToString(",")).apply()
        }

    var isHideSuggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SUGGESTIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_SUGGESTIONS, value).apply()

    /** Tracks last custom animated type imported (GIF or VIDEO) for Tab 2 custom card */
    var lastCustomAnimatedType: BgType
        get() {
            val raw = prefs.getString(KEY_LAST_CUSTOM_ANIMATED_TYPE, BgType.GIF.name)
            return if (raw == BgType.VIDEO.name) BgType.VIDEO else BgType.GIF
        }
        set(value) {
            if (value == BgType.GIF || value == BgType.VIDEO) {
                prefs.edit().putString(KEY_LAST_CUSTOM_ANIMATED_TYPE, value.name).apply()
            }
        }

    /** Cache-busting timestamp incremented when user imports a custom file */
    var customMediaVersion: Long
        get() = prefs.getLong(KEY_CUSTOM_MEDIA_VERSION, 0L)
        set(value) = prefs.edit().putLong(KEY_CUSTOM_MEDIA_VERSION, value).apply()

    fun getStaticBgFile(): File = File(appContext.filesDir, FILE_STATIC_BG)

    fun getAnimatedGifFile(): File = File(appContext.filesDir, FILE_ANIMATED_GIF)

    fun getAnimatedVideoFile(): File = File(appContext.filesDir, FILE_ANIMATED_VIDEO)

    fun getSelectedPreset(): BgPreset? {
        return ALL_PRESETS.firstOrNull { it.id == selectedPresetId }
    }

    fun getCustomAnimatedFile(): File? {
        val primary = if (lastCustomAnimatedType == BgType.VIDEO) {
            getAnimatedVideoFile()
        } else {
            getAnimatedGifFile()
        }
        if (primary.exists() && primary.length() > 0L) return primary

        val fallbackVideo = getAnimatedVideoFile()
        if (fallbackVideo.exists() && fallbackVideo.length() > 0L) return fallbackVideo

        val fallbackGif = getAnimatedGifFile()
        if (fallbackGif.exists() && fallbackGif.length() > 0L) return fallbackGif

        return null
    }

    fun saveAll(
        modEnabled: Boolean,
        type: BgType,
        source: BgSource,
        presetId: String,
        blurPx: Int,
        dimPercent: Int,
        ambientGlow: Boolean,
        selectedLedColors: List<String>,
        hideSuggestions: Boolean
    ) {
        val cleanedColors = selectedLedColors
            .map { it.trim().lowercase() }
            .filter { it.matches(HEX_COLOR_REGEX) }
            .distinct()
            .ifEmpty { listOf(DEFAULT_LED_FALLBACK_COLOR) }

        prefs.edit()
            .putBoolean(KEY_MOD_ENABLED, modEnabled)
            .putString(KEY_BG_TYPE, type.name)
            .putString(KEY_BG_SOURCE, source.name)
            .putString(KEY_SELECTED_PRESET_ID, presetId)
            .putInt(KEY_GLASS_BLUR, blurPx.coerceIn(0, 30))
            .putInt(KEY_OVERLAY_DIM, dimPercent.coerceIn(0, 80))
            .putBoolean(KEY_AMBIENT_GLOW, ambientGlow)
            .putString(KEY_LED_COLORS, cleanedColors.joinToString(","))
            .putBoolean(KEY_HIDE_SUGGESTIONS, hideSuggestions)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "mod_ui_prefs"

        private const val KEY_MOD_ENABLED = "mod_enabled"
        private const val KEY_BG_TYPE = "bg_type"
        private const val KEY_BG_SOURCE = "bg_source"
        private const val KEY_SELECTED_PRESET_ID = "selected_preset_id"
        private const val KEY_GLASS_BLUR = "glass_blur_px"
        private const val KEY_OVERLAY_DIM = "overlay_dim_percent"
        private const val KEY_AMBIENT_GLOW = "ambient_glow"
        private const val KEY_LED_COLORS = "led_colors"
        private const val KEY_HIDE_SUGGESTIONS = "hide_suggestions"
        private const val KEY_LAST_CUSTOM_ANIMATED_TYPE = "last_custom_animated_type"
        private const val KEY_CUSTOM_MEDIA_VERSION = "custom_media_version"

        private val HEX_COLOR_REGEX = Regex("^#[0-9a-f]{6}$")

        const val DEFAULT_GLASS_BLUR = 20
        const val DEFAULT_OVERLAY_DIM = 35
        const val DEFAULT_LED_FALLBACK_COLOR = "#38bdf8"
        const val DEFAULT_LED_COLORS_CSV = "#38bdf8,#a855f7,#ec4899"

        val DEFAULT_LED_COLORS = listOf("#38bdf8", "#a855f7", "#ec4899")

        val LED_COLOR_OPTIONS = listOf(
            LedColorOption("#38bdf8", "Xanh Cyan", needsDarkCheck = true),
            LedColorOption("#3b82f6", "Xanh Dương", needsDarkCheck = false),
            LedColorOption("#a855f7", "Tím Neon", needsDarkCheck = false),
            LedColorOption("#ec4899", "Hồng Magenta", needsDarkCheck = false),
            LedColorOption("#f97316", "Đỏ Cam", needsDarkCheck = false),
            LedColorOption("#22c55e", "Xanh Lá", needsDarkCheck = true),
            LedColorOption("#f8fafc", "Trắng Băng", needsDarkCheck = true)
        )

        const val FILE_STATIC_BG = "bg_static.jpg"
        const val FILE_ANIMATED_GIF = "bg_animated.gif"
        const val FILE_ANIMATED_VIDEO = "bg_animated.mp4"

        val STATIC_PRESETS = listOf(
            BgPreset("static_aurora", "Cực Quang", "static_aurora.png", BgType.STATIC),
            BgPreset("static_nebula", "Tinh Vân", "static_nebula.png", BgType.STATIC),
            BgPreset("static_sunset", "Hoàng Hôn", "static_sunset.png", BgType.STATIC),
            BgPreset("static_ocean", "Biển Đêm", "static_ocean.png", BgType.STATIC)
        )

        val ANIMATED_PRESETS = listOf(
            BgPreset("anim_aurora", "Sóng Cực Quang", "anim_aurora.gif", BgType.GIF),
            BgPreset("anim_liquid", "Lỏng Ánh Cam", "anim_liquid.gif", BgType.GIF),
            BgPreset("anim_cyber", "Ánh Sáng Neon", "anim_cyber.gif", BgType.GIF)
        )

        val ALL_PRESETS = STATIC_PRESETS + ANIMATED_PRESETS
    }
}
