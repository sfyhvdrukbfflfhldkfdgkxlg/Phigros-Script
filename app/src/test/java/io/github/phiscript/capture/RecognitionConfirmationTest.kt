package io.github.phiscript.capture

import org.junit.Assert.*
import org.junit.Test

class RecognitionConfirmationTest {
    @Test fun confirmationIsSingleUse() {
        val state = RecognitionConfirmation()
        val offer = state.propose("song/IN", 100)
        assertTrue(state.accept(offer.token, 200))
        assertFalse(state.accept(offer.token, 201))
    }
    @Test fun unseenCandidateExpires() {
        val state = RecognitionConfirmation(1000)
        val offer = state.propose("song/IN", 100)
        assertFalse(state.accept(offer.token, 1101))
        assertNull(state.pending(1101))
    }
    @Test fun missingOcrDoesNotRefreshConsent() {
        val state = RecognitionConfirmation(1000)
        val offer = state.propose("song/IN", 100)
        state.observe(null, 800)
        assertFalse(state.accept(offer.token, 1101))
    }
    @Test fun matchingFreshOcrRefreshesConsent() {
        val state = RecognitionConfirmation(1000)
        val offer = state.propose("song/IN", 100)
        state.observe("song/IN", 800)
        assertTrue(state.accept(offer.token, 1500))
    }
    @Test fun differentDifficultyRevokesConsentImmediately() {
        val state = RecognitionConfirmation()
        val offer = state.propose("song/IN", 100)
        state.observe("song/HD", 200)
        assertFalse(state.accept(offer.token, 201))
    }
    @Test fun resultScreenRevokesEvenWhenTitleMatches() {
        val state = RecognitionConfirmation()
        val offer = state.propose("song/IN", 100)
        state.observe("song/IN", 200, invalidScreen = true)
        assertFalse(state.accept(offer.token, 201))
    }
    @Test fun staleFrameCannotExtendLease() {
        val state = RecognitionConfirmation(1000)
        val offer = state.propose("song/IN", 100)
        state.observe("song/IN", 100)
        assertFalse(state.accept(offer.token, 1101))
    }
    @Test fun outOfOrderFrameCannotReplaceCurrentEvidence() {
        val state = RecognitionConfirmation()
        val offer = state.propose("song/IN", 500)
        state.observe("other/IN", 400)
        assertTrue(state.accept(offer.token, 600))
    }
    @Test fun oldButtonCannotConfirmReplacementOffer() {
        val state = RecognitionConfirmation()
        val old = state.propose("song/IN", 100)
        val current = state.propose("other/IN", 200)
        assertFalse(state.accept(old.token, 300))
        assertTrue(state.accept(current.token, 301))
    }
    @Test fun leavingGameRevokesPendingConsent() {
        val state = RecognitionConfirmation()
        val offer = state.propose("song/IN", 100)
        state.invalidate()
        assertFalse(state.accept(offer.token, 101))
    }
    @Test fun futureDatedEvidenceCannotBeConfirmed() {
        val state = RecognitionConfirmation()
        val offer = state.propose("song/IN", 200)
        assertFalse(state.accept(offer.token, 100))
    }
}
