package com.fitzy.apexcleaner.net

import android.content.Context
import java.io.File

enum class Rule { ALLOW, DENY }

/** Your Allow / Block choices plus the lookup history, saved privately on the device. */
object ShieldRules {
    private const val PREFS = "shield_rules"
    val history = DnsHistory()
    @Volatile var live: FilterEngine? = null
    @Volatile private var loaded = false
    private var sinceSave = 0

    private fun file(ctx: Context) = File(ctx.filesDir, "dns_history.tsv")

    @Synchronized fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        runCatching { history.load(file(ctx).readLines()) }
    }

    fun rules(ctx: Context): Map<String, Rule> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getStringSet("allow", emptySet()).orEmpty().associateWith { Rule.ALLOW } +
            p.getStringSet("deny", emptySet()).orEmpty().associateWith { Rule.DENY }
    }

    /** Sets (or with null clears) the rule for a name and applies it to the running shield straight away. */
    fun set(ctx: Context, domain: String, rule: Rule?) {
        val d = domain.lowercase()
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val allow = p.getStringSet("allow", emptySet()).orEmpty() - d
        val deny = p.getStringSet("deny", emptySet()).orEmpty() - d
        p.edit().putStringSet("allow", if (rule == Rule.ALLOW) allow + d else allow)
            .putStringSet("deny", if (rule == Rule.DENY) deny + d else deny).apply()
        apply(ctx, live)
    }

    fun apply(ctx: Context, engine: FilterEngine?) {
        engine ?: return
        val r = rules(ctx)
        engine.userAllow = DomainTrie().also { t -> r.filterValues { it == Rule.ALLOW }.keys.forEach(t::add) }
        engine.userDeny = DomainTrie().also { t -> r.filterValues { it == Rule.DENY }.keys.forEach(t::add) }
    }

    fun record(ctx: Context, domain: String, blocked: Boolean) {
        history.record(domain, blocked, System.currentTimeMillis())
        if (++sinceSave >= 40) save(ctx)
    }

    @Synchronized fun save(ctx: Context) {
        sinceSave = 0
        runCatching { file(ctx).writeText(history.toLines().joinToString("\n")) }
    }

    fun clearHistory(ctx: Context) { history.forget(); file(ctx).delete() }
}
