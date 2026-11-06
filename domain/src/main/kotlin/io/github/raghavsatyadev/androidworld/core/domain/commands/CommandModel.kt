package io.github.raghavsatyadev.androidworld.core.domain.commands

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How much a command can change the device; [Destructive] ones are confirmed first, naming the device. */
enum class CommandRisk { Read, Reversible, Destructive }

enum class CommandCategory(val label: String) {
    Display("Display"),
    System("System"),
    Input("Input"),
    Battery("Battery"),
    Connectivity("Connectivity"),
    Sound("Sound"),
    Apps("Apps"),
    Diagnostics("Diagnostics"),
}

/** One labelled choice of a [FieldKind.Choice]; [value] is what goes to the device. */
data class Choice(val value: String, val label: String)

/** What a field accepts. Each kind checks a value and says what is wrong with it, or `null`. */
sealed interface FieldKind {
    fun problem(value: String): String?

    data class Text(val pattern: Regex? = null, val patternHint: String = "") : FieldKind {
        override fun problem(value: String): String? =
            if (pattern != null && !pattern.matches(value)) patternHint.ifEmpty { "Not a valid value" } else null
    }

    data class Integer(val min: Long, val max: Long) : FieldKind {
        override fun problem(value: String): String? {
            val number = value.toLongOrNull() ?: return "Enter a whole number"
            return if (number in min..max) null else "Between $min and $max"
        }
    }

    data class Choices(val options: List<Choice>) : FieldKind {
        override fun problem(value: String): String? =
            if (options.any { it.value == value }) null else "Pick one of the options"
    }
}

/**
 * One input of a command. A blank [optional] field is left out of the command line entirely, so
 * `pm list packages` runs with no filter instead of with an empty argument.
 */
data class CommandField(
    val label: String,
    val kind: FieldKind,
    val hint: String = "",
    val optional: Boolean = false,
    val default: String = "",
) {
    fun problem(value: String): String? = when {
        value.isBlank() -> if (optional) null else "Required"
        else -> kind.problem(value.trim())
    }
}

/**
 * A command line made of fixed words and field slots. Each slot becomes exactly one quoted shell
 * value (with any fixed [Part.Slot.prefix]/[Part.Slot.suffix] inside the quotes), so field input can
 * never add words to the command: `input tap` takes X and Y as two fields, never one "X Y" string.
 */
data class CommandTemplate(val parts: List<Part>) {
    sealed interface Part {
        data class Word(val text: String) : Part
        data class Slot(val field: Int, val prefix: String = "", val suffix: String = "") : Part
    }

    val fieldsUsed: Set<Int> get() = parts.filterIsInstance<Part.Slot>().mapTo(mutableSetOf()) { it.field }

    /** The command to run; [values] must already be valid for the fields this template uses. */
    fun build(values: List<String>): AdbShellCommand = AdbShellCommand(
        parts.mapNotNull { part ->
            when (part) {
                is Part.Word -> ShellToken.Literal(part.text)
                is Part.Slot -> values.getOrNull(part.field)?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { ShellToken.Value(ShellValue.of(part.prefix + it + part.suffix)) }
            }
        },
    )

    /** The command line as it will run, with `<label>` where a required field is still empty. */
    fun preview(fields: List<CommandField>, values: List<String>): String = parts.mapNotNull { part ->
        when (part) {
            is Part.Word -> part.text
            is Part.Slot -> {
                val value = values.getOrNull(part.field)?.trim().orEmpty()
                when {
                    value.isNotEmpty() -> AdbShellCommand.of(ShellToken.Value(ShellValue.of(part.prefix + value + part.suffix))).render()
                    fields.getOrNull(part.field)?.optional == true -> null
                    else -> "<${fields.getOrNull(part.field)?.label?.lowercase() ?: "value"}>"
                }
            }
        }
    }.joinToString(" ")

    companion object {
        private val slot = Regex("""^(.*)\{(\d+)\}(.*)$""")

        /** Parses developer-written text: words split on spaces, `{n}` marks field n (`tel:{0}` keeps its prefix). */
        fun parse(text: String): CommandTemplate = CommandTemplate(
            text.trim().split(Regex("\\s+")).map { word ->
                slot.matchEntire(word)?.destructured?.let { (prefix, index, suffix) -> Part.Slot(index.toInt(), prefix, suffix) }
                    ?: Part.Word(word)
            },
        )
    }
}

/**
 * One entry of the command browser. [current] reads the value the command changes (shown next to it,
 * optionally narrowed to the first output line containing [currentLine]); [action] is what Run sends.
 * Both may use [fields]. [warning] is shown with the entry and repeated in the confirmation.
 */
data class CommandEntry(
    val id: String,
    val category: CommandCategory,
    val name: String,
    val description: String,
    val action: CommandTemplate,
    val risk: CommandRisk,
    val fields: List<CommandField> = emptyList(),
    val current: CommandTemplate? = null,
    val currentLine: String? = null,
    val warning: String? = null,
    val timeout: Duration = 15.seconds,
) {
    /** Problems with [values] for the fields [template] uses, by field index; empty when it can run. */
    fun problems(template: CommandTemplate, values: List<String>): Map<Int, String> =
        template.fieldsUsed.mapNotNull { index ->
            fields.getOrNull(index)?.problem(values.getOrElse(index) { "" })?.let { index to it }
        }.toMap()

    /** Every field's starting value. */
    fun defaults(): List<String> = fields.map { it.default }

    /** Whether [query] matches this entry's name, description, category or command words. */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val words = action.parts.filterIsInstance<CommandTemplate.Part.Word>().joinToString(" ") { it.text }
        return listOf(name, description, category.label, words).any { it.contains(query.trim(), ignoreCase = true) }
    }
}

/** Picks the line of [output] that [entry] cares about, or the whole trimmed output. */
fun CommandEntry.currentValue(output: String): String {
    val key = currentLine ?: return output.trim()
    return output.lineSequence().firstOrNull { it.contains(key, ignoreCase = true) }?.trim() ?: output.trim()
}
