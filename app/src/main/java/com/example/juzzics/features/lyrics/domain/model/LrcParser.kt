package com.example.juzzics.features.lyrics.domain.model

/** One line of synced lyrics, shown from [timeMs] on. */
data class LyricLine(val timeMs: Long, val text: String)

private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")

/**
 * Parses LRC text: `[mm:ss.xx] text` (a line can have several time tags).
 * Lines without a time tag (e.g. `[ar: Artist]` metadata) are skipped. Sorted by time.
 */
fun parseLrc(lrc: String): List<LyricLine> =
    lrc.lineSequence().flatMap { line ->
        val tags = timeTag.findAll(line).toList()
        val text = line.replace(timeTag, "").trim()
        tags.map { tag ->
            val minutes = tag.groupValues[1].toLong()
            val seconds = tag.groupValues[2].replace(':', '.').toDouble()
            LyricLine(timeMs = minutes * 60_000 + (seconds * 1000).toLong(), text = text)
        }
    }.sortedBy { it.timeMs }.toList()

/** index of the line playing at [positionMs], -1 before the first line */
fun List<LyricLine>.indexAt(positionMs: Long): Int = indexOfLast { it.timeMs <= positionMs }

/** plain text of LRC lyrics (timestamps removed) */
fun lrcToPlainText(lrc: String): String = parseLrc(lrc).joinToString("\n") { it.text }
