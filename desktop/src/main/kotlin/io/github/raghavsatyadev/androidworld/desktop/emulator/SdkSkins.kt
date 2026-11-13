package io.github.raghavsatyadev.androidworld.desktop.emulator

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.SkinLayout
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.SkinLayoutParser
import io.github.raghavsatyadev.androidworld.desktop.tools.AndroidSdk
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.skia.Image

/**
 * Loads device skin layouts and background artwork from the Android SDK `skins` directory.
 */
class SdkSkins(private val sdk: AndroidSdk = AndroidSdk()) {
    private var cachedLayouts: Map<String, SkinLayout>? = null
    private val backgroundCache = ConcurrentHashMap<String, ImageBitmap>()

    @Synchronized
    fun layouts(): Map<String, SkinLayout> {
        cachedLayouts?.let { return it }
        val root = sdk.root() ?: return emptyMap()
        val skinsDir = root.resolve("skins")
        if (!Files.isDirectory(skinsDir)) return emptyMap()

        val dirs = runCatching {
            Files.list(skinsDir).use { stream ->
                stream.filter { Files.isDirectory(it) }.toList()
            }
        }.getOrNull() ?: return emptyMap()

        val map = mutableMapOf<String, SkinLayout>()
        for (dir in dirs) {
            val layoutFile = dir.resolve("layout")
            if (Files.isRegularFile(layoutFile)) {
                val text = runCatching { Files.readString(layoutFile) }.getOrNull() ?: continue
                val layout = runCatching { SkinLayoutParser.parse(text) }.getOrNull() ?: continue
                map[dir.fileName.toString()] = layout
            }
        }
        cachedLayouts = map
        return map
    }

    fun background(name: String): ImageBitmap? {
        backgroundCache[name]?.let { return it }
        val layout = layouts()[name] ?: return null
        val bgName = layout.background ?: return null
        val root = sdk.root() ?: return null
        val bgPath = root.resolve("skins").resolve(name).resolve(bgName)
        if (!Files.isRegularFile(bgPath)) return null

        return runCatching {
            val bytes = Files.readAllBytes(bgPath)
            val skiaImage = Image.makeFromEncoded(bytes) ?: return null
            val bitmap = skiaImage.toComposeImageBitmap()
            backgroundCache[name] = bitmap
            bitmap
        }.getOrNull()
    }
}
