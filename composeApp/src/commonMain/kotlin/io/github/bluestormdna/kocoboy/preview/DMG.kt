package io.github.bluestormdna.kocoboy.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.github.bluestormdna.kocoboy.ui.dmg.BrandLabelling
import io.github.bluestormdna.kocoboy.ui.dmg.ColorTheme
import io.github.bluestormdna.kocoboy.ui.dmg.GameBoy
import io.github.bluestormdna.kocoboy.ui.dmg.GamePad
import io.github.bluestormdna.kocoboy.ui.dmg.HeadPhoneJack
import io.github.bluestormdna.kocoboy.ui.dmg.PowerRow
import io.github.bluestormdna.kocoboy.ui.dmg.ScreenBezel
import io.github.bluestormdna.kocoboy.ui.dmg.SelectStart
import io.github.bluestormdna.kocoboy.ui.dmg.Speaker
import io.github.bluestormdna.kocoboy.ui.dmg.playItLoudThemeList
import io.github.bluestormdna.kocoboy.ui.theme.KocoBoyTheme
import kocoboy.composeapp.generated.resources.Res
import kocoboy.composeapp.generated.resources.reference
import org.jetbrains.compose.resources.painterResource

@Preview
@Composable
fun ReferenceGameBoy() {
    Box {
        GameBoy()
        Image(
            modifier = Modifier
                .matchParentSize(),
            painter = painterResource(Res.drawable.reference),
            contentDescription = null,
        )
    }
}

@Preview
@Composable
fun OverlayGameBoy() {
    Box {
        GameBoy()
        Image(
            modifier = Modifier
                .matchParentSize()
                .alpha(0.5f),
            painter = painterResource(Res.drawable.reference),
            contentDescription = null,
        )
    }
}

@Preview
@Composable
fun GameBoyPreview() {
    GameBoy()
}

class ThemeProvider : PreviewParameterProvider<ColorTheme> {
    override val values = playItLoudThemeList.asSequence()
}

@Preview
@Composable
fun GameBoyPlayItLoudPreview(@PreviewParameter(ThemeProvider::class) theme: ColorTheme) {
    KocoBoyTheme(theme) {
        GameBoy()
    }
}

@Preview
@Composable
fun PowerRowPreview() {
    PowerRow()
}

@Preview
@Composable
fun ScreenBezelPreview() {
    ScreenBezel()
}

@Preview
@Composable
fun BrandLabellingPreview() {
    BrandLabelling()
}

@Preview
@Composable
fun GamePadPreview() {
    GamePad(modifier = Modifier.aspectRatio(3 / 1f))
}

@Preview
@Composable
fun SelectStartPreview() {
    SelectStart(modifier = Modifier.aspectRatio(2f))
}

@Preview
@Composable
fun SpeakerPreview() {
    Speaker()
}

@Preview
@Composable
fun HeadPhoneJackPreview() {
    HeadPhoneJack()
}
