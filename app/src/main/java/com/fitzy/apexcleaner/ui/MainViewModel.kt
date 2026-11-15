package com.fitzy.apexcleaner.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitzy.apexcleaner.core.Actions
import com.fitzy.apexcleaner.core.CleanReport
import com.fitzy.apexcleaner.core.Health
import com.fitzy.apexcleaner.core.HealthMonitor
import com.fitzy.apexcleaner.core.SafeCleaner
import com.fitzy.apexcleaner.core.WhitelistStore
import com.fitzy.apexcleaner.core.ProtectedApps
import com.fitzy.apexcleaner.core.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val protectedByUser: Boolean, val automatic: Boolean)

data class UiState(
    val health: Health? = null,
    val scan: CleanReport? = null,
    val apps: List<AppEntry> = emptyList(),
    val message: String? = null,
    val busy: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app
    private val store = WhitelistStore(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun refresh() = launchBusy {
        val health = HealthMonitor(ctx).read()
        val scan = SafeCleaner(ctx).scan()
        _state.value = _state.value.copy(health = health, scan = scan, apps = loadApps())
    }

    fun clean() = launchBusy {
        val r = SafeCleaner(ctx).clean()
        val health = HealthMonitor(ctx).read()
        _state.value = _state.value.copy(
            health = health,
            scan = SafeCleaner(ctx).scan(),
            message = "Freed ${formatBytes(r.totalBytes)}. Your apps, logins and settings were not touched.",
        )
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
            _state.value = _state.value.copy(busy = true)
            withContext(Dispatchers.IO) { block() }
            _state.value = _state.value.copy(busy = false)
        }
    }

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
            .map { (p, l) -> AppEntry(p, l, p in mine, p in auto) }
            .sortedBy { it.label.lowercase() }
    }
}
