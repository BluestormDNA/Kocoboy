package io.github.bluestormdna.kocoboy.ui.dmg

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.AutoSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bluestormdna.kocoboy.core.JoypadInputs
import io.github.bluestormdna.kocoboy.ui.theme.KocoBoyTheme
import kocoboy.composeapp.generated.resources.Res
import kocoboy.composeapp.generated.resources.lato_regular
import kocoboy.composeapp.generated.resources.leaguespartan_regular
import kocoboy.composeapp.generated.resources.nes_controller
import kocoboy.composeapp.generated.resources.pretendo
import org.jetbrains.compose.resources.Font
import kotlin.jvm.JvmInline

@Composable
fun GameBoy(
    modifier: Modifier = Modifier,
    uiJoyPadEvent: (UiJoyPadEvent) -> Unit = {},
    screen: @Composable BoxScope.() -> Unit = {},
    poweredOn: () -> Boolean = { false },
) {
    GameBoyLayout(modifier) {
        PowerRow()
        ScreenBezel(
            screen = screen,
            poweredOn = poweredOn
        )
        BrandLabelling()
        GamePad(uiJoyPadEvent)
        SelectStart(uiJoyPadEvent)
        Speaker()
        HeadPhoneJack()
    }
}

@Composable
fun PowerRow(
    modifier: Modifier = Modifier.height(IntrinsicSize.Min)
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .border(border = BorderStroke(Dp.Hairline, brush = topRowExtrudedShadows))
            )
            Spacer(modifier.aspectRatio(1 / 8f))
            BasicText(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(border = BorderStroke(Dp.Hairline, brush = topRowExtrudedShadows))
                    .wrapContentSize(BiasAlignment(-0.90f, 0f))
                    .fillMaxHeight(0.6f)
                    .fillMaxWidth(0.225f)
                    .border(Dp.Hairline, dropShadow, CircleShape)
                    .wrapContentSize()
                    .fillMaxWidth(0.95f),
                style = TextStyle(
                    fontFamily = LeagueSpartan(),
                    letterSpacing = TextUnit(1.2f, TextUnitType.Sp),
                    color = KocoBoyTheme.colors.body,
                    lineHeightStyle = LineHeightStyle.Default,
                    shadow = Shadow(
                        color = KocoBoyTheme.colors.accentDark,
                        offset = Offset(1f, 1f),
                        blurRadius = 2f,
                    )
                ),
                autoSize = AutoSize.StepBased(minFontSize = 2.sp, stepSize = 1.sp),
                text = "◀OFF·ON▶",
            )
            Spacer(modifier.aspectRatio(1 / 8f))
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .border(border = BorderStroke(Dp.Hairline, brush = topRowExtrudedShadows))
            )
        }
        Spacer(modifier = Modifier.aspectRatio(148f / 1f))
        HorizontalDivider(color = Color(0xAACCCCCC))
    }
}

@Composable
fun ScreenBezel(
    modifier: Modifier = Modifier.height(IntrinsicSize.Min),
    screen: @Composable BoxScope.() -> Unit = { },
    poweredOn: () -> Boolean = { false },
) {
    val ledColor = remember(poweredOn()) {
        if (poweredOn()) LedOn else LedOff
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .border(BorderStroke(1.dp, KocoBoyTheme.colors.accentDark), FrameShape)
            .clip(FrameShape)
            .background(KocoBoyTheme.colors.bezel)
            .wrapContentSize(align = Alignment.Center)
            .fillMaxSize(0.93f),
    ) {
        Column(
            modifier = modifier
                .matchParentSize()
                .wrapContentSize(Alignment.TopCenter)
                .fillMaxHeight(0.06f)
        ) {
            Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
            Spacer(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .background(KocoBoyTheme.colors.bezelTopLine)
            )
            Spacer(modifier = Modifier.fillMaxWidth().weight(2f))
            Spacer(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .background(KocoBoyTheme.colors.bezelBottomLine)
            )
            Spacer(modifier = Modifier.fillMaxWidth().weight(1f))
        }
        BasicText(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.06f)
                .wrapContentSize(BiasAlignment(0.35f, 0f))
                .fillMaxHeight()
                .fillMaxWidth(0.57f)
                .background(KocoBoyTheme.colors.bezel)
                .wrapContentSize()
                .fillMaxHeight()
                .fillMaxWidth(0.95f)
                .wrapContentSize(),
            text = "DOT MATRIX WITH STEREO SOUND",
            style = TextStyle(
                fontFamily = LeagueSpartan(),
                lineHeightStyle = LineHeightStyle.Default,
                color = KocoBoyTheme.colors.bezelText,
                textAlign = TextAlign.Center,
            ),
            autoSize = AutoSize.StepBased(minFontSize = 2.sp)
        )
        Box(
            modifier = Modifier
                .aspectRatio(10 / 9f)
                .fillMaxSize()
                .wrapContentSize(align = Alignment.Center)
                .fillMaxSize(0.83f)
                .align(Alignment.Center)
                .fillMaxSize()
                .background(KocoBoyTheme.colors.screenBackground)
                .background(horizontalScreenShadow)
                .background(verticalScreenShadow),
            content = screen,
        )
        Column(
            verticalArrangement = Arrangement.aligned(BiasAlignment.Vertical(-0.14f)),
            modifier = Modifier.fillMaxHeight()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.035f)
                    .align(BiasAlignment.Horizontal(-0.4f))
            ) {
                drawCircle(ledColor, size.width / 2)
            }
            Spacer(modifier = Modifier.fillMaxHeight(0.06f))
            BasicText(
                modifier = Modifier
                    .fillMaxWidth(0.12f)
                    .fillMaxHeight(0.06f),
                text = "BATTERY",
                style = TextStyle(
                    fontFamily = LeagueSpartan(),
                    lineHeightStyle = LineHeightStyle.Default,
                    color = KocoBoyTheme.colors.bezelText,
                ),
                autoSize = AutoSize.StepBased(minFontSize = 2.sp)
            )
        }
    }
}

@Composable
fun BrandLabelling(
    modifier: Modifier = Modifier
) {
    Row {
        BasicText(
            modifier = modifier.alignByBaseline()
                .fillMaxWidth(0.26f)
                .fillMaxHeight(0.42f),
            text = "Nintendo",
            style = TextStyle(fontFamily = Pretendo(), color = KocoBoyTheme.colors.bodyText),
            autoSize = AutoSize.StepBased(minFontSize = 2.sp),
        )
        Spacer(modifier.fillMaxWidth(0.02f))
        BasicText(
            modifier = modifier.alignByBaseline()
                .fillMaxWidth(0.53f)
                .fillMaxHeight(0.42f),
            text = "GAME BOY",
            style = TextStyle(
                fontFamily = LatoRegular(),
                fontStyle = FontStyle.Italic,
                color = KocoBoyTheme.colors.bodyText
            ),
            autoSize = AutoSize.StepBased(minFontSize = 2.sp),
        )
        BasicText(
            modifier = modifier.alignByBaseline()
                .fillMaxWidth(0.15f)
                .fillMaxHeight(0.15f),
            text = "TM",
            style = TextStyle(fontFamily = LatoRegular(), color = KocoBoyTheme.colors.bodyText),
            autoSize = AutoSize.StepBased(minFontSize = 2.sp),
        )
    }
}

@Composable
fun GamePad(
    uiJoyPadEvent: (UiJoyPadEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Pad(uiJoyPadEvent)
        MainButtons(uiJoyPadEvent)
    }
}

@Composable
fun MainButtons(
    uiJoyPadEvent: (UiJoyPadEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .rotate(-25f)
            .aspectRatio(1.12f)
    ) {
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .weight(4f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(concaveShadow)
                .wrapContentSize()
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Spacer(
                modifier = modifier
                    .weight(1f)
                    .detectInput(
                        onPress = { uiJoyPadEvent(KeyDown(JoypadInputs.B)) },
                        onRelease = { uiJoyPadEvent(KeyUp(JoypadInputs.B)) },
                    )
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(KocoBoyTheme.colors.mainButtons)
                    .background(mainButtonShadow)
            )
            Spacer(modifier.weight(0.5f))
            Spacer(
                modifier = modifier
                    .weight(1f)
                    .detectInput(
                        onPress = { uiJoyPadEvent(KeyDown(JoypadInputs.A)) },
                        onRelease = { uiJoyPadEvent(KeyUp(JoypadInputs.A)) },
                    )
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(KocoBoyTheme.colors.mainButtons)
                    .background(mainButtonShadow)
            )
        }
        Row(
            modifier = Modifier.weight(2f),
            verticalAlignment = BiasAlignment.Vertical(-0.7f)
        ) {
            BasicText(
                text = "B",
                style = TextStyle(
                    color = KocoBoyTheme.colors.bodyText,
                    fontFamily = NesController(),
                    lineHeightStyle = LineHeightStyle.Default,
                ),
                autoSize = AutoSize.StepBased(minFontSize = 2.sp, stepSize = 0.1.sp),
                modifier = modifier.weight(1f)
                    .wrapContentSize()
                    .fillMaxWidth(0.17f)
                    .fillMaxHeight(0.40f),
            )
            BasicText(
                text = "A",
                style = TextStyle(
                    color = KocoBoyTheme.colors.bodyText,
                    fontFamily = NesController(),
                    lineHeightStyle = LineHeightStyle.Default,
                ),
                autoSize = AutoSize.StepBased(minFontSize = 2.sp, stepSize = 0.1.sp),
                modifier = modifier.weight(1f)
                    .wrapContentSize()
                    .fillMaxWidth(0.17f)
                    .fillMaxHeight(0.40f),
            )
        }
    }
}

@Composable
fun Triangle(
    modifier: Modifier = Modifier,
    orientation: Float = 0f,
    color: Color = KocoBoyTheme.colors.body,
    shadowColor: Color = Color(0xFFA8A29F),
) {
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(1.dp, spotColor = shadowColor)
    ) {
        val rect = Rect(Offset.Zero, size)
        val trianglePath = Path().apply {
            moveTo(rect.topCenter.x, rect.topCenter.y)
            lineTo(rect.bottomRight.x, rect.bottomRight.y)
            lineTo(rect.bottomLeft.x, rect.bottomLeft.y)
            close()
        }
        rotate(orientation) {
            drawPath(trianglePath, color)
        }
    }
}

@Composable
fun Pad(
    uiJoyPadEvent: (UiJoyPadEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(concaveShadow),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.95f)
        ) {
            Triangle(
                orientation = 0f,
                modifier = modifier
                    .fillMaxSize(0.05f)
                    .align(Alignment.TopCenter)
            )
            Triangle(
                orientation = 90f,
                modifier = modifier
                    .fillMaxSize(0.05f)
                    .align(Alignment.CenterEnd)
            )
            Triangle(
                orientation = 180f,
                modifier = modifier
                    .fillMaxSize(0.05f)
                    .align(Alignment.BottomCenter)
            )
            Triangle(
                orientation = 270f,
                modifier = modifier
                    .fillMaxSize(0.05f)
                    .align(Alignment.CenterStart)
            )
        }
        // Actual PAD
        Box(
            modifier.fillMaxSize(0.8f)
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Black)
                    .fillMaxWidth(0.3f)
                    .fillMaxHeight(0.35f)
                    .align(Alignment.TopCenter)
                    .wrapContentWidth()
                    .fillMaxWidth(0.8f)
                    .wrapContentWidth()
                    .detectInput(
                        onPress = { uiJoyPadEvent(KeyDown(JoypadInputs.UP)) },
                        onRelease = { uiJoyPadEvent(KeyUp(JoypadInputs.UP)) },
                    )
            ) {
                PadHorizontalGrip()
            }
            Row(
                modifier = Modifier
                    .background(Color.Black)
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight(0.3f)
                    .align(Alignment.CenterEnd)
                    .wrapContentHeight()
                    .fillMaxHeight(0.8f)
                    .wrapContentHeight()
                    .detectInput(
                        onPress = { uiJoyPadEvent(KeyDown(JoypadInputs.RIGHT)) },
                        onRelease = { uiJoyPadEvent(KeyUp(JoypadInputs.RIGHT)) },
                    )
            ) {
                PadVerticalGrip()
            }
            Column(
                modifier = Modifier
                    .background(Color.Black)
                    .fillMaxWidth(0.3f)
                    .fillMaxHeight(0.35f)
                    .align(Alignment.BottomCenter)
                    .wrapContentWidth()
                    .fillMaxWidth(0.8f)
                    .wrapContentWidth()
                    .detectInput(
                        onPress = { uiJoyPadEvent(KeyDown(JoypadInputs.DOWN)) },
                        onRelease = { uiJoyPadEvent(KeyUp(JoypadInputs.DOWN)) },
                    )
            ) {
                PadHorizontalGrip()
            }
            Row(
                modifier = Modifier
                    .background(Color.Black)
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight(0.3f)
                    .align(Alignment.CenterStart)
                    .wrapContentHeight()
                    .fillMaxHeight(0.8f)
                    .wrapContentHeight()
                    .detectInput(
                        onPress = { uiJoyPadEvent(KeyDown(JoypadInputs.LEFT)) },
                        onRelease = { uiJoyPadEvent(KeyUp(JoypadInputs.LEFT)) },
                    )
            ) {
                PadVerticalGrip()
            }
            Box(
                modifier = Modifier
                    .background(Color.Black)
                    .fillMaxSize(0.3f)
                    .align(Alignment.Center)
            ) {
                Canvas(modifier = modifier.fillMaxSize()) {
                    drawCircle(padCircleCenterConcaveShadow, size.minDimension / 2.5f)
                }
            }
        }
    }
}

@Composable
fun ColumnScope.PadHorizontalGrip() {
    val horizontalPillModifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .clip(CircleShape)
        .background(KocoBoyTheme.colors.accentDark)

    Spacer(modifier = Modifier.weight(0.5f))
    repeat(3) {
        Spacer(modifier = horizontalPillModifier)
        Spacer(modifier = Modifier.weight(0.5f))
    }
}

@Composable
fun RowScope.PadVerticalGrip() {
    val verticalPillModifier = Modifier
        .fillMaxHeight()
        .weight(1f)
        .clip(CircleShape)
        .background(KocoBoyTheme.colors.accentDark)

    Spacer(modifier = Modifier.weight(0.5f))
    repeat(3) {
        Spacer(modifier = verticalPillModifier)
        Spacer(modifier = Modifier.weight(0.5f))
    }
}

@Composable
fun SelectStart(
    uiJoyPadEvent: (UiJoyPadEvent) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RubberButton(
            text = "SELECT",
            modifier = Modifier.detectInput(
                onPress = { uiJoyPadEvent(KeyDown(JoypadInputs.SELECT)) },
                onRelease = { uiJoyPadEvent(KeyUp(JoypadInputs.SELECT)) },
            ),
        )
        RubberButton(
            text = "START",
            modifier = Modifier.detectInput(
                onPress = { uiJoyPadEvent(KeyDown(JoypadInputs.START)) },
                onRelease = { uiJoyPadEvent(KeyUp(JoypadInputs.START)) },
            ),
        )
    }
}

@Composable
fun RubberButton(
    text: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .rotate(-26f)
            .aspectRatio(1.05f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val horizontalPillModifier = Modifier
            .fillMaxWidth()
            .weight(0.5f)
            .clip(CircleShape)
            .background(concaveShadow)
        Spacer(Modifier.weight(0.20f))
        Box(
            modifier = horizontalPillModifier,
            contentAlignment = Alignment.Center,
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.6f)
                    .clip(CircleShape)
                    .background(KocoBoyTheme.colors.internalDark)
                    .wrapContentSize()
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .clip(CircleShape)
                    .background(KocoBoyTheme.colors.rubberButtons)
                    .background(rubberShadow)
            )
        }
        BasicText(
            text = text,
            style = TextStyle(
                color = KocoBoyTheme.colors.bodyText,
                letterSpacing = TextUnit(1.2f, TextUnitType.Sp),
                fontFamily = NesController(),
                lineHeightStyle = LineHeightStyle.Default,
            ),
            autoSize = AutoSize.StepBased(minFontSize = 2.sp),
            modifier = Modifier
                .weight(0.35f)
                .wrapContentSize(align = BiasAlignment(0f, -0.8f))
                .fillMaxWidth(text.count() * 0.17f),
        )
    }
}

@Composable
fun Speaker(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.rotate(-29f),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            modifier = modifier
                .matchParentSize()
                .wrapContentSize(Alignment.BottomCenter)
                .fillMaxHeight(0.62f)
                .fillMaxWidth()
                .background(KocoBoyTheme.colors.bodyBottomRightCornerShadow)
        )
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.65f)
                .fillMaxHeight(0.5f)
        ) {
            SpeakerGridLine(dummy = true)
            SpeakerGridLine()
            SpeakerGridLine()
            SpeakerGridLine()
            SpeakerGridLine()
            SpeakerGridLine()
        }
    }

}

@Composable
fun RowScope.SpeakerGridLine(dummy: Boolean = false) {
    Box(
        Modifier.weight(1f)
            .wrapContentSize()
            .fillMaxHeight()
            .fillMaxWidth(0.3f)
            .clip(CircleShape)
            .background(horizontalSpeakerGridShadow)
            .background(verticalSpeakerGridShadow)
    ) {
        if (dummy) return
        Spacer(
            Modifier
                .wrapContentSize()
                .fillMaxHeight(0.7f)
                .fillMaxWidth(0.2f)
                .background(KocoBoyTheme.colors.internalDark)
        )
    }
}

@Composable
fun HeadPhoneJack(
    modifier: Modifier = Modifier.height(IntrinsicSize.Min)
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(Dp.Hairline, dropShadow, CircleShape)
                .wrapContentSize(),
            style = TextStyle(
                fontFamily = LeagueSpartan(),
                letterSpacing = TextUnit(1f, TextUnitType.Sp),
                color = KocoBoyTheme.colors.body,
                lineHeightStyle = LineHeightStyle.Default,
                shadow = Shadow(
                    color = KocoBoyTheme.colors.accentDark,
                    offset = Offset(1f, 1f),
                    blurRadius = 2f,
                )
            ),
            autoSize = AutoSize.StepBased(minFontSize = 2.sp),
            text = "⠀PHONES⠀",
        )
    }
}

infix fun Int.percentOf(value: Int): Int {
    return if (this == 0) 0
    else (value * this.toFloat() / 100).toInt()
}

infix fun Float.percentOf(value: Int): Int {
    return if (this == 0f) 0
    else (value * this / 100).toInt()
}

@Composable
fun GameBoyLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier
            .aspectRatio(90 / 148f)
            .clip(UnitShape)
            .background(KocoBoyTheme.colors.body)
            .background(verticalBodyShadow)
            .background(horizontalBodyShadow),
        content = content,
    ) { measurables, constraints ->

        val powerRowHeight = 5 percentOf constraints.maxHeight
        val spacingScreenTop = 3 percentOf constraints.maxHeight
        val spacingDefault = 4.5f percentOf constraints.maxHeight
        val spacingPadLeft = 3.4f percentOf constraints.maxHeight
        val spacingMainButtonsRight = 1.8f percentOf constraints.maxHeight
        val screenHeight = 39 percentOf constraints.maxHeight
        val labellingHeight = 11 percentOf constraints.maxHeight
        val padHeight = 18.5f percentOf constraints.maxHeight
        val selectStartHeight = 9 percentOf constraints.maxHeight
        val spacingScreenBottom = 1 percentOf constraints.maxHeight
        val spacingSelectStartTop = 1.8f percentOf constraints.maxHeight
        val spacingSelectStartLeft = 18 percentOf constraints.maxHeight
        val spacingSelectStartRight = 23 percentOf constraints.maxHeight

        val spacingSpeakerTop = 5f percentOf constraints.maxHeight
        val spacingSpeakerLeft = 57.5f percentOf constraints.maxWidth
        val speakerHeight = 20 percentOf constraints.maxHeight
        val speakerWidth = 52 percentOf constraints.maxWidth

        val headPhonesTopPadding = 12f percentOf constraints.maxHeight
        val headPhoneJackHeight = 2.5f percentOf constraints.maxHeight
        val headPhoneJackWidth = 16 percentOf constraints.maxWidth
        val headPhoneJackLeft = 37f percentOf constraints.maxWidth

        val powerRow = measurables[0].measure(
            constraints.copy(
                minHeight = powerRowHeight,
                maxHeight = powerRowHeight,
            )
        )
        val screen = measurables[1].measure(
            constraints.copy(
                minHeight = screenHeight,
                maxHeight = screenHeight,
                minWidth = constraints.minWidth - spacingDefault * 2,
                maxWidth = constraints.maxWidth - spacingDefault * 2
            )
        )

        val labelling = measurables[2].measure(
            constraints.copy(
                minHeight = labellingHeight,
                maxHeight = labellingHeight,
                minWidth = constraints.minWidth - spacingDefault * 2,
                maxWidth = constraints.maxWidth - spacingDefault * 2,
            )
        )

        val gamePad = measurables[3].measure(
            constraints.copy(
                minHeight = padHeight,
                maxHeight = padHeight,
                minWidth = constraints.minWidth - (spacingPadLeft + spacingMainButtonsRight),
                maxWidth = constraints.maxWidth - (spacingPadLeft + spacingMainButtonsRight),
            )
        )

        val selectStart = measurables[4].measure(
            constraints.copy(
                minHeight = selectStartHeight,
                maxHeight = selectStartHeight,
                minWidth = constraints.minWidth - (spacingSelectStartLeft + spacingSelectStartRight),
                maxWidth = constraints.maxWidth - (spacingSelectStartLeft + spacingSelectStartRight),
            )
        )

        val speaker = measurables[5].measure(
            constraints.copy(
                minHeight = speakerHeight,
                maxHeight = speakerHeight,
                minWidth = speakerWidth,
                maxWidth = speakerWidth,
            )
        )


        val headphoneJack = measurables[6].measure(
            constraints.copy(
                minHeight = headPhoneJackHeight,
                maxHeight = headPhoneJackHeight,
                minWidth = headPhoneJackWidth,
                maxWidth = headPhoneJackWidth,
            )
        )

        layout(
            width = constraints.maxWidth,
            height = constraints.maxHeight,
        ) {
            var y = 0
            powerRow.place(0, y)
            y += powerRow.height + spacingScreenTop
            screen.place(spacingDefault, y)
            y += screen.height + spacingScreenBottom
            labelling.place(spacingDefault, y)
            y += labelling.height
            gamePad.place(spacingPadLeft, y)
            y += gamePad.height + spacingSelectStartTop
            selectStart.place(spacingSelectStartLeft, y)
            y += spacingSpeakerTop
            speaker.place(spacingSpeakerLeft, y)
            y += headPhonesTopPadding
            headphoneJack.place(headPhoneJackLeft, y)
        }
    }
}

@Composable
fun LatoRegular() = FontFamily(Font(Res.font.lato_regular))

@Composable
fun Pretendo() = FontFamily(Font(Res.font.pretendo))

@Composable
fun LeagueSpartan() = FontFamily(Font(Res.font.leaguespartan_regular))

@Composable
fun NesController() = FontFamily(Font(Res.font.nes_controller))

val dropShadow = Brush.sweepGradient(
    listOf(
        Color(0xFFA8A29F),
        Color.LightGray,
        Color.DarkGray,
        Color(0xFFA8A29F),
    )
)

val concaveShadow = Brush.verticalGradient(
    0f to Color(0x809B9899),
    0.7f to Color(0x80EAE8E4),
    1f to Color(0x80FFFDF8),
)

val rubberShadow = Brush.verticalGradient(
    0f to Color(0x80C0BCD7),
    100f to Color(0x80727075)
)

val mainButtonShadow = Brush.verticalGradient(
    0f to Color(0x20FFFFFF),
    100f to Color(0x40000000)
)

val horizontalSpeakerGridShadow = Brush.horizontalGradient(
    0f to Color(0x80727075),
    0.3f to Color(0x80C0BCD7),
    1f to Color(0x80727075)
)

val verticalSpeakerGridShadow = Brush.verticalGradient(
    0f to Color(0x80727075),
    0.1f to Color(0x20C0BCD7),
    0.9f to Color(0x20C0BCD7),
    1f to Color(0x80727075)
)

val verticalBodyShadow = Brush.verticalGradient(
    0f to Color(0x90F0F0F0),
    0.01f to Color.Transparent,
    0.98f to Color.Transparent,
    1f to Color(0x90727075)
)

val horizontalBodyShadow = Brush.horizontalGradient(
    0f to Color(0x90727075),
    0.02f to Color.Transparent,
    0.98f to Color.Transparent,
    1f to Color(0x90727075)
)

val topRowExtrudedShadows = Brush.verticalGradient(
    0f to Color(0x90F0F0F0),
    0.7f to Color(0x90727075),
    1f to Color(0x90727075),
)

val horizontalScreenShadow = Brush.horizontalGradient(
    0f to Color(0x90000000),
    0.03f to Color.Transparent,
    0.98f to Color.Transparent,
    1f to Color(0x90727075)
)

val verticalScreenShadow = Brush.verticalGradient(
    0f to Color(0x90000000),
    0.03f to Color.Transparent,
    0.98f to Color.Transparent,
    1f to Color(0x90727075)
)

val padCircleCenterConcaveShadow = Brush.sweepGradient(
    listOf(
        Color.Black,
        Color.Black,
        Color.White,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.White,
        Color.Black,
        Color.Black,
    )
)

sealed interface UiJoyPadEvent

@JvmInline
value class KeyDown(val key: JoypadInputs) : UiJoyPadEvent

@JvmInline
value class KeyUp(val key: JoypadInputs) : UiJoyPadEvent

fun Modifier.detectInput(
    onPress: () -> Unit,
    onRelease: () -> Unit
) = this.pointerInput(Unit) {
    detectTapGestures(
        onPress = {
            try {
                onPress()
                awaitRelease()
            } finally {
                onRelease()
            }
        },
    )
}

