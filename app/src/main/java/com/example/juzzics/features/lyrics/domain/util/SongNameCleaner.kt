package com.example.juzzics.features.lyrics.domain.util

/** A guess of a song's real artist + title, for searching lyrics. */
data class SongGuess(val artist: String, val title: String)

/**
 * Turns YouTube-style file names into searchable artist/title guesses:
 *
 * "Aerosmith - Dream on Lyrics"             -> Aerosmith / Dream on
 * "Aerosmith - Crazy (official music video)" -> Aerosmith / Crazy
 * "Blank Space - Taylor Swift Acoustic (english subtitles)" -> both orders are guessed
 */
object SongNameCleaner {

    /**
     * Word beginnings that mark a bracket as YouTube clutter, not part of the song name.
     * Beginnings (not whole words) so misspellings like "subtitiel" or "lyrcis video" still match.
     */
    private val clutterStems = listOf(
        """offici""", """video""", """audio""", """lyric""", """letra""", """subt""", """remaster""",
        """visuali""", """karaok""", """slowed""", """reverb""", """sped\s*up""", """bass\s*boost""",
        """live\b""", """acoust""", """cover\b""", """version""", """legend""", """tradu""", """перев""",
        """текст""", """hd\b""", """hq\b""", """4k\b""", """1080""", """720""", """mv\b""", """m/v""",
        """explicit""", """clean\b""", """full\s+album""", """on\s+screen""", """8d\b""",
    ).joinToString("|")

    private val bracketed = Regex("""[(\[{【「]([^)\]}】」]*)[)\]}】」]""")
    private val clutterInside = Regex("""\b($clutterStems)""", RegexOption.IGNORE_CASE)
    private val anyBracket = Regex("""[(\[{【「][^)\]}】」]*[)\]}】」]""")
    private val trailingClutter = Regex(
        """[\s\-|:,]*\b(official\s+(music\s+)?video|official\s+audio|lyric\s+video|lyrics?|audio|video|hd|hq|4k|mv)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val featuring = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val separators = Regex("""\s+[-–—|~]\s+|\s*[-–—]{2,}\s*""")
    private val spaces = Regex("""\s+""")

    /** "Crazy (official music video)" -> "Crazy"; keeps brackets that are part of the name */
    fun clean(text: String): String {
        var s = text.replace('_', ' ')
        s = bracketed.replace(s) { match ->
            if (clutterInside.containsMatchIn(match.groupValues[1])) " " else match.value
        }
        s = featuring.replace(s, "")
        repeat(3) { s = trailingClutter.replace(s, "") }
        return s.replace(spaces, " ").trim(' ', '-', '|', ':', ',', '.', '"', '\'')
    }

    /**
     * Guesses to try, most likely first. [artistTag] is the file's artist ("" when unknown).
     */
    fun guesses(rawTitle: String, artistTag: String): List<SongGuess> {
        val title = clean(rawTitle)
        val artist = clean(artistTag)
        val parts = title.split(separators, limit = 2).map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<SongGuess>()

        if (parts.size == 2) {
            val (left, right) = parts
            when {
                // "Aerosmith - Dream On" with artist tag Aerosmith
                artist.isNotEmpty() && left.equals(artist, ignoreCase = true) -> result += SongGuess(artist, right)
                artist.isNotEmpty() && right.equals(artist, ignoreCase = true) -> result += SongGuess(artist, left)
            }
            result += SongGuess(left, right)   // "Artist - Title" (most common)
            result += SongGuess(right, left)   // "Title - Artist"
        } else if (artist.isNotEmpty()) {
            result += SongGuess(artist, title)
        }
        return result.distinct()
    }

    /**
     * Free-text searches (whole name, without any brackets), e.g. "Blank Space Taylor Swift ...".
     * Long names also get a shorter version (first 4 words): leftovers like "the grammy museum"
     * can make a search find nothing.
     */
    fun freeTexts(rawTitle: String, artistTag: String): List<String> {
        val title = clean(rawTitle).replace(anyBracket, " ").replace(separators, " ")
        val artist = clean(artistTag)
        val text = (if (artist.isNotEmpty() && !title.contains(artist, ignoreCase = true)) "$artist $title" else title)
            .replace(spaces, " ").trim(' ', '.')
        if (text.isEmpty()) return emptyList()
        val words = text.split(' ')
        return if (words.size > 4) listOf(text, words.take(4).joinToString(" ")) else listOf(text)
    }

    /** lowercase words, for comparing names ("Don't Stop" -> [dont, stop]) */
    fun words(text: String): Set<String> =
        text.lowercase()
            .replace(Regex("""['’`]"""), "")
            .split(Regex("""[^\p{L}\p{N}]+"""))
            .filter { it.isNotBlank() }
            .toSet()
}
