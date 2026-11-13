package io.github.raghavsatyadev.androidworld.core.domain.input

internal object InputText {
    fun textChunks(line: String): List<String> {
        if (line.isEmpty()) return emptyList()
        val chunks = mutableListOf<String>()
        var start = 0
        for (i in 0 until line.length - 1) {
            if (line[i] == '%' && line[i + 1] == 's') {
                chunks.add(line.substring(start, i + 1))
                start = i + 1
            }
        }
        chunks.add(line.substring(start))
        return chunks
    }
}
