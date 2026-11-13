package io.github.raghavsatyadev.androidworld.core.domain.files

/** One breadcrumb: the label shown and the absolute folder it opens. */
data class Crumb(val label: String, val path: String)

/** Absolute POSIX paths on the device. Always normalised: no trailing slash except the root. */
object RemotePath {
    const val ROOT = "/"

    fun normalize(path: String): String {
        val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
        val stack = ArrayDeque<String>()
        for (part in parts) if (part == "..") stack.removeLastOrNull() else stack.addLast(part)
        return "/" + stack.joinToString("/")
    }

    fun child(parent: String, name: String): String {
        require(isValidName(name)) { "not a file name: '$name'" }
        return if (parent == ROOT) "/$name" else "${normalize(parent)}/$name"
    }

    fun parent(path: String): String {
        val normalized = normalize(path)
        if (normalized == ROOT) return ROOT
        return normalized.substringBeforeLast('/').ifEmpty { ROOT }
    }

    fun name(path: String): String = normalize(path).substringAfterLast('/')

    /** `/` first, then one crumb per folder down to [path]. */
    fun crumbs(path: String): List<Crumb> {
        val parts = normalize(path).split('/').filter { it.isNotEmpty() }
        return listOf(Crumb(ROOT, ROOT)) + parts.indices.map { i -> Crumb(parts[i], "/" + parts.take(i + 1).joinToString("/")) }
    }

    /** A single path segment a user may type for a new folder or a rename. */
    fun isValidName(name: String): Boolean = name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\u0000' !in name

    /** Folders that must never be deleted or renamed from the browser. */
    fun isProtected(path: String): Boolean = normalize(path) in PROTECTED

    private val PROTECTED = setOf("/", "/data", "/system", "/vendor", "/product", "/sdcard", "/storage", "/storage/emulated", "/storage/emulated/0")
}
