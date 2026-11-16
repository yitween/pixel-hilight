package com.custom.pixelhilight

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import rikka.shizuku.Shizuku
import java.util.Calendar

class AppNotificationListener : NotificationListenerService() {

    private var lightBridge: ILightBridge? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.custom.pixelhilight", HiLightUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("hilight_service")
        .debuggable(false)
        .version(2)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            lightBridge = ILightBridge.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            lightBridge = null
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        bindShizukuServiceIfReady()
    }

    private fun bindShizukuServiceIfReady() {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            if (lightBridge == null) {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (sbn.isGroup && (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        val appPreset = SUPPORTED_APPS.find { it.pkg == pkg } ?: return
        val prefs = getSharedPreferences("hilight_prefs", Context.MODE_PRIVATE)

        // 1. 檢查該 App 是否啟用燈效
        if (!prefs.getBoolean("${pkg}_enabled", true)) return

        // 2. 智慧情境檢查：僅在螢幕關閉時亮燈
        if (prefs.getBoolean("only_screen_off", false)) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) return
        }

        // 3. 智慧情境檢查：跟隨系統勿擾模式
        if (prefs.getBoolean("respect_dnd", true)) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
        }

        // 4. 智慧情境檢查：夜間休眠時段 (23:00 - 07:00)
        if (prefs.getBoolean("quiet_hours", false)) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour >= 23 || hour < 7) return
        }

        val color = prefs.getInt("${pkg}_color", appPreset.defaultColor)
        val effectId = prefs.getInt("${pkg}_effect", appPreset.defaultEffect.id)
        val brightness = prefs.getFloat("global_brightness", 0.85f)
        val speed = prefs.getFloat("global_speed", 1.0f)
        val durationMs = prefs.getInt("duration_sec", 4) * 1000

        bindShizukuServiceIfReady()
        lightBridge?.triggerEffect(color, effectId, brightness, speed, durationMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (_: Exception) {
        }
    }
}
