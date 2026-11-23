package com.fitzy.apexcleaner.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitzy.apexcleaner.core.Actions
import com.fitzy.apexcleaner.core.CleanReport
import com.fitzy.apexcleaner.core.Health
import com.fitzy.apexcleaner.core.HealthMonitor
import com.fitzy.apexcleaner.core.NeededPermission
import com.fitzy.apexcleaner.core.OtherAppCaches
import com.fitzy.apexcleaner.core.Permissions
import com.fitzy.apexcleaner.core.SafeCleaner
import com.fitzy.apexcleaner.core.HelperState
import com.fitzy.apexcleaner.core.ShizukuBridge
import com.fitzy.apexcleaner.core.WhitelistStore
import com.fitzy.apexcleaner.core.ProtectedApps
import com.fitzy.apexcleaner.core.formatBytes
import com.fitzy.apexcleaner.power.ChargeEngine
import com.fitzy.apexcleaner.power.ChargeMath
import com.fitzy.apexcleaner.power.ChargeSettings
import com.fitzy.apexcleaner.power.ChargeUi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val protectedByUser: Boolean, val automatic: Boolean, val banking: Boolean = false)

data class UiState(
    val health: Health? = null,
    val scan: CleanReport? = null,
    val apps: List<AppEntry> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
    val checking: Boolean = false,
    val updateStatus: String? = null,
    val filesAccess: Boolean = true,
    val otherCacheBytes: Long = 0L,
    val missing: List<NeededPermission> = emptyList(),
    val helper: HelperState = HelperState.NotInstalled,
    val charge: ChargeUi = ChargeUi(),
    val shieldOn: Boolean = false,
    val shieldPausedUntil: Long = 0L,
    val blockedCount: Int = 0,
    val bankingDomains: Int = 0,
    val recentBlocked: List<String> = emptyList(),
    val listDomains: Int = 0,
    val bankingAppsFound: Int = 0,
    val history: List<com.fitzy.apexcleaner.net.DomainStat> = emptyList(),
    val rules: Map<String, com.fitzy.apexcleaner.net.Rule> = emptyMap(),
    val nightClean: Boolean = true,
    val encryptedDns: Boolean = true,
    val limiter: Boolean = true,
    val limiterOn: Boolean = false,
    val limiting: Boolean = false,
    val hotC: Float = 42f,
    val version: String = "",
    val update: com.fitzy.apexcleaner.core.UpdateInfo? = null,
    val breakdown: List<com.fitzy.apexcleaner.core.AppCache> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val store = WhitelistStore(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        _state.update { st -> st.copy(
            nightClean = com.fitzy.apexcleaner.core.AppSettings.nightClean(ctx),
            encryptedDns = com.fitzy.apexcleaner.core.AppSettings.encryptedDns(ctx),
            limiter = com.fitzy.apexcleaner.core.AppSettings.limiter(ctx),
            hotC = ChargeSettings(ctx).hotC,
            version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty(),
            bankingDomains = com.fitzy.apexcleaner.net.SafeLists.bankingDomains.size,
            bankingAppsFound = com.fitzy.apexcleaner.net.SafeLists.bankingBypassPackages.count { p -> runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess },
        ) }
        viewModelScope.launch { com.fitzy.apexcleaner.net.ShieldStats.recent.collect { r -> _state.update { it.copy(recentBlocked = r) } } }
        viewModelScope.launch {
            com.fitzy.apexcleaner.net.ShieldRules.ensureLoaded(ctx)
            // Refresh the log every few seconds while the app is open.
            while (true) {
                val h = com.fitzy.apexcleaner.net.ShieldRules.history.top(200)
                val r = com.fitzy.apexcleaner.net.ShieldRules.rules(ctx)
                _state.update { it.copy(history = h, rules = r) }
                kotlinx.coroutines.delay(3000)
            }
        }
        viewModelScope.launch { com.fitzy.apexcleaner.net.ShieldStats.limiterOn.collect { r -> _state.update { it.copy(limiterOn = r) } } }
        viewModelScope.launch { com.fitzy.apexcleaner.net.ShieldStats.limiting.collect { r -> _state.update { it.copy(limiting = r) } } }
        viewModelScope.launch { com.fitzy.apexcleaner.net.ShieldStats.running.collect { r -> _state.update { it.copy(shieldOn = r) } } }
        com.fitzy.apexcleaner.net.AdBlock.ensureLoaded(ctx)
        viewModelScope.launch { com.fitzy.apexcleaner.net.AdBlock.pausedFlow.collect { t -> _state.update { it.copy(shieldPausedUntil = t) } } }
        viewModelScope.launch { com.fitzy.apexcleaner.net.ShieldStats.failure.collect { f -> if (f != null) _state.update { it.copy(message = f) } } }
        viewModelScope.launch { com.fitzy.apexcleaner.net.ShieldStats.blocked.collect { n -> _state.update { it.copy(blockedCount = n) } } }
        viewModelScope.launch { com.fitzy.apexcleaner.core.UpdateInstaller.status.collect { t -> _state.update { it.copy(updateStatus = t) } } }
        viewModelScope.launch { ChargeEngine.ui.collect { c -> _state.update { it.copy(charge = c) } } }
    }

    fun setChargeLimit(pct: Int) {
        ChargeSettings(ctx).limitPct = ChargeMath.clampLimit(pct)
        ChargeEngine.update(ctx, force = true)
    }

    fun setFastCharge(on: Boolean) {
        ChargeSettings(ctx).fastEnabled = on
        ChargeEngine.update(ctx, force = true)
    }

    private var checkedUpdate = false

    fun refresh() = launchBusy {
        if (!checkedUpdate) {
            checkedUpdate = true
            com.fitzy.apexcleaner.core.UpdateChecker.check(ctx)?.let { u -> _state.update { it.copy(update = u) } }
        }
        ChargeEngine.update(ctx, force = true)
        val health = HealthMonitor(ctx).read()
        val scan = SafeCleaner(ctx).scan()
        _state.value = _state.value.copy(health = health, scan = scan, apps = loadApps(), filesAccess = hasFilesAccess(), otherCacheBytes = OtherAppCaches.clearableBytes(ctx), missing = Permissions.missing(ctx), helper = ShizukuBridge.state(ctx), breakdown = com.fitzy.apexcleaner.core.CacheBreakdown.scan(ctx))
    }

    fun clean() = launchBusy {
        val r = SafeCleaner(ctx).clean()
        val others = OtherAppCaches.clear(ctx)
        val health = HealthMonitor(ctx).read()
        _state.value = _state.value.copy(
            health = health,
            scan = SafeCleaner(ctx).scan(),
            otherCacheBytes = OtherAppCaches.clearableBytes(ctx),
            message = "Freed ${formatBytes(r.totalBytes + others)} " +
                "(${formatBytes(others)} from other apps' caches). Your apps, logins and settings were not touched.",
        )
    }

    fun updateLists() = launchBusy {
        val n = com.fitzy.apexcleaner.net.FilterLists.update(ctx)
        if (n > 0) com.fitzy.apexcleaner.net.ShieldRules.reloadLists(ctx)
        val total = com.fitzy.apexcleaner.net.FilterLists.count(ctx)
        _state.value = _state.value.copy(listDomains = total, message = if (n == 0) "Could not download the ad lists. Check your connection and try again." else "Ad lists updated: $total names..")
    }

    fun setRule(domain: String, rule: com.fitzy.apexcleaner.net.Rule?) {
        com.fitzy.apexcleaner.net.ShieldRules.set(ctx, domain, rule)
        _state.update { it.copy(rules = com.fitzy.apexcleaner.net.ShieldRules.rules(ctx)) }
    }

    fun setNightClean(on: Boolean) {
        com.fitzy.apexcleaner.core.AppSettings.setNightClean(ctx, on)
        if (on) com.fitzy.apexcleaner.core.PowerPolicy.scheduleDeepClean(ctx) else com.fitzy.apexcleaner.core.PowerPolicy.cancelDeepClean(ctx)
        _state.update { it.copy(nightClean = on) }
    }

    /** Streaming priority on/off. If the shield is running it restarts so the new mode takes effect. */
    fun setLimiter(on: Boolean) {
        com.fitzy.apexcleaner.core.AppSettings.setLimiter(ctx, on)
        _state.update { it.copy(limiter = on) }
        if (com.fitzy.apexcleaner.net.ShieldStats.running.value) {
            com.fitzy.apexcleaner.net.ApexVpnService.stop(ctx)
            viewModelScope.launch { kotlinx.coroutines.delay(800); com.fitzy.apexcleaner.net.ApexVpnService.start(ctx) }
        }
    }

    fun setEncryptedDns(on: Boolean) {
        com.fitzy.apexcleaner.core.AppSettings.setEncryptedDns(ctx, on)
        _state.update { it.copy(encryptedDns = on) }
    }

    fun setHeat(c: Float) {
        ChargeSettings(ctx).hotC = c
        ChargeEngine.update(ctx, force = true)
        _state.update { it.copy(hotC = ChargeSettings(ctx).hotC) }
    }

    /** Has its own "Checking…" state so it never waits on, or gets stuck behind, other jobs. */
    fun checkUpdate() {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, message = null) }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { com.fitzy.apexcleaner.core.UpdateChecker.checkNow(ctx) }
            _state.update { st ->
                when (r) {
                    is com.fitzy.apexcleaner.core.UpdateChecker.Outcome.Found -> st.copy(checking = false, update = r.info, message = "Version ${r.info.tag} is ready. Tap Update at the top of the page.")
                    com.fitzy.apexcleaner.core.UpdateChecker.Outcome.UpToDate -> st.copy(checking = false, update = null, message = "You have the latest version.")
                    com.fitzy.apexcleaner.core.UpdateChecker.Outcome.Failed -> st.copy(checking = false, message = "Could not reach the update server. Check your connection and try again.")
                }
            }
        }
    }

    fun clearHistory() {
        com.fitzy.apexcleaner.net.ShieldRules.clearHistory(ctx)
        _state.update { it.copy(history = emptyList()) }
    }

    fun boost() = launchBusy {
        val msg = Actions.boost(ctx)
        _state.value = _state.value.copy(health = HealthMonitor(ctx).read(), message = msg)
    }

    fun coolDown() = launchBusy {
        _state.value = _state.value.copy(message = Actions.coolDown(ctx))
    }

    fun toggleProtected(pkg: String) {
        store.toggle(pkg)
        _state.value = _state.value.copy(apps = _state.value.apps.map {
            if (it.pkg == pkg) it.copy(protectedByUser = !it.protectedByUser) else it
        })
    }

    fun dismissMessage() { _state.value = _state.value.copy(message = null) }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try { withContext(Dispatchers.IO) { block() } } finally { _state.update { it.copy(busy = false) } }
        }
    }

    private fun hasFilesAccess() =
        android.os.Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager()

    private fun loadApps(): List<AppEntry> {
        val pm = ctx.packageManager
        val auto = ProtectedApps(ctx, store).automatic()
        val mine = store.all()
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        return launchable
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != ctx.packageName }
            .map { (p, l) -> AppEntry(p, l, p in mine, p in auto || p in com.fitzy.apexcleaner.net.SafeLists.bankingBypassPackages, p in com.fitzy.apexcleaner.net.SafeLists.bankingBypassPackages) }
            .sortedBy { it.label.lowercase() }
    }
}
