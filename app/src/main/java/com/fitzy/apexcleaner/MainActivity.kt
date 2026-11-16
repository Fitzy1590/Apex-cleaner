package com.fitzy.apexcleaner

import android.Manifest
import android.app.UiModeManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.fitzy.apexcleaner.core.NeededPermission
import com.fitzy.apexcleaner.core.PowerPolicy
import com.fitzy.apexcleaner.core.TunerService
import com.fitzy.apexcleaner.ui.MainViewModel
import com.fitzy.apexcleaner.ui.mobile.MobileDashboard
import com.fitzy.apexcleaner.ui.tv.TvDashboard

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val isTv: Boolean
        get() = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType ==
            Configuration.UI_MODE_TYPE_TELEVISION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PowerPolicy.scheduleLightCheck(this)
        PowerPolicy.scheduleDeepClean(this)
        val tv = isTv
        setContent {
            val state by vm.state.collectAsState()
            val notifPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { TunerService.start(this); vm.refresh() }
            val grant: (NeededPermission) -> Unit = { p ->
                when (p) {
                    NeededPermission.Notifications ->
                        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    NeededPermission.AllFiles -> openAllFilesAccess()
                    NeededPermission.UsageAccess -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            LaunchedEffect(Unit) {
                vm.refresh()
                TunerService.start(this@MainActivity)
            }
            if (tv) {
                TvDashboard(state, vm::clean, vm::boost, vm::coolDown, vm::toggleProtected, vm::dismissMessage, grant)
            } else {
                MobileDashboard(
                    state, vm::clean, vm::boost, vm::coolDown, vm::toggleProtected,
                    vm::dismissMessage, ::openAllFilesAccess, ::openStorageSettings, grant,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun openStorageSettings() {
        startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
    }

    private fun openAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")),
            )
        }
    }
}
