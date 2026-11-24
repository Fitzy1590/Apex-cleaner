package com.fitzy.apexcleaner.net

/** Decides what to do with one DNS name. Safe domains always win over blocklists. */
class FilterEngine(@Volatile var blocks: DomainTrie, private val safe: DomainTrie) {
    enum class Verdict { ALLOW, SAFE_PASSTHROUGH, BLOCK }

    /** Your own choices from the log. Allow beats every blocklist; Block cannot override banking or push names. */
    @Volatile var userAllow: DomainTrie = DomainTrie()
    @Volatile var userDeny: DomainTrie = DomainTrie()

    fun decide(domain: String): Verdict = when {
        userAllow.matches(domain) -> Verdict.ALLOW
        safe.matches(domain) -> Verdict.SAFE_PASSTHROUGH
        userDeny.matches(domain) -> Verdict.BLOCK
        blocks.matches(domain) -> Verdict.BLOCK
        else -> Verdict.ALLOW
    }

    /**
     * Like [decide], but also looks at the alias names (CNAMEs) the answer came back through. A harmless-looking name
     * that is secretly an alias of a listed tracker is blocked. Your Allow choice and the safe lists still win.
     */
    fun decideWithAliases(domain: String, aliases: List<String>): Verdict {
        val first = decide(domain)
        if (first != Verdict.ALLOW || userAllow.matches(domain)) return first
        return if (aliases.any { decide(it) == Verdict.BLOCK }) Verdict.BLOCK else Verdict.ALLOW
    }

    companion object {
        /** Starter list plus any downloaded lists. */
        fun buildBlocks(extra: Sequence<String>): DomainTrie = DomainTrie.fromList(extra).also { t -> SafeLists.starterBlocks.forEach(t::add) }

        fun withDefaults(extraBlockLines: Sequence<String> = emptySequence()): FilterEngine {
            val b = DomainTrie.fromList(extraBlockLines)
            SafeLists.starterBlocks.forEach(b::add)
            val s = DomainTrie()
            (SafeLists.bankingDomains + SafeLists.essentialDomains).forEach(s::add)
            return FilterEngine(b, s)
        }
    }
}
