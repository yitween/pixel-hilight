package com.custom.pixelhilight

import android.content.Context
import android.graphics.Color
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.system.exitProcess

class HiLightUserService(private val context: Context) : ILightBridge.Stub() {

    private var session: LightsManager.LightsSession? = null
    private val animThread = HandlerThread("HiLightAnimThread").apply { start() }
    private val animHandler = Handler(animThread.looper)
    private var activeRunnable: Runnable? = null

    private fun getLightsManager(): LightsManager? {
        return context.getSystemService(LightsManager::class.java)
    }

    override fun triggerEffect(
        argbColor: Int,
        effectId: Int,
        brightness: Float,
        speed: Float,
        durationMs: Int
    ) {
        val manager = getLightsManager() ?: return
        val rgbLights = manager.lights.filter { light ->
            light.hasRgbControl() || light.type == 8
        }.ifEmpty {
            manager.lights.filter {
                it.type != Light.LIGHT_TYPE_INPUT && it.type != Light.LIGHT_TYPE_KEYBOARD_BACKLIGHT
            }
        }
        if (rgbLights.isEmpty()) return

        stopAnimationLoop()

        if (session == null) {
            session = manager.openSession()
        }

        val safeDuration = durationMs.coerceIn(500, 30_000).toLong()
        val safeBrightness = brightness.coerceIn(0.05f, 1.0f)
        val safeSpeed = speed.coerceIn(0.3f, 3.0f)
        val startTime = SystemClock.elapsedRealtime()

        val runnable = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                if (elapsed >= safeDuration) {
                    turnOff()
                    return
                }

                renderFrame(rgbLights, argbColor, effectId, safeBrightness, safeSpeed, elapsed)

                if (effectId == LightEffect.SOLID.id) {
                    animHandler.postDelayed(this, safeDuration - elapsed)
                } else {
                    animHandler.postDelayed(this, 33L)
                }
            }
        }

        activeRunnable = runnable
        animHandler.post(runnable)
    }

    private fun renderFrame(
        lights: List<Light>,
        baseColor: Int,
        effectId: Int,
        brightness: Float,
        speed: Float,
        elapsedMs: Long
    ) {
        val requestBuilder = LightsRequest.Builder()
        val count = lights.size

        for (i in lights.indices) {
            val ledColor = when (effectId) {
                LightEffect.SOLID.id -> scaleColor(baseColor, brightness)

                LightEffect.BREATHE.id -> {
                    val phase = (elapsedMs * speed * 0.0045) % (2 * PI)
                    val factor = (0.15f + 0.85f * ((sin(phase).toFloat() + 1f) / 2f))
                    scaleColor(baseColor, brightness * factor)
                }

                LightEffect.BLINK.id -> {
                    val cycleMs = (1100f / speed).toLong().coerceAtLeast(300L)
                    val pos = (elapsedMs % cycleMs).toFloat() / cycleMs
                    val on = (pos in 0.0f..0.14f) || (pos in 0.24f..0.38f)
                    scaleColor(baseColor, if (on) brightness else 0f)
                }

                LightEffect.CHASE.id -> {
                    val head = ((elapsedMs * speed * 0.008f) % count)
                    var dist = abs(i - head)
                    if (dist > count / 2f) dist = count - dist
                    val intensity = max(0f, 1f - (dist / 2.2f))
                    scaleColor(baseColor, brightness * intensity * intensity)
                }

                LightEffect.RAINBOW.id -> {
                    val hue = ((elapsedMs * speed * 0.14f) + i * (360f / count)) % 360f
                    val hsvColor = Color.HSVToColor(floatArrayOf(hue, 0.95f, 1f))
                    scaleColor(hsvColor, brightness)
                }

                else -> scaleColor(baseColor, brightness)
            }

            requestBuilder.addLight(lights[i], LightState.Builder().setColor(ledColor).build())
        }

        try {
            session?.requestLights(requestBuilder.build())
        } catch (_: Exception) {
        }
    }

    private fun scaleColor(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        val r = (Color.red(color) * f).toInt()
        val g = (Color.green(color) * f).toInt()
        val b = (Color.blue(color) * f).toInt()
        return Color.argb(255, r, g, b)
    }

    private fun stopAnimationLoop() {
        activeRunnable?.let { animHandler.removeCallbacks(it) }
        activeRunnable = null
    }

    override fun turnOff() {
        stopAnimationLoop()
        try {
            session?.close()
        } catch (_: Exception) {
        } finally {
            session = null
        }
    }

    override fun destroy() {
        turnOff()
        animThread.quitSafely()
        exitProcess(0)
    }
}
