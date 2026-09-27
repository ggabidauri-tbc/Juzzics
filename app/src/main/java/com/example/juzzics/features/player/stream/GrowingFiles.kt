package com.example.juzzics.features.player.stream

import java.util.concurrent.ConcurrentHashMap

/**
 * Files still being written while they're played (songs streaming in from a friend's phone).
 * The writer reports progress; the player's [GrowingFileDataSource] waits for bytes that
 * haven't arrived yet instead of treating the current end as the end of the song.
 */
object GrowingFiles {

    private class Entry(@Volatile var totalBytes: Long) {
        @Volatile var written = 0L
        @Volatile var finished = false
        @Volatile var failed = false
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    /** [totalBytes]: the file's final size if known (lets the player jump within the song), else -1 */
    fun start(path: String, totalBytes: Long) {
        entries[path] = Entry(totalBytes)
    }

    /** the final size became known after it started */
    fun setTotalBytes(path: String, totalBytes: Long) {
        if (totalBytes > 0) entries[path]?.totalBytes = totalBytes
    }

    fun progress(path: String, bytesWritten: Long) {
        entries[path]?.written = bytesWritten
    }

    /** fully written ([success]) or given up: readers stop waiting */
    fun finish(path: String, success: Boolean) {
        val entry = entries[path] ?: return
        if (success) entry.finished = true else entry.failed = true
        // a finished file reads like any other file from now on
        entries.remove(path)
    }

    fun isGrowing(path: String): Boolean = entries.containsKey(path)

    /** how much of the file is written; null once it's complete (or not growing at all) */
    internal fun written(path: String): Long? = entries[path]?.written

    internal fun totalBytes(path: String): Long = entries[path]?.totalBytes ?: -1L

    internal fun failed(path: String): Boolean = entries[path]?.failed == true
}
