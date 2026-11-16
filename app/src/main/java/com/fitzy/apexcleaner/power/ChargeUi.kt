package com.fitzy.apexcleaner.power

data class CurvePoint(val level: Int, val watts: Float)

/** Everything the charging card and the lock-screen notification show. */
data class ChargeUi(
    val pluggedIn: Boolean = false,
    val level: Int = 0,
    val full: Boolean = false,
    val tempC: Float = 0f,
    val voltageV: Float = 0f,
    val currentA: Float? = null,
    val watts: Float? = null,
    val profile: ChargeProfile = ChargeProfile.NONE,
    val etaMin: Int? = null,
    val etaMax: Int? = null,
    val fastActive: Boolean = false,
    val tooWarm: Boolean = false,
    val limitPct: Int = ChargeMath.DEFAULT_LIMIT,
    val fastEnabled: Boolean = true,
    val curve: List<CurvePoint> = emptyList(),
) {
    val etaText: String
        get() = when {
            !pluggedIn -> ""
            full -> "Fully charged"
            etaMin != null && etaMax != null -> "Full in ${ChargeMath.formatEta(etaMin, etaMax)}"
            else -> "Estimating time to full…"
        }
}
