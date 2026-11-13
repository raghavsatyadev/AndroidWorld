package io.github.raghavsatyadev.androidworld.core.domain.sdk

/** One row of `android sdk list`. [path] uses slashes, e.g. `system-images/android-34/google_apis/x86_64`. */
data class SdkPackage(
    val path: String,
    val version: String, // "7.0.0", "2.0.0 (+1)" kept whole, "30.0.15729638-rc.2", "unknown"
    val description: String,
    val channel: String? = null, // "beta" / "canary" / "dev" when the row shows one
    val update: String? = null, // the version after "->" when the row shows one
)

/** Installed and available packages, as the two sections of the listing. */
data class SdkListing(val installed: List<SdkPackage>, val available: List<SdkPackage>)

/**
 * An emulator system image. [apiLevel] is the text after `android-` without any extension suffix
 * ("34", "37.2", "Baklava", "CANARY"); [extension] is N from `-extN`, else null.
 */
data class SystemImage(
    val path: String,
    val apiLevel: String,
    val extension: Int?,
    val tag: String, // "google_apis", "google_apis_playstore", "default", "android-tv", ...
    val abi: String, // "x86_64", "arm64-v8a", ...
    val version: String,
    val description: String,
    val installed: Boolean,
) {
    /** The id avdmanager and sdkmanager take: path with ';' instead of '/'. */
    val packageId: String get() = path.replace('/', ';')

    /** "API 34", "API 34 ext 10", "API Baklava". */
    val apiLabel: String
        get() = if (extension != null) "API $apiLevel ext $extension" else "API $apiLevel"

    /**
     * Human tag: google_apis -> "Google APIs", google_apis_playstore -> "Google Play", default -> "AOSP",
     * google_atd -> "Google APIs ATD", aosp_atd -> "AOSP ATD", android-tv -> "Android TV", google-tv -> "Google TV",
     * android-wear -> "Wear OS", android-automotive -> "Automotive", android-automotive-playstore -> "Automotive with Google Play",
     * android-desktop -> "Desktop"; a "_ps16k" suffix adds " (16 KB pages)"; anything else: replace '_' and '-' with spaces
     * and capitalise each word.
     */
    val tagLabel: String
        get() {
            val hasPs16k = tag.endsWith("_ps16k")
            val baseTag = if (hasPs16k) tag.removeSuffix("_ps16k") else tag
            val baseLabel = when (baseTag) {
                "google_apis" -> "Google APIs"
                "google_apis_playstore" -> "Google Play"
                "default" -> "AOSP"
                "google_atd" -> "Google APIs ATD"
                "aosp_atd" -> "AOSP ATD"
                "android-tv" -> "Android TV"
                "google-tv" -> "Google TV"
                "android-wear" -> "Wear OS"
                "android-automotive" -> "Automotive"
                "android-automotive-playstore" -> "Automotive with Google Play"
                "android-desktop" -> "Desktop"
                else -> baseTag
                    .split('_', '-')
                    .filter { it.isNotEmpty() }
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
            }
            return if (hasPs16k) "$baseLabel (16 KB pages)".trim() else baseLabel
        }
}

/** Pure-Kotlin utilities for parsing, merging, and ordering emulator [SystemImage] entries. */
object SystemImages {
    private val pathRegex =
        Regex("""^system-images/android-([A-Za-z0-9.]+)(?:-ext(\d+))?/([^/]+)/([^/]+)$""")

    /** Parses an [SdkPackage] into a [SystemImage], or null if the path is not a system image. */
    fun from(pkg: SdkPackage, installed: Boolean): SystemImage? {
        val match = pathRegex.matchEntire(pkg.path) ?: return null
        val apiLevel = match.groupValues[1]
        val extension = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
        val tag = match.groupValues[3]
        val abi = match.groupValues[4]
        return SystemImage(
            path = pkg.path,
            apiLevel = apiLevel,
            extension = extension,
            tag = tag,
            abi = abi,
            version = pkg.version,
            description = pkg.description,
            installed = installed,
        )
    }

    /**
     * Orders API levels ascending: numeric levels compare by parts ("37.2" > "37" > "36.1" > "9"),
     * non-numeric codenames sort above every number, and codenames compare alphabetically.
     */
    val apiOrder: Comparator<String> = Comparator { a, b ->
        val aNumeric = isNumeric(a)
        val bNumeric = isNumeric(b)
        when {
            !aNumeric && !bNumeric -> a.compareTo(b, ignoreCase = true).let { if (it != 0) it else a.compareTo(b) }
            !aNumeric && bNumeric -> 1
            aNumeric && !bNumeric -> -1
            else -> compareNumericParts(a, b)
        }
    }

    /**
     * Merges installed and available system images, deduping by path with installed entries winning.
     * Sorted by API newest first, extension (null first), tag, then ABI.
     */
    fun merge(listing: SdkListing): List<SystemImage> {
        val byPath = LinkedHashMap<String, SystemImage>()
        for (pkg in listing.available) {
            from(pkg, installed = false)?.let { byPath[it.path] = it }
        }
        for (pkg in listing.installed) {
            from(pkg, installed = true)?.let { byPath[it.path] = it }
        }
        return byPath.values.sortedWith(
            compareByDescending<SystemImage, String>(apiOrder) { it.apiLevel }
                .thenBy(nullsFirst<Int>()) { it.extension }
                .thenBy { it.tag }
                .thenBy { it.abi }
        )
    }

    /** Supported emulator ABIs for host architecture [osArch]. Returns empty set when architecture is unknown. */
    fun hostAbis(osArch: String): Set<String> =
        when (osArch.lowercase()) {
            "amd64", "x86_64" -> setOf("x86_64", "x86")
            "aarch64", "arm64" -> setOf("arm64-v8a")
            else -> emptySet()
        }

    private fun isNumeric(api: String): Boolean {
        val parts = api.split('.')
        return parts.isNotEmpty() && parts.all { part -> part.isNotEmpty() && part.all { it.isDigit() } }
    }

    private fun compareNumericParts(a: String, b: String): Int {
        val aParts = a.split('.').mapNotNull { it.toIntOrNull() }
        val bParts = b.split('.').mapNotNull { it.toIntOrNull() }
        val maxParts = maxOf(aParts.size, bParts.size)
        for (i in 0 until maxParts) {
            val aPart = aParts.getOrElse(i) { 0 }
            val bPart = bParts.getOrElse(i) { 0 }
            val diff = aPart.compareTo(bPart)
            if (diff != 0) return diff
        }
        return aParts.size.compareTo(bParts.size)
    }
}
