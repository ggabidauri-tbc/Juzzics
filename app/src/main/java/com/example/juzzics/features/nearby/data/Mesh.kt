package com.example.juzzics.features.nearby.data

/**
 * Small group messages hop from phone to phone: positions, meeting points and "come to me".
 * If you're connected to Nika and Nika to Luka, you still see Luka, so a group spread along a
 * trail stays together past one phone's range.
 *
 * Every such message carries who said it first and their counter: each phone handles and passes
 * on a message once (the newest per person and kind), and not further than [MAX_HOPS] phones.
 */
class Mesh(private val myId: () -> String, private val myName: () -> String) {

    private var lastSeq = 0L
    /** newest counter seen, per person + kind of message */
    private val newest = mutableMapOf<String, Long>()

    /** this phone says it: marked as its own, with a new counter */
    fun stamp(message: NearbyMessage): NearbyMessage {
        // the clock keeps counters rising across app restarts
        lastSeq = maxOf(System.currentTimeMillis(), lastSeq + 1)
        return message.copy(origin = myId(), originName = myName(), seq = lastSeq, hops = 0)
    }

    /** true: seen for the first time (handle it, pass it on); false: an old or own copy */
    fun isNew(message: NearbyMessage): Boolean {
        val origin = message.origin ?: return true // an older Juzzics: just this hop
        if (origin == myId()) return false
        val seq = message.seq ?: return true
        val key = "$origin/${kind(message.type)}"
        if (seq <= (newest[key] ?: Long.MIN_VALUE)) return false
        newest[key] = seq
        return true
    }

    /** the copy to pass on to other friends, or null if it went far enough */
    fun forwarded(message: NearbyMessage): NearbyMessage? {
        if (message.origin == null) return null
        val hops = (message.hops ?: 0) + 1
        return if (hops > MAX_HOPS) null else message.copy(hops = hops)
    }

    /** a position and "stopped sharing" must stay in order, and so must a pin and its removal */
    private fun kind(type: String) = when (type) {
        NearbyMessage.LOCATION, NearbyMessage.LOCATION_OFF -> "location"
        NearbyMessage.PIN, NearbyMessage.PIN_CLEAR -> "pin"
        else -> type
    }

    companion object {
        /** the kinds of messages passed on */
        val RELAYED = setOf(
            NearbyMessage.LOCATION,
            NearbyMessage.LOCATION_OFF,
            NearbyMessage.PIN,
            NearbyMessage.PIN_CLEAR,
            NearbyMessage.COME_TO_ME,
        )

        /** a message goes at most this many phones away */
        const val MAX_HOPS = 4
    }
}
