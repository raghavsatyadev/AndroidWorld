package io.github.raghavsatyadev.androidworld.core.domain.capture

/** Puts a PNG on the system clipboard (an `:intellij` port, task 061); false when the clipboard refused it. */
fun interface ImageClipboard {
    fun copyPng(png: ByteArray): Boolean
}
