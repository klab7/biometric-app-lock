package eu.hxreborn.biometricapplock.hook

import android.content.Intent
import android.os.SystemClock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// one-time tokens bridging the hook and the auth activity, each holding the exact launch we
// intercepted so the auth pass can resume it instead of a rebuilt intent
private const val TOKEN_TTL_MS = 2 * 60 * 1000L

internal class PendingAuth(
    val issuedAt: Long,
    val launch: Intent?,
)

private val pending = ConcurrentHashMap<String, PendingAuth>()

internal fun createToken(): String {
    removeExpiredTokens()
    val token = UUID.randomUUID().toString()
    pending[token] = PendingAuth(SystemClock.elapsedRealtime(), null)
    return token
}

internal fun stashLaunch(
    token: String,
    intent: Intent,
) {
    pending.computeIfPresent(token) { _, current -> PendingAuth(current.issuedAt, Intent(intent)) }
}

internal fun discardToken(token: String) {
    pending.remove(token)
}

// removes the token and returns its entry when still valid, else null
internal fun consumeToken(token: String): PendingAuth? {
    val entry = pending.remove(token) ?: return null
    return entry.takeIf { SystemClock.elapsedRealtime() - it.issuedAt <= TOKEN_TTL_MS }
}

private fun removeExpiredTokens() {
    val cutoff = SystemClock.elapsedRealtime() - TOKEN_TTL_MS
    pending.entries.removeIf { it.value.issuedAt < cutoff }
}
