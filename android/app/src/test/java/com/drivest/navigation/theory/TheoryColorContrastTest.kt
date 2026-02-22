package com.drivest.navigation.theory

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TheoryColorContrastTest {

    @Test
    fun dayPaletteMaintainsReadableContrast() {
        val colors = parseColors(File("src/main/res/values/colors.xml"))
        assertContrastAtLeast(colors, "theory_text_primary", "theory_bg", 7.0)
        assertContrastAtLeast(colors, "theory_text_secondary", "theory_bg", 4.5)
    }

    @Test
    fun nightPaletteMaintainsReadableContrast() {
        val fallback = parseColors(File("src/main/res/values/colors.xml"))
        val night = parseColors(File("src/main/res/values-night/colors.xml"))
        val merged = fallback + night
        assertContrastAtLeast(merged, "theory_text_primary", "theory_bg", 7.0)
        assertContrastAtLeast(merged, "theory_text_secondary", "theory_bg", 4.5)
    }

    private fun assertContrastAtLeast(
        colors: Map<String, String>,
        foregroundName: String,
        backgroundName: String,
        minimum: Double
    ) {
        val foreground = colors[foregroundName] ?: error("Missing color: $foregroundName")
        val background = colors[backgroundName] ?: error("Missing color: $backgroundName")
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "Contrast $ratio is below $minimum for $foregroundName on $backgroundName",
            ratio >= minimum
        )
    }

    private fun parseColors(file: File): Map<String, String> {
        require(file.exists()) { "Missing file: ${file.path}" }
        val regex = Regex("<color\\s+name=\"([^\"]+)\">\\s*(#[0-9A-Fa-f]{6,8})\\s*</color>")
        return regex.findAll(file.readText())
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    private fun contrastRatio(foregroundHex: String, backgroundHex: String): Double {
        val lum1 = relativeLuminance(parseRgb(foregroundHex))
        val lum2 = relativeLuminance(parseRgb(backgroundHex))
        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun parseRgb(hex: String): Triple<Int, Int, Int> {
        val raw = hex.removePrefix("#")
        val rgb = when (raw.length) {
            8 -> raw.substring(2)
            6 -> raw
            else -> error("Unsupported color format: $hex")
        }
        return Triple(
            rgb.substring(0, 2).toInt(16),
            rgb.substring(2, 4).toInt(16),
            rgb.substring(4, 6).toInt(16)
        )
    }

    private fun relativeLuminance(rgb: Triple<Int, Int, Int>): Double {
        val (r, g, b) = rgb
        val sr = linearize(r / 255.0)
        val sg = linearize(g / 255.0)
        val sb = linearize(b / 255.0)
        return 0.2126 * sr + 0.7152 * sg + 0.0722 * sb
    }

    private fun linearize(channel: Double): Double {
        return if (channel <= 0.03928) {
            channel / 12.92
        } else {
            Math.pow((channel + 0.055) / 1.055, 2.4)
        }
    }
}
