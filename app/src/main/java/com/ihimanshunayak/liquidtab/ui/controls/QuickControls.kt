/*
 * Copyright (c) A|iens. All rights reserved.
 *
 * Liquid Tab Launcher — quick controls.
 *
 * Name      : QuickControls.kt
 * Version   : 1.0.0
 * Purpose   : The Control Center's tiles: Wi-Fi, Bluetooth, Do not disturb,
 *             torch, brightness, rotation lock, and media transport. Every tile
 *             reads a real platform state and writes through a real platform
 *             API, because a launcher's control center that shows a toggle as
 *             "on" when it is off is worse than not having one.
 *
 * Notes     : The platform does not expose a like-for-like setter for most of
 *             these. Wi-Fi and Bluetooth are switched by opening the system
 *             panel on modern Android — `setWifiEnabled` was removed from the
 *             public API in Android 10 — so the tile is honest about what it
 *             does rather than silently failing. Torch, brightness, DND and
 *             rotation all have real setters, and those are used directly.
 *
 *             Nothing here polls. State comes from broadcasts, from
 *             `AudioManager`'s own listeners, and from a read at the moment the
 *             tile is drawn.
 */

package com.ihimanshunayak.liquidtab.ui.controls

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ihimanshunayak.liquidtab.R
import com.ihimanshunayak.liquidtab.util.toast

private const val TAG = "QuickControls"

/**
 * Live platform state for the tiles, and the actions that change it.
 *
 * Held as one object per composition of the control center so the receiver is
 * registered exactly while the panel is on screen — a Control Center that kept
 * listening in the background would be a registered receiver running for the
 * whole session.
 */
class QuickControlsState(
    private val context: Context,
    /**
     * The activity whose window the brightness slider drives. Optional so the
     * state can be built in a preview or a test, but a real panel is always
     * hosted by one — window brightness needs no permission and therefore works
     * even when WRITE_SETTINGS has never been granted.
     */
    private val activity: android.app.Activity? = null,
) {

    var wifiEnabled by mutableStateOf(false)
        private set

    var bluetoothEnabled by mutableStateOf(false)
        private set

    var dndEnabled by mutableStateOf(false)
        private set

    var torchEnabled by mutableStateOf(false)
        private set

    var brightness by mutableFloatStateOf(0.5f)
        private set

    /**
     * True once the user has moved the brightness slider without the special
     * WRITE_SETTINGS grant, so the panel can offer the one-tap way to get it.
     */
    var brightnessNeedsPermission by mutableStateOf(false)
        private set

    /**
     * True once the app has been refused the runtime Bluetooth permission, so
     * the tile can say why it reads off instead of silently lying.
     */
    var bluetoothNeedsPermission by mutableStateOf(false)
        private set

    /** 0 = auto, 1 = portrait locked, 2 = landscape locked, 3 = free rotation. */
    var rotationLocked by mutableStateOf(false)
        private set

    var ringerMode by mutableIntStateOf(AudioManager.RINGER_MODE_NORMAL)
        private set

    /**
     * True when the system reports a torch-capable camera at all.
     *
     * Capability, not state: whether the torch is *on* is only knowable from
     * the camera service's own callback, which is registered in [start].
     */
    var torchAvailable by mutableStateOf(false)
        private set

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var torchCameraId: String? = null

    /**
     * The camera service's torch state, which is the only source that knows
     * about a torch lit from somewhere other than this app — the system quick
     * settings, a camera app, or a widget.
     */
    private var torchCallback: CameraManager.TorchCallback? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = read()
    }

    /** Registers for the broadcasts that carry these states, then reads them once. */
    fun start() {
        val filter = IntentFilter().apply {
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        runCatching { context.registerReceiver(receiver, filter) }
            .onFailure { Log.w(TAG, "Could not register quick controls receiver", it) }

        torchCameraId = runCatching {
            cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        torchAvailable = torchCameraId != null

        // Whether the torch is lit is not readable as a property; the camera
        // service reports it through this callback, and reports changes made
        // outside this app too. Registered only while the panel is composed.
        torchCameraId?.let { id ->
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == torchCameraId) torchEnabled = enabled
                }
            }
            torchCallback = callback
            runCatching { cameraManager?.registerTorchCallback(callback, null) }
                .onFailure { Log.w(TAG, "Could not register torch callback", it) }
        }

        read()
    }

    fun stop() {
        runCatching { context.unregisterReceiver(receiver) }
        torchCallback?.let { callback ->
            runCatching { cameraManager?.unregisterTorchCallback(callback) }
                .onFailure { Log.w(TAG, "Could not unregister torch callback", it) }
        }
        torchCallback = null
    }

    /**
     * Reads every value from its platform source. Called on start and on each
     * broadcast; never on a timer.
     */
    fun read() {
        val wifi = context.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        wifiEnabled = wifi?.isWifiEnabled == true

        bluetoothEnabled = runCatching {
            // getDefaultAdapter() is deprecated in favour of BluetoothManager,
            // which is what the platform now routes through anyway. From API 31
            // this needs BLUETOOTH_CONNECT at runtime; without it the call
            // throws and the tile would read "off" while Bluetooth is on, so the
            // refusal is recorded rather than swallowed.
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)
                ?.adapter?.isEnabled == true
        }.onFailure {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothNeedsPermission = !hasBluetoothPermission()
            }
        }.getOrDefault(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermission()) {
            bluetoothNeedsPermission = true
        }

        dndEnabled = runCatching {
            notificationManager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        }.getOrDefault(false)

        torchEnabled = runCatching {
            // The callback above owns this value; the boolean state is only kept
            // so a panel that opens before the first callback still shows
            // something sane. The capability check must NOT be folded into the
            // state, or the tile could never read as on.
            torchCameraId != null && torchEnabled
        }.getOrDefault(false)

        ringerMode = runCatching { audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL }
            .getOrDefault(AudioManager.RINGER_MODE_NORMAL)

        brightness = runCatching {
            // The window override, when set, is what the user is actually
            // looking at — the system value behind it can be anything. Reading
            // the system value first would make the slider snap back to a level
            // that is not in effect.
            val window = activity?.window?.attributes?.screenBrightness ?: -1f
            if (window >= 0f) {
                window
            } else {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            }
        }.getOrDefault(0.5f)

        rotationLocked = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0
        }.getOrDefault(false)
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /** True when the runtime Bluetooth permission has been granted. */
    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Re-reads after a permission result, so a granted tile stops lying. */
    fun onPermissionResult() {
        bluetoothNeedsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasBluetoothPermission()
        read()
    }

    /**
     * Opens the system's Wi-Fi panel. Android removed the public Wi-Fi setter,
     * so this is the honest implementation of the tile rather than one that
     * appears to toggle and does nothing.
     */
    fun openWifiSettings() = openPanel(Settings.ACTION_WIFI_SETTINGS)

    fun openBluetoothSettings() = openPanel(Settings.ACTION_BLUETOOTH_SETTINGS)

    /**
     * Toggles Do not disturb. Uses the notification policy access the user must
     * grant; without it the system panel is opened so the tile still does
     * something.
     */
    fun toggleDnd() {
        if (notificationManager?.isNotificationPolicyAccessGranted != true) {
            openPanel(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return
        }
        val target = if (dndEnabled) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        }
        runCatching { notificationManager?.setInterruptionFilter(target) }
            .onFailure { Log.w(TAG, "DND toggle failed", it) }
    }

    /** Toggles the torch through the camera service — a real setter. */
    fun toggleTorch() {
        val id = torchCameraId ?: run {
            toast(context, context.getString(R.string.toast_no_torch))
            return
        }
        runCatching {
            cameraManager?.setTorchMode(id, !torchEnabled)
            torchEnabled = !torchEnabled
        }.onFailure {
            Log.w(TAG, "Torch toggle failed", it)
            toast(context, context.getString(R.string.toast_torch_failed))
        }
    }

    /** Sets screen brightness. Writes the system value and re-applies it, which
     * is what makes the change visible without leaving the launcher. */
    fun applyBrightness(fraction: Float) {
        val clamped = fraction.coerceIn(0.02f, 1f)
        brightness = clamped
        val value = (clamped * 255).toInt()

        // Two writes, because they cover different devices.
        //
        // WRITE_SETTINGS is a special permission that is never granted from a
        // dialog, so on a fresh install the system write fails silently and the
        // slider would appear to do nothing. The window attribute needs no
        // permission at all and is what the user actually sees change, so it is
        // applied first and unconditionally.
        activity?.let { host ->
            runCatching {
                host.window.attributes = host.window.attributes.apply {
                    screenBrightness = clamped
                }
            }.onFailure { Log.w(TAG, "Window brightness failed", it) }
        }

        if (Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    value,
                )
            }.onFailure { Log.w(TAG, "Brightness write failed", it) }
        } else {
            brightnessNeedsPermission = true
        }
    }

    /**
     * Offers the system's write-settings screen, which is the only way
     * [applyBrightness] can change the brightness of the whole device rather
     * than just this window.
     */
    fun requestBrightnessPermission() {
        openPanel(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
    }

    /** Locks or frees rotation. A real system setting on every Android version. */
    fun toggleRotationLock() {
        val target = if (rotationLocked) 1 else 0
        runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, target)
            rotationLocked = !rotationLocked
        }.onFailure {
            Log.w(TAG, "Rotation write failed", it)
            // Writing Settings.System needs WRITE_SETTINGS from API 23; without
            // it the system panel is the only way, and saying so beats silence.
            if (!Settings.System.canWrite(context)) {
                openPanel(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            }
        }
    }

    /** Cycles the ringer between sound, vibrate and silent. */
    fun cycleRinger() {
        val next = when (ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        runCatching { audioManager?.ringerMode = next }
            .onFailure {
                Log.w(TAG, "Ringer change failed", it)
                // DND access is what gates this on modern Android.
                openPanel(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            }
    }

    private fun openPanel(action: String, data: Uri? = null) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
            if (data != null) this.data = data
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not open $action", it) }
    }
}

/** Creates the state and keeps its receiver alive exactly while it is composed. */
@Composable
fun rememberQuickControlsState(): QuickControlsState {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val state = remember(context) { QuickControlsState(context, activity) }
    DisposableEffect(state) {
        state.start()
        onDispose { state.stop() }
    }
    return state
}
