package com.custom.pixelhilight

import android.content.Context
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Handler
import android.os.Looper
import kotlin.system.exitProcess

class HiLightUserService(private val context: Context) : ILightBridge.Stub() {

    private var session: LightsManager.LightsSession? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var offRunnable: Runnable? = null

    private fun getLightsManager(): LightsManager? {
        return context.getSystemService(LightsManager::class.java)
    }

    override fun triggerHiLight(argbColor: Int, durationMs: Int) {
        val manager = getLightsManager() ?: return
        val rgbLights = manager.lights.filter { light ->
            light.hasRgbControl() || light.type == Light.LIGHT_TYPE_MICROPHONE
        }
        if (rgbLights.isEmpty()) return

        // 取消前一次尚未結束的熄燈排程
        offRunnable?.let { mainHandler.removeCallbacks(it) }

        if (session == null) {
            session = manager.openSession()
        }

        // 建立點亮請求，設定所有 8 顆 HiLight LED 的顏色
        val requestBuilder = LightsRequest.Builder()
        val lightState = LightState.Builder()
            .setColor(argbColor)
            .build()

        for (light in rgbLights) {
            requestBuilder.addLight(light, lightState)
        }
        session?.requestLights(requestBuilder.build())

        // 安全保護：強制在指定時間（最長限制 30 秒）後自動關閉 LED
        val safeDuration = durationMs.coerceIn(500, 30_000).toLong()
        offRunnable = Runnable { turnOff() }
        mainHandler.postDelayed(offRunnable!!, safeDuration)
    }

    override fun turnOff() {
        try {
            session?.close()
        } catch (_: Exception) {
        } finally {
            session = null
        }
    }

    override fun destroy() {
        turnOff()
        exitProcess(0)
    }
}
