package io.github.bluestormdna.kocoboy.ui.dmg

import androidx.compose.ui.graphics.Color

val playItLoudThemeList = listOf(
    VibrantYellowColorTheme,
    GorgeousGreenColorTheme,
    RadiantRedColorTheme,
    CoolBlueColorTheme,
    TraditionalWhiteColorTheme,
    DeepBlackColorTheme,
)

val themeList = playItLoudThemeList + ClassicColorTheme

sealed class ColorTheme(
    val body: Color,
    val bezel: Color,
    val bezelTopLine: Color,
    val bezelBottomLine: Color,
    val bezelText: Color,
    val bodyText: Color,
    val mainButtons: Color,
    val rubberButtons: Color,
    val screenBackground: Color,
    val bodyBottomRightCornerShadow: Color,
    val accentDark: Color,
    val internalDark: Color,
)

data object ClassicColorTheme : ColorTheme(
    body = ClassicGrayBody,
    bezel = GrayBezel,
    bezelTopLine = WashedPurple,
    bezelBottomLine = WashedBlue,
    bezelText = ClassicGrayBody,
    bodyText = Blue,
    mainButtons = Purple,
    rubberButtons = SelectStartColor,
    screenBackground = Olive,
    bodyBottomRightCornerShadow = BodyShadow,
    accentDark = GrayAccentDark,
    internalDark = InternalGray,
)

data object VibrantYellowColorTheme : ColorTheme(
    body = YellowBody,
    bezel = DarkGrayComponent,
    bezelTopLine = WashedPurple,
    bezelBottomLine = WashedBlue,
    bezelText = ClassicGrayBody,
    bodyText = DarkGrayComponent,
    mainButtons = DarkGrayComponent,
    rubberButtons = DarkGrayComponent,
    screenBackground = Olive,
    bodyBottomRightCornerShadow = BodyShadow,
    accentDark = GrayAccentDark,
    internalDark = InternalGray,
)

data object GorgeousGreenColorTheme : ColorTheme(
    body = GreenBody,
    bezel = DarkGrayComponent,
    bezelTopLine = WashedPurple,
    bezelBottomLine = WashedBlue,
    bezelText = ClassicGrayBody,
    bodyText = DarkGrayComponent,
    mainButtons = DarkGrayComponent,
    rubberButtons = DarkGrayComponent,
    screenBackground = Olive,
    bodyBottomRightCornerShadow = BodyShadow,
    accentDark = GrayAccentDark,
    internalDark = InternalGray,
)

data object RadiantRedColorTheme : ColorTheme(
    body = RedBody,
    bezel = DarkGrayComponent,
    bezelTopLine = WashedPurple,
    bezelBottomLine = WashedBlue,
    bezelText = ClassicGrayBody,
    bodyText = DarkGrayComponent,
    mainButtons = DarkGrayComponent,
    rubberButtons = DarkGrayComponent,
    screenBackground = Olive,
    bodyBottomRightCornerShadow = BodyShadow,
    accentDark = GrayAccentDark,
    internalDark = InternalGray,
)

data object CoolBlueColorTheme : ColorTheme(
    body = BlueBody,
    bezel = DarkGrayComponent,
    bezelTopLine = WashedPurple,
    bezelBottomLine = WashedBlue,
    bezelText = ClassicGrayBody,
    bodyText = DarkGrayComponent,
    mainButtons = DarkGrayComponent,
    rubberButtons = DarkGrayComponent,
    screenBackground = Olive,
    bodyBottomRightCornerShadow = BodyShadow,
    accentDark = GrayAccentDark,
    internalDark = InternalGray,
)

data object DeepBlackColorTheme : ColorTheme(
    body = BlackBody,
    bezel = DarkGrayComponent,
    bezelTopLine = WashedPurple,
    bezelBottomLine = WashedBlue,
    bezelText = ClassicGrayBody,
    bodyText = WashedPurple, // TODO
    mainButtons = DarkGrayComponent,
    rubberButtons = DarkGrayComponent,
    screenBackground = Olive,
    bodyBottomRightCornerShadow = BodyShadow,
    accentDark = GrayAccentDark,
    internalDark = InternalGray,
)

data object TraditionalWhiteColorTheme : ColorTheme(
    body = WhiteBody,
    bezel = DarkGrayComponent,
    bezelTopLine = WashedPurple,
    bezelBottomLine = WashedBlue,
    bezelText = ClassicGrayBody,
    bodyText = DarkGrayComponent,
    mainButtons = DarkGrayComponent,
    rubberButtons = DarkGrayComponent,
    screenBackground = Olive,
    bodyBottomRightCornerShadow = BodyShadow,
    accentDark = GrayAccentDark,
    internalDark = InternalGray,
)

interface ScreenTheme {
    val id0: Int
    val id1: Int
    val id2: Int
    val id3: Int
}

object ClassicScreenTheme : ScreenTheme {
    override val id0 = 0xFF8BAC0F.toInt()
    override val id1 = 0xFF8BAC0F.toInt()
    override val id2 = 0xFF306230.toInt()
    override val id3 = 0xFF0F380F.toInt()
}

object PocketScreenTheme : ScreenTheme {
    override val id0 = 0xFFFFFFFF.toInt()
    override val id1 = 0xFFA9A9A9.toInt()
    override val id2 = 0xFF545454.toInt()
    override val id3 = 0xFF000000.toInt()
}