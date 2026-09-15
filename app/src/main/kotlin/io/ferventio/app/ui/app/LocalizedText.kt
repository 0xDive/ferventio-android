package io.ferventio.app.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import io.ferventio.app.domain.AppLanguage

private val LocalAppResourceStrings = staticCompositionLocalOf<AppResourceStrings?> { null }

@Composable
internal fun ProvideAppResourceStrings(
    appLanguage: AppLanguage,
    content: @Composable () -> Unit,
) {
    val strings = rememberAppResourceStrings(appLanguage)
    CompositionLocalProvider(LocalAppResourceStrings provides strings) { content() }
}

@Composable
internal fun localizedString(source: String): String {
    val configured = LocalAppResourceStrings.current
    return configured?.legacy(source) ?: LegacyUiStringCatalog.resolve(LocalContext.current, source)
}

@Composable
internal fun localizedJoinToString(
    values: Iterable<String>,
    separator: String = " · ",
): String {
    val configured = LocalAppResourceStrings.current
    val context = LocalContext.current
    val resolver: (String) -> String = if (configured != null) {
        configured::legacy
    } else {
        { source -> LegacyUiStringCatalog.resolve(context, source) }
    }
    return values.joinToString(separator = separator, transform = resolver)
}

internal fun verbatimArgument(value: Any?): String = LegacyUiStringCatalog.verbatim(value?.toString().orEmpty())

@Composable
internal fun LocalizedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = localizedString(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}
