package com.pyamsoft.tetherfi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import com.pyamsoft.pydroid.theme.keylines
import com.pyamsoft.pydroid.ui.theme.HairlineSize
import com.pyamsoft.pydroid.ui.theme.ZeroElevation
import com.pyamsoft.pydroid.ui.uri.LocalExternalUriHandler

private enum class LinkContentTypes {
  FAQ_LINK
}

private const val FAQ_TEXT = "FAQs"
private const val KNW_TEXT = "Known to Not Work"

fun LazyListScope.renderLinks(
    modifier: Modifier = Modifier,
    appName: String,
) {
  item(
      contentType = LinkContentTypes.FAQ_LINK,
  ) {
    val uriHandler = LocalExternalUriHandler.current

    val textColor = MaterialTheme.colors.onSurface
    val linkColor = MaterialTheme.colors.primary
    val faqBlurb =
        remember(
            textColor,
            linkColor,
            appName,
        ) {
          buildAnnotatedString {
            withStyle(
                style =
                    SpanStyle(
                        color = textColor,
                    ),
            ) {
              appendLine(
                  "Have questions about how $appName works? Noticing some issues connecting?")
              appendLine()
              append("View our ")
              appendLink(
                  tag = "FAQ",
                  linkColor = linkColor,
                  text = FAQ_TEXT,
                  url = "https://github.com/pyamsoft/tetherfi/wiki",
              )
              append(" and apps that are ")
              appendLink(
                  tag = "KNW",
                  linkColor = linkColor,
                  text = KNW_TEXT,
                  url = "https://github.com/pyamsoft/tetherfi/wiki/Known-Not-Working",
              )
            }
          }
        }

    Surface(
        modifier =
            modifier
                .padding(top = MaterialTheme.keylines.content)
                .padding(bottom = MaterialTheme.keylines.baseline),
        border =
            BorderStroke(
                width = HairlineSize,
                color = MaterialTheme.colors.primary,
            ),
        elevation = ZeroElevation,
        color = Color.Transparent,
        shape = MaterialTheme.shapes.medium,
    ) {
      ClickableText(
          modifier = Modifier.fillMaxWidth().padding(MaterialTheme.keylines.content),
          text = faqBlurb,
          style = MaterialTheme.typography.body1,
          onClick = { offset ->
            faqBlurb
                .getStringAnnotations(
                    tag = "FAQ",
                    start = offset,
                    end = offset + FAQ_TEXT.length,
                )
                .firstOrNull()
                ?.also { uriHandler.openUri(it.item) }

            faqBlurb
                .getStringAnnotations(
                    tag = "KNW",
                    start = offset,
                    end = offset + KNW_TEXT.length,
                )
                .firstOrNull()
                ?.also { uriHandler.openUri(it.item) }
          },
      )
    }
  }
}

@Preview
@Composable
private fun PreviewLinks() {
  LazyColumn {
    renderLinks(
        appName = "TEST",
    )
  }
}
