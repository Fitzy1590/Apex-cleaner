package com.fitzy.apexcleaner.net

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** One time-window rule: between [startMin] and [endMin] (minutes after midnight, may wrap past midnight) the chosen names are not looked up. */
data class QuietRule(val name: String, val social: Boolean, val custom: List<String>, val startMin: Int, val endMin: Int, val enabled: Boolean) {
    fun activeAt(minute: Int): Boolean = enabled && when {
        startMin == endMin -> false
        startMin < endMin -> minute in startMin until endMin
        else -> minute >= startMin || minute < endMin
    }

    fun encode() = listOf(name.replace("|", " "), if (social) "1" else "0", custom.joinToString(","), startMin, endMin, if (enabled) "1" else "0").joinToString("|")

    companion object {
        fun decode(line: String): QuietRule? {
            val f = line.split("|")
            if (f.size != 6) return null
            val s = f[3].toIntOrNull() ?: return null; val e = f[4].toIntOrNull() ?: return null
            if (s !in 0..1439 || e !in 0..1439) return null
            return QuietRule(f[0], f[1] == "1", f[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }, s, e, f[5] == "1")
        }
        fun fmt(min: Int) = "%02d:%02d".format(min / 60, min % 60)
    }
}

/** Quiet-hours engine. Banking, clock and push names are checked first elsewhere (FilterEngine), so they can never be blocked here. */
object QuietHours {
    val social: List<String> = listOf(
        "facebook.com", "fbcdn.net", "instagram.com", "cdninstagram.com", "threads.net", "tiktok.com", "tiktokv.com", "tiktokcdn.com",
        "twitter.com", "x.com", "twimg.com", "snapchat.com", "sc-cdn.net", "reddit.com", "redd.it", "redditstatic.com", "redditmedia.com",
        "pinterest.com", "pinimg.com", "tumblr.com")
    private val socialTrie by lazy { DomainTrie().also { t -> social.forEach(t::add) } }
    private const val PREFS = "quiet_hours"

    @Volatile private var cache: List<Pair<QuietRule, DomainTrie>> = emptyList()
    @Volatile private var loaded = false

    fun parse(lines: List<String>) = lines.mapNotNull { QuietRule.decode(it) }

    fun rules(ctx: Context): List<QuietRule> =
        parse(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("rules", "").orEmpty().split("\n").filter { it.isNotBlank() })

    fun save(ctx: Context, rules: List<QuietRule>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("rules", rules.joinToString("\n") { it.encode() }).apply()
        cache = build(rules); loaded = true
    }

    private fun build(rules: List<QuietRule>) = rules.map { r -> r to DomainTrie().also { t -> r.custom.forEach(t::add) } }

    /** True if [name] is inside a rule that is active at [minute]. */
    fun blocks(ctx: Context, name: String, minute: Int): Boolean {
        if (!loaded) { cache = build(rules(ctx)); loaded = true }
        return isBlocked(cache, name, minute)
    }

    internal fun isBlocked(rules: List<Pair<QuietRule, DomainTrie>>, name: String, minute: Int) =
        rules.any { (r, custom) -> r.activeAt(minute) && ((r.social && socialTrie.matches(name)) || custom.matches(name)) }

    internal fun blocksNow(rules: List<QuietRule>, name: String, minute: Int) = isBlocked(build(rules), name, minute)
}

/** 4-digit PIN kept as a salted hash; five wrong tries lock changes for five minutes. */
class PinLock(private var salt: String = "", private var hash: String = "", private var fails: Int = 0, private var lockedUntil: Long = 0L) {
    val isSet get() = hash.isNotEmpty()
    fun state() = listOf(salt, hash, fails.toString(), lockedUntil.toString()).joinToString("|")

    fun lockedFor(now: Long) = (lockedUntil - now).coerceAtLeast(0)

    fun set(pin: String): Boolean {
        if (!valid(pin)) return false
        val s = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        salt = s; hash = digest(pin, s); fails = 0; lockedUntil = 0; return true
    }

    fun check(pin: String, now: Long): Boolean {
        if (!isSet || lockedFor(now) > 0 || !valid(pin)) { if (isSet && lockedFor(now) == 0L) fail(now); return false }
        if (MessageDigest.isEqual(digest(pin, salt).toByteArray(), hash.toByteArray())) { fails = 0; return true }
        fail(now); return false
    }

    private fun fail(now: Long) { if (++fails >= 5) { fails = 0; lockedUntil = now + 5 * 60_000L } }

    companion object {
        fun valid(pin: String) = pin.length == 4 && pin.all { it in '0'..'9' }
        private fun digest(pin: String, salt: String): String =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 20_000, 256)).encoded.joinToString("") { "%02x".format(it) }

        fun parse(s: String?): PinLock {
            val f = s?.split("|") ?: return PinLock()
            return if (f.size == 4) PinLock(f[0], f[1], f[2].toIntOrNull() ?: 0, f[3].toLongOrNull() ?: 0L) else PinLock()
        }

        private const val PREFS = "quiet_hours"
        fun load(ctx: Context) = parse(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pin", null))
        fun store(ctx: Context, p: PinLock) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pin", p.state()).apply()
    }
}
