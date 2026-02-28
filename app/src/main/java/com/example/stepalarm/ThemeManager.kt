package com.example.stepalarm

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

/**
 * Manages color palettes for the app.
 * Stores the user's selected palette in SharedPreferences and provides
 * color values for each palette to be applied programmatically.
 */
object ThemeManager {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_PALETTE = "selected_palette"

    const val PALETTE_SOOTHING_DAWN = "soothing_dawn"
    const val PALETTE_MATERIAL_DEEP_DARK = "material_deep_dark"
    const val PALETTE_SUNSET_ALARM = "sunset_alarm"
    const val PALETTE_PURE_WHITE = "pure_white"

    // Default palette
    private const val DEFAULT_PALETTE = PALETTE_SOOTHING_DAWN

    data class Palette(
        val name: String,
        val background: Int,
        val textPrimary: Int,
        val accent: Int,
        val subtleAccent: Int,
        val statusBar: Int,
        val cardBackground: Int,
        val cardStroke: Int,
        val switchTrackOn: Int,
        val switchTrackOff: Int,
        val fabBackground: Int,
        val fabIcon: Int,
        val toolbarBackground: Int,
        val toolbarText: Int,
        val buttonBackground: Int,
        val buttonText: Int,
        val overlayBackground: Int
    )

    val soothingDawn = Palette(
        name = "Soothing Dawn",
        background = Color.parseColor("#0A192F"),
        textPrimary = Color.parseColor("#E6F1FF"),
        accent = Color.parseColor("#64FFDA"),
        subtleAccent = Color.parseColor("#8892B0"),
        statusBar = Color.parseColor("#060F1F"),
        cardBackground = Color.parseColor("#112240"),
        cardStroke = Color.parseColor("#1D3461"),
        switchTrackOn = Color.parseColor("#64FFDA"),
        switchTrackOff = Color.parseColor("#8892B0"),
        fabBackground = Color.parseColor("#64FFDA"),
        fabIcon = Color.parseColor("#0A192F"),
        toolbarBackground = Color.parseColor("#0A192F"),
        toolbarText = Color.parseColor("#E6F1FF"),
        buttonBackground = Color.parseColor("#64FFDA"),
        buttonText = Color.parseColor("#0A192F"),
        overlayBackground = Color.parseColor("#CC0A192F")
    )

    val materialDeepDark = Palette(
        name = "Material Deep Dark",
        background = Color.parseColor("#121212"),
        textPrimary = Color.parseColor("#FFFFFF"),
        accent = Color.parseColor("#BB86FC"),
        subtleAccent = Color.parseColor("#81D4FA"),
        statusBar = Color.parseColor("#000000"),
        cardBackground = Color.parseColor("#1E1E1E"),
        cardStroke = Color.parseColor("#333333"),
        switchTrackOn = Color.parseColor("#03DAC6"),
        switchTrackOff = Color.parseColor("#555555"),
        fabBackground = Color.parseColor("#BB86FC"),
        fabIcon = Color.parseColor("#000000"),
        toolbarBackground = Color.parseColor("#1E1E1E"),
        toolbarText = Color.parseColor("#FFFFFF"),
        buttonBackground = Color.parseColor("#BB86FC"),
        buttonText = Color.parseColor("#000000"),
        overlayBackground = Color.parseColor("#CC121212")
    )

    val sunsetAlarm = Palette(
        name = "Sunset Alarm",
        background = Color.parseColor("#2B1A1A"),
        textPrimary = Color.parseColor("#FFFDD0"),
        accent = Color.parseColor("#FFAB40"),
        subtleAccent = Color.parseColor("#FF5252"),
        statusBar = Color.parseColor("#1A0F0F"),
        cardBackground = Color.parseColor("#3D2626"),
        cardStroke = Color.parseColor("#5C3A3A"),
        switchTrackOn = Color.parseColor("#FFAB40"),
        switchTrackOff = Color.parseColor("#5C3A3A"),
        fabBackground = Color.parseColor("#FFAB40"),
        fabIcon = Color.parseColor("#2B1A1A"),
        toolbarBackground = Color.parseColor("#2B1A1A"),
        toolbarText = Color.parseColor("#FFFDD0"),
        buttonBackground = Color.parseColor("#FFAB40"),
        buttonText = Color.parseColor("#2B1A1A"),
        overlayBackground = Color.parseColor("#CC2B1A1A")
    )

    val pureWhite = Palette(
        name = "Pure White",
        background = Color.parseColor("#F5F5F5"),
        textPrimary = Color.parseColor("#212121"),
        accent = Color.parseColor("#3F51B5"),
        subtleAccent = Color.parseColor("#03A9F4"),
        statusBar = Color.parseColor("#303F9F"),
        cardBackground = Color.parseColor("#FFFFFF"),
        cardStroke = Color.parseColor("#E0E0E0"),
        switchTrackOn = Color.parseColor("#03A9F4"),
        switchTrackOff = Color.parseColor("#BDBDBD"),
        fabBackground = Color.parseColor("#3F51B5"),
        fabIcon = Color.parseColor("#FFFFFF"),
        toolbarBackground = Color.parseColor("#3F51B5"),
        toolbarText = Color.parseColor("#FFFFFF"),
        buttonBackground = Color.parseColor("#3F51B5"),
        buttonText = Color.parseColor("#FFFFFF"),
        overlayBackground = Color.parseColor("#CCF5F5F5")
    )

    private val palettes = mapOf(
        PALETTE_SOOTHING_DAWN to soothingDawn,
        PALETTE_MATERIAL_DEEP_DARK to materialDeepDark,
        PALETTE_SUNSET_ALARM to sunsetAlarm,
        PALETTE_PURE_WHITE to pureWhite
    )

    fun getSelectedPaletteKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PALETTE, DEFAULT_PALETTE) ?: DEFAULT_PALETTE
    }

    fun setSelectedPalette(context: Context, paletteKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PALETTE, paletteKey).apply()
    }

    fun getSelectedPalette(context: Context): Palette {
        val key = getSelectedPaletteKey(context)
        return palettes[key] ?: soothingDawn
    }

    fun getAllPalettes(): Map<String, Palette> = palettes

    fun getPalette(key: String): Palette = palettes[key] ?: soothingDawn
}
