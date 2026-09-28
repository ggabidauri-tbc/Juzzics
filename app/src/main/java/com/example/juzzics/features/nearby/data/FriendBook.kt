package com.example.juzzics.features.nearby.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * This phone's lasting id, and the phones it paired with (codes compared once).
 *
 * Nearby gives every connection a new random endpoint id, so to find a friend again each phone
 * advertises its lasting id with its name. A paired phone reconnects without comparing codes,
 * but has to prove it's the same phone: when two phones first pair, they agree on a secret
 * (over the connection the codes protected); on reconnecting each asks the other a random
 * question only the secret answers (HMAC). Someone copying a friend's id doesn't know it.
 */
class FriendBook(context: Context) {

    private val prefs = context.getSharedPreferences("nearby_friends", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    /** a phone this one paired with */
    data class Known(val name: String, val secret: String)

    /** this phone's lasting id: 8 letters / digits, made once */
    val myId: String = prefs.getString(KEY_MY_ID, null) ?: randomText(8, ALPHABET).also { id ->
        prefs.edit { putString(KEY_MY_ID, id) }
    }

    private var known: Map<String, Known> = load()

    val count: Int get() = known.size

    fun known(phoneId: String): Known? = known[phoneId]

    fun remember(phoneId: String, name: String, secret: String) {
        known = known + (phoneId to Known(name, secret))
        save()
    }

    /** keeps the name fresh (friends rename their phones) */
    fun rename(phoneId: String, name: String) {
        val entry = known[phoneId] ?: return
        if (entry.name == name) return
        known = known + (phoneId to entry.copy(name = name))
        save()
    }

    fun forget(phoneId: String) {
        if (phoneId !in known) return
        known = known - phoneId
        save()
    }

    fun forgetAll() {
        known = emptyMap()
        save()
    }

    fun newSecret(): String = randomText(32, HEX)

    fun newNonce(): String = randomText(16, HEX)

    /**
     * the answer to [nonce] that only someone knowing [secret] can give, bound to who proves it
     * to whom (so a question can't be bounced back to get its own answer)
     */
    fun proof(secret: String, nonce: String, prover: String, verifier: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal("$nonce|$prover|$verifier".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun randomText(length: Int, alphabet: String) =
        String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] })

    private fun load(): Map<String, Known> = runCatching {
        val json = JSONObject(prefs.getString(KEY_KNOWN, null) ?: return emptyMap())
        json.keys().asSequence().associateWith { id ->
            val entry = json.getJSONObject(id)
            Known(entry.getString("name"), entry.getString("secret"))
        }
    }.getOrDefault(emptyMap())

    private fun save() {
        val json = JSONObject()
        known.forEach { (id, entry) -> json.put(id, JSONObject().put("name", entry.name).put("secret", entry.secret)) }
        prefs.edit { putString(KEY_KNOWN, json.toString()) }
    }

    companion object {
        private const val KEY_MY_ID = "my_id"
        private const val KEY_KNOWN = "known"
        private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"
        private const val HEX = "0123456789abcdef"

        /** between the name and the id in the advertised name (never typed by anyone) */
        private const val SEPARATOR = '\u001F'

        /** Bluetooth advertisements are small: the name is cut to fit (UTF-8 bytes) */
        private const val MAX_NAME_BYTES = 90

        /**
         * "Nika's phone" + id, as the other phones see it while searching.
         * [bump]: this phone is in "bump to connect" (other bumping phones connect to it by themselves)
         */
        fun encodeName(name: String, phoneId: String, bump: Boolean = false): String {
            var cut = name.replace(SEPARATOR.toString(), "")
            while (cut.toByteArray().size > MAX_NAME_BYTES) cut = cut.dropLast(1)
            return "$cut$SEPARATOR$phoneId" + if (bump) "$SEPARATOR$BUMP_MARK" else ""
        }

        /** the name to show, and the phone's id (null: an older Juzzics without one) */
        fun decodeName(endpointName: String): Pair<String, String?> {
            val parts = endpointName.split(SEPARATOR)
            if (parts.size < 2) return endpointName to null
            return parts[0] to parts[1].ifBlank { null }
        }

        /** the phone is in "bump to connect" */
        fun isBumping(endpointName: String): Boolean = endpointName.split(SEPARATOR).getOrNull(2) == BUMP_MARK

        private const val BUMP_MARK = "b"
    }
}
