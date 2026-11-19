package com.goldcalculateur.app.core

enum class CalcMode { CUT_REFINE, ADD_INGOT }

data class IngotRow(val id: Long, val weight: String = "", val karat: String = "") {
    val isBlank: Boolean get() = weight.isBlank() && karat.isBlank()
}

/** Everything the user typed. Persisted automatically and reused by the history. */
data class WorkState(
    val rows: List<IngotRow>,
    val target: String,
    val mode: CalcMode,
    val addedKarat: String,
) {
    companion object {
        val DEFAULT = WorkState(
            rows = listOf(IngotRow(0)),
            target = "",
            mode = CalcMode.ADD_INGOT,
            addedKarat = "1000",
        )
    }
}

/**
 * Compact text format: `v1|MODE|target|addedKarat|w:k;w:k`.
 * Field values are sanitized numerics, so they can never contain the separators.
 */
object WorkStateCodec {
    private const val VERSION = "v1"

    fun encode(s: WorkState): String {
        val rows = s.rows.joinToString(";") { "${InputParser.sanitize(it.weight)}:${InputParser.sanitize(it.karat)}" }
        return listOf(VERSION, s.mode.name, InputParser.sanitize(s.target), InputParser.sanitize(s.addedKarat), rows)
            .joinToString("|")
    }

    fun decode(text: String?): WorkState? {
        if (text.isNullOrBlank()) return null
        val parts = text.split("|", limit = 5)
        if (parts.size < 5 || parts[0] != VERSION) return null
        val mode = CalcMode.entries.firstOrNull { it.name == parts[1] } ?: return null
        val rows = parts[4].split(";").filter { it.isNotEmpty() }.mapIndexed { i, chunk ->
            val kv = chunk.split(":", limit = 2)
            IngotRow(
                id = i.toLong(),
                weight = InputParser.sanitize(kv.getOrElse(0) { "" }),
                karat = InputParser.sanitize(kv.getOrElse(1) { "" }),
            )
        }
        return WorkState(
            rows = rows.ifEmpty { listOf(IngotRow(0)) },
            target = InputParser.sanitize(parts[2]),
            mode = mode,
            addedKarat = InputParser.sanitize(parts[3]).ifBlank { "1000" },
        )
    }
}
