package com.custom.pixelhilight

enum class LightEffect(val id: Int, val label: String) {
    SOLID(0, "恆亮"),
    BREATHE(1, "呼吸燈"),
    BLINK(2, "雙閃提醒"),
    CHASE(3, "8珠流光"),
    RAINBOW(4, "霓虹彩虹");

    companion object {
        fun fromId(id: Int): LightEffect = entries.find { it.id == id } ?: BREATHE
    }
}

data class AppPreset(
    val pkg: String,
    val displayName: String,
    val defaultColor: Int,
    val defaultEffect: LightEffect
)

val SUPPORTED_APPS = listOf(
    AppPreset("jp.naver.line.android", "LINE", 0xFF06C755.toInt(), LightEffect.BREATHE),
    AppPreset("com.instagram.android", "Instagram", 0xFFE1306C.toInt(), LightEffect.CHASE),
    AppPreset("com.instagram.barcelona", "Threads", 0xFFFFFFFF.toInt(), LightEffect.BLINK),
    AppPreset("com.facebook.orca", "Messenger", 0xFF00B2FF.toInt(), LightEffect.BREATHE),
    AppPreset("com.discord", "Discord", 0xFF5865F2.toInt(), LightEffect.CHASE),
    AppPreset("org.telegram.messenger", "Telegram", 0xFF26A5E4.toInt(), LightEffect.SOLID)
)
