package com.alekseevich47.notes.service

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.alekseevich47.notes.log.AppLog

/**
 * One system screen for autostart and battery.
 *
 * While the app is still optimized, the battery prompt comes first. If that
 * activity does not resolve, the battery-optimization list is the other
 * battery screen. Otherwise the first OEM autostart activity that resolves.
 * Otherwise this app's details. A single call starts one activity.
 */
object OemPowerSettings {
    enum class Kind {
        /** [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] for this package. */
        BATTERY_PROMPT,

        /** [Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS]. */
        BATTERY_LIST,

        /** First resolved Transsion, Xiaomi, or Samsung activity. */
        OEM,

        /** [Settings.ACTION_APPLICATION_DETAILS_SETTINGS] for this package. */
        APP_DETAILS,
    }

    data class OemCandidate(
        val packageName: String,
        val className: String? = null,
        val action: String? = null,
        /** Samsung sleeping-apps deeplink: 0 sleeping, 1 deep sleeping, 2 never sleeping. */
        val activityType: Int? = null,
    )

    data class Choice(
        val kind: Kind,
        val oem: OemCandidate? = null,
    )

    /**
     * Tried in order. Only the first activity [PackageManager] resolves is opened.
     * Transsion covers Tecno, Infinix, and HiOS. Xiaomi covers MIUI and HyperOS.
     * Samsung covers the battery screen and the sleeping-apps list.
     */
    val OEM_CANDIDATES: List<OemCandidate> = listOf(
        OemCandidate(
            "com.transsion.phonemaster",
            "com.cyin.himgr.autostart.AutoStartActivity",
        ),
        OemCandidate(
            "com.transsion.phonemaster",
            "com.transsion.phonemaster.startupmanager.StartupManagerActivity",
        ),
        OemCandidate(
            "com.transsion.phonemanager",
            "com.cyin.himgr.autostart.AutoStartActivity",
        ),
        OemCandidate(
            packageName = "com.mediatek.autobootcontroller",
            className = "com.mediatek.autobootcontroller.AutoBootAppManageActivity",
            action = "com.mediatek.autobootcontroller.AUTO_BOOT",
        ),
        OemCandidate(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        OemCandidate(
            packageName = "com.samsung.android.lool",
            action = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY",
            activityType = SLEEPING_APPS,
        ),
        OemCandidate(
            packageName = "com.samsung.android.sm",
            action = "com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY",
            activityType = SLEEPING_APPS,
        ),
        OemCandidate(
            packageName = "com.samsung.android.lool",
            className = "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity",
            activityType = SLEEPING_APPS,
        ),
        OemCandidate(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.battery.BatteryActivity",
        ),
        OemCandidate(
            "com.samsung.android.lool",
            "com.samsung.android.sm.battery.ui.BatteryActivity",
        ),
        OemCandidate(
            "com.samsung.android.sm",
            "com.samsung.android.sm.ui.battery.BatteryActivity",
        ),
    )

    /**
     * @param oemResolved parallel to [OEM_CANDIDATES]. Missing or extra flags
     * are treated as not resolved.
     */
    fun choose(
        ignoringBatteryOptimizations: Boolean,
        batteryPromptResolves: Boolean,
        batteryListResolves: Boolean,
        oemResolved: List<Boolean>,
    ): Choice {
        if (!ignoringBatteryOptimizations) {
            if (batteryPromptResolves) return Choice(Kind.BATTERY_PROMPT)
            if (batteryListResolves) return Choice(Kind.BATTERY_LIST)
        }
        val index = oemResolved.indexOfFirst { it }
        val oem = OEM_CANDIDATES.getOrNull(index)
        if (index >= 0 && oem != null) return Choice(Kind.OEM, oem)
        return Choice(Kind.APP_DETAILS)
    }

    fun open(context: Context) {
        val packageName = context.packageName
        val manager = context.packageManager
        val ignoring = ignoringBatteryOptimizations(context)
        val choice = choose(
            ignoringBatteryOptimizations = ignoring,
            batteryPromptResolves = resolves(manager, batteryPromptIntent(packageName)),
            batteryListResolves = resolves(manager, batteryListIntent()),
            oemResolved = OEM_CANDIDATES.map { resolves(manager, oemIntent(it)) },
        )
        val intent = when (choice.kind) {
            Kind.BATTERY_PROMPT -> batteryPromptIntent(packageName)
            Kind.BATTERY_LIST -> batteryListIntent()
            Kind.OEM -> choice.oem?.let(::oemIntent) ?: appDetailsIntent(packageName)
            Kind.APP_DETAILS -> appDetailsIntent(packageName)
        }
        if (context.findActivity() == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            AppLog.i(TAG, "open ${choice.kind} pkg=${choice.oem?.packageName ?: packageName}")
        } catch (error: ActivityNotFoundException) {
            AppLog.e(TAG, "power screen missing ${choice.kind}", error)
        }
    }

    private fun ignoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)
    }

    private fun batteryPromptIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
    }

    private fun batteryListIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    private fun appDetailsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    }

    private fun oemIntent(candidate: OemCandidate): Intent {
        val intent = Intent()
        if (!candidate.action.isNullOrEmpty()) intent.action = candidate.action
        if (!candidate.className.isNullOrEmpty()) {
            intent.component = ComponentName(candidate.packageName, candidate.className)
        } else {
            intent.setPackage(candidate.packageName)
        }
        candidate.activityType?.let { intent.putExtra(EXTRA_SAMSUNG_ACTIVITY_TYPE, it) }
        return intent
    }

    private fun resolves(manager: PackageManager, intent: Intent): Boolean {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.resolveActivity(intent, 0)
            }
            info != null
        } catch (error: Exception) {
            false
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context = this
        repeat(6) {
            if (current is Activity) return current
            if (current !is ContextWrapper) return null
            current = current.baseContext ?: return null
        }
        return null
    }

    private const val TAG = "WarmupPower"
    private const val EXTRA_SAMSUNG_ACTIVITY_TYPE = "activity_type"
    private const val SLEEPING_APPS = 0
}
