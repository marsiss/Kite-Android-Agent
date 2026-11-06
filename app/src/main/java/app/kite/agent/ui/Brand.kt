package app.kite.agent.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.kite.agent.R
import app.kite.agent.ui.theme.Scanner

/**
 * Original KITT-inspired car-front mark, generated with the local Qwen-Image
 * model and kept as a single consistent asset across icon and in-app avatars.
 */
@Composable
fun ScannerMark(
    modifier: Modifier = Modifier,
    accent: Color = Scanner,
) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
