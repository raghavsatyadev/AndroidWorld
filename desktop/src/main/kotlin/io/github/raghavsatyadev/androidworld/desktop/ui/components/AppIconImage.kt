package io.github.raghavsatyadev.androidworld.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.domain.packages.AppIcon
import org.jetbrains.skia.Image as SkiaImage

/** An app's launcher icon, decoded once per icon, with the Android robot as a placeholder. */
@Composable
fun AppIconImage(icon: AppIcon?, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(icon) {
        icon?.let { runCatching { SkiaImage.makeFromEncoded(it.png).toComposeImageBitmap() }.getOrNull() }
    }
    Box(modifier.size(size).clip(MaterialTheme.shapes.large), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, null, Modifier.size(size))
        else Icon(Icons.Filled.Android, null, Modifier.size(size * 0.7f), tint = MaterialTheme.colorScheme.primary)
    }
}
