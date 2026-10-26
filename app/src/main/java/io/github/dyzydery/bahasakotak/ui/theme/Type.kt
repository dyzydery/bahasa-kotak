package io.github.dyzydery.bahasakotak.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.dyzydery.bahasakotak.R

@OptIn(ExperimentalTextApi::class) // variationSettings on resource fonts
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Plus Jakarta Sans (variable, OFL; license in assets/licenses). */
val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

// Material's default tracking (up to 0.5sp) is tuned for Roboto; Plus Jakarta Sans is already open, so drop it.
private fun TextStyle.jakarta(weight: FontWeight? = null) =
    copy(fontFamily = Jakarta, fontWeight = weight ?: fontWeight, letterSpacing = 0.sp)

val AppTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.jakarta(FontWeight.ExtraBold),
        displayMedium = displayMedium.jakarta(FontWeight.ExtraBold),
        displaySmall = displaySmall.jakarta(FontWeight.Bold),
        headlineLarge = headlineLarge.jakarta(FontWeight.Bold),
        headlineMedium = headlineMedium.jakarta(FontWeight.Bold),
        headlineSmall = headlineSmall.jakarta(FontWeight.SemiBold),
        titleLarge = titleLarge.jakarta(FontWeight.Bold),
        titleMedium = titleMedium.jakarta(FontWeight.SemiBold),
        titleSmall = titleSmall.jakarta(FontWeight.SemiBold),
        bodyLarge = bodyLarge.jakarta(),
        bodyMedium = bodyMedium.jakarta(),
        bodySmall = bodySmall.jakarta(),
        labelLarge = labelLarge.jakarta(FontWeight.SemiBold),
        labelMedium = labelMedium.jakarta(FontWeight.SemiBold),
        labelSmall = labelSmall.jakarta(FontWeight.Medium),
    )
}
