package io.github.phiscript.capture

/** A confirmation belongs to one visible identity and expires without fresh camera evidence. */
internal class RecognitionConfirmation(private val maximumAgeMs: Long = 4000) {
    data class Offer(val token: Long, val key: String)
    init { require(maximumAgeMs > 0) }
    private var generation = 0L
    private var offer: Offer? = null
    private var lastSeen = Long.MIN_VALUE
    private var lastObserved = Long.MIN_VALUE

    fun propose(key: String, frameMs: Long): Offer {
        require(key.isNotBlank() && frameMs >= lastObserved)
        lastObserved = frameMs
        lastSeen = frameMs
        return Offer(++generation, key).also { offer = it }
    }
    fun observe(key: String?, frameMs: Long, invalidScreen: Boolean = false) {
        if (frameMs <= lastObserved) return
        lastObserved = frameMs
        val current = offer ?: return
        if (invalidScreen || (key != null && key != current.key)) invalidate()
        else if (key == current.key) lastSeen = frameMs
    }
    fun pending(now: Long): Offer? {
        if (now < lastSeen || (lastSeen != Long.MIN_VALUE && now - lastSeen > maximumAgeMs)) invalidate()
        return offer
    }
    fun accept(token: Long, now: Long): Boolean {
        val current = pending(now) ?: return false
        if (current.token != token) return false
        invalidate()
        return true
    }
    fun invalidate() { offer = null; lastSeen = Long.MIN_VALUE }
}
