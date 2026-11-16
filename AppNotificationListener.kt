package com.custom.pixelhilight

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import rikka.shizuku.Shizuku

class AppNotificationListener : NotificationListenerService() {

    companion object {
        const val PKG_LINE = "jp.naver.line.android"
        const val PKG_INSTAGRAM = "com.instagram.android"
    }

    private var lightBridge: ILightBridge? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.custom.pixelhilight", HiLightUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("hilight_service")
        .debuggable(false)
        .version(1)

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
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (lightBridge == null) {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        // 忽略群組摘要通知，避免單則訊息重複閃爍兩次
        if (sbn.isGroup && (sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        val prefs = getSharedPreferences("hilight_prefs", Context.MODE_PRIVATE)
        val durationMs = prefs.getInt("duration_sec", 4) * 1000

        when (pkg) {
            PKG_LINE -> {
                if (prefs.getBoolean("line_enabled", true)) {
                    val color = prefs.getInt("line_color", 0xFF06C755.toInt()) // LINE 綠色
                    triggerLed(color, durationMs)
                }
            }
            PKG_INSTAGRAM -> {
                if (prefs.getBoolean("ig_enabled", true)) {
                    val color = prefs.getInt("ig_color", 0xFFE1306C.toInt()) // IG 桃紫色
                    triggerLed(color, durationMs)
                }
            }
        }
    }

    private fun triggerLed(color: Int, durationMs: Int) {
        bindShizukuServiceIfReady()
        lightBridge?.triggerHiLight(color, durationMs)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
        } catch (_: Exception) {
        }
    }
}
