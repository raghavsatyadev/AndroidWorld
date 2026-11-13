package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

/** Coarse-grained display rotation matching Android's ROTATION_* constants. */
enum class ScreenRotation(val degrees: Int) {
    Portrait(0),
    Landscape(90),
    ReversePortrait(180),
    ReverseLandscape(270);

    /** Rotates left (90 degrees counter-clockwise, advancing to the next rotation index). */
    fun rotatedLeft(): ScreenRotation = entries[(ordinal + 1) % 4]

    /** Rotates right (90 degrees clockwise, moving to the previous rotation index). */
    fun rotatedRight(): ScreenRotation = entries[(ordinal + 3) % 4]

    companion object {
        /** Returns the [ScreenRotation] corresponding to index 0..3, defaulting to [Portrait]. */
        fun ofIndex(i: Int): ScreenRotation = entries.getOrElse(i) { Portrait }
    }
}

/** A raw RGBA8888 frame delivered from the emulator's display stream. */
class EmulatorFrame(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
    val seq: Int,
    val rotation: ScreenRotation,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmulatorFrame) return false
        return width == other.width &&
            height == other.height &&
            seq == other.seq &&
            rotation == other.rotation &&
            rgba.contentEquals(other.rgba)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + seq
        result = 31 * result + rotation.hashCode()
        result = 31 * result + rgba.contentHashCode()
        return result
    }
}

/** User inputs directed to the emulator display. */
sealed interface EmulatorInput {
    /** Single pointer touch event in natural unrotated display coordinates. */
    data class Touch(val x: Int, val y: Int, val pressed: Boolean, val id: Int = 0) : EmulatorInput

    /** Mouse at natural device pixels; bit 0 of [buttons] = primary pressed (0 = released / hover). */
    data class Mouse(val x: Int, val y: Int, val buttons: Int) : EmulatorInput

    /** Key press using W3C key names recognized by the emulator. */
    data class Key(val key: String) : EmulatorInput

    /** Direct text typing injected into the emulator. */
    data class Text(val text: String) : EmulatorInput
}

/** Common W3C key names understood by the emulator's key injection endpoint. */
object EmulatorKeys {
    const val HOME = "GoHome"
    const val BACK = "GoBack"
    const val RECENTS = "AppSwitch"
    const val POWER = "Power"
    const val VOLUME_UP = "AudioVolumeUp"
    const val VOLUME_DOWN = "AudioVolumeDown"
    const val ENTER = "Enter"
    const val BACKSPACE = "Backspace"
    const val TAB = "Tab"
    const val ESCAPE = "Escape"
    const val ARROW_LEFT = "ArrowLeft"
    const val ARROW_RIGHT = "ArrowRight"
    const val ARROW_UP = "ArrowUp"
    const val ARROW_DOWN = "ArrowDown"
    const val DELETE = "Delete"
}

/** Foldable device posture states matching the emulator controller proto values. */
enum class FoldPosture(val protoValue: Int, val label: String) {
    Closed(1, "Closed"),
    HalfOpened(2, "Half open"),
    Opened(3, "Open"),
    Flipped(4, "Flipped"),
    Tent(5, "Tent");

    companion object {
        /** Maps a proto integer posture value to [FoldPosture], or null if unmapped. */
        fun ofProto(v: Int): FoldPosture? = entries.firstOrNull { it.protoValue == v }
    }
}

/** Hardware configuration of the emulator display and foldable sensors. */
data class EmulatorHardware(
    val width: Int,
    val height: Int,
    val density: Int?,
    val postures: List<FoldPosture>,
    val skinName: String?,
) {
    /** True if this device supports foldable postures. */
    val foldable: Boolean get() = postures.isNotEmpty()
}

/** Parses [EmulatorHardware] from an emulator hardware configuration map. */
object EmulatorHardwareParser {
    private val SIZE_REGEX = Regex("""^\d+x\d+$""")

    fun parse(config: Map<String, String>): EmulatorHardware {
        val width = config["hw.lcd.width"]?.toIntOrNull() ?: 0
        val height = config["hw.lcd.height"]?.toIntOrNull() ?: 0
        val density = config["hw.lcd.density"]?.toIntOrNull()

        val isFoldable = config["hw.sensor.hinge"]?.equals("yes", ignoreCase = true) == true ||
            config["hw.sensor.roll"]?.equals("yes", ignoreCase = true) == true

        val postures = if (isFoldable) {
            val rawList = config["hw.sensor.posture_list"]
            if (!rawList.isNullOrBlank()) {
                rawList.split(',', ' ')
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it != 0 }
                    .mapNotNull(FoldPosture::ofProto)
            } else {
                listOf(FoldPosture.Closed, FoldPosture.HalfOpened, FoldPosture.Opened)
            }
        } else {
            emptyList()
        }

        val rawSkinName = config["skin.name"]?.trim()
        val skinName = rawSkinName?.takeIf { it.isNotEmpty() && !SIZE_REGEX.matches(it) }

        return EmulatorHardware(
            width = width,
            height = height,
            density = density,
            postures = postures,
            skinName = skinName,
        )
    }
}
