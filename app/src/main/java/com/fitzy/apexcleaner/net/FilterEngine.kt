package com.fitzy.apexcleaner.net

/** Decides what to do with one DNS name. Safe domains always win over blocklists. */
class FilterEngine(private val blocks: DomainTrie, private val safe: DomainTrie) {
    enum class Verdict { ALLOW, SAFE_PASSTHROUGH, BLOCK }

    fun decide(domain: String): Verdict = when {
        safe.matches(domain) -> Verdict.SAFE_PASSTHROUGH
        blocks.matches(domain) -> Verdict.BLOCK
        else -> Verdict.ALLOW
    }

    companion object {
        fun withDefaults(extraBlockLines: Sequence<String> = emptySequence()): FilterEngine {
            val b = DomainTrie.fromList(extraBlockLines)
            SafeLists.starterBlocks.forEach(b::add)
            val s = DomainTrie()
            (SafeLists.bankingDomains + SafeLists.essentialDomains).forEach(s::add)
            return FilterEngine(b, s)
        }
    }
}
