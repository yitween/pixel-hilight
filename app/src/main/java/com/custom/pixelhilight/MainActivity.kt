package com.custom.pixelhilight

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku
import kotlin.math.*

private val BgObsidian = Color(0xFF0B0D10)
private val CardSurface = Color(0xFF151921)
private val CardElevated = Color(0xFF1E2430)
private val BorderSubtle = Color(0xFF2A3241)

class MainActivity : ComponentActivity() {

    private var lightBridge by mutableStateOf<ILightBridge?>(null)

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.custom.pixelhilight", HiLightUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("hilight_service")
        .version(2)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            lightBridge = ILightBridge.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            lightBridge = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgObsidian,
                    surface = CardSurface,
                    primary = Color(0xFF64B5F6)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgObsidian) {
                    HiLightStudioApp(
                        isConnected = lightBridge != null,
                        onConnectShizuku = {
                            if (Shizuku.pingBinder()) {
                                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                                    Shizuku.bindUserService(userServiceArgs, serviceConnection)
                                } else {
                                    Shizuku.requestPermission(1001)
                                }
                            }
                        },
                        onTriggerHardware = { color, effect, brightness, speed, durationSec ->
                            lightBridge?.triggerEffect(
                                color,
                                effect.id,
                                brightness,
                                speed,
                                durationSec * 1000
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HiLightStudioApp(
    isConnected: Boolean,
    onConnectShizuku: () -> Unit,
    onTriggerHardware: (Int, LightEffect, Float, Float, Int) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hilight_prefs", Context.MODE_PRIVATE) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedApp by remember { mutableStateOf(SUPPORTED_APPS.first()) }

    // 全域參數狀態
    var brightness by remember { mutableFloatStateOf(prefs.getFloat("global_brightness", 0.85f)) }
    var speed by remember { mutableFloatStateOf(prefs.getFloat("global_speed", 1.0f)) }
    var durationSec by remember { mutableIntStateOf(prefs.getInt("duration_sec", 4)) }
    var onlyScreenOff by remember { mutableStateOf(prefs.getBoolean("only_screen_off", false)) }
    var respectDnd by remember { mutableStateOf(prefs.getBoolean("respect_dnd", true)) }
    var quietHours by remember { mutableStateOf(prefs.getBoolean("quiet_hours", false)) }

    // 當前選中 App 的專屬狀態
    var appEnabled by remember(selectedApp) {
        mutableStateOf(prefs.getBoolean("${selectedApp.pkg}_enabled", true))
    }
    var appColor by remember(selectedApp) {
        mutableIntStateOf(prefs.getInt("${selectedApp.pkg}_color", selectedApp.defaultColor))
    }
    var appEffect by remember(selectedApp) {
        mutableStateOf(
            LightEffect.fromId(prefs.getInt("${selectedApp.pkg}_effect", selectedApp.defaultEffect.id))
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        // 1. 頂部標題與硬體連線狀態列
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "HiLight Studio",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Pixel 11 Pro 機背 8-LED 燈圈控制器",
                        fontSize = 12.sp,
                        color = Color(0xFF8E9BAE)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isConnected) Color(0xFF0D2E1E) else Color(0xFF2E1418),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isConnected) Color(0xFF06C755) else Color(0xFFFF5252)
                    ),
                    modifier = Modifier.clickable { onConnectShizuku() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF06C755) else Color(0xFFFF5252))
                        )
                        Text(
                            text = if (isConnected) "燈圈已連線" else "授權硬體",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 2. 擬真 8-LED 燈圈即時預覽卡片
        item {
            LedRingPreviewCard(
                appName = selectedApp.displayName,
                colorInt = appColor,
                effect = appEffect,
                brightness = brightness,
                speed = speed,
                onTestClick = {
                    onTriggerHardware(appColor, appEffect, brightness, speed, durationSec)
                },
                onOpenNotifSettings = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            )
        }

        // 3. 分頁切換器
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("應用程式燈效", "智慧情境與參數").forEachIndexed { index, title ->
                    val active = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) CardElevated else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) Color.White else Color(0xFF8E9BAE)
                        )
                    }
                }
            }
        }

        if (selectedTab == 0) {
            // 4A. App 選擇橫向列
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SUPPORTED_APPS) { app ->
                        val isSelected = app.pkg == selectedApp.pkg
                        val itemColor = Color(prefs.getInt("${app.pkg}_color", app.defaultColor))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) CardElevated else CardSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isSelected) itemColor else BorderSubtle
                            ),
                            modifier = Modifier.clickable { selectedApp = app }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(itemColor)
                                )
                                Text(
                                    text = app.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // 4B. 該 App 的細緻燈效與 RGB 調色卡片
            item {
                AppFineTuningCard(
                    app = selectedApp,
                    enabled = appEnabled,
                    onToggleEnabled = {
                        appEnabled = it
                        prefs.edit().putBoolean("${selectedApp.pkg}_enabled", it).apply()
                    },
                    selectedEffect = appEffect,
                    onSelectEffect = {
                        appEffect = it
                        prefs.edit().putInt("${selectedApp.pkg}_effect", it.id).apply()
                        onTriggerHardware(appColor, it, brightness, speed, 2)
                    },
                    selectedColor = appColor,
                    onColorChange = { newColor ->
                        appColor = newColor
                        prefs.edit().putInt("${selectedApp.pkg}_color", newColor).apply()
                    },
                    onPreviewHardware = {
                        onTriggerHardware(appColor, appEffect, brightness, speed, 2)
                    }
                )
            }
        } else {
            // 5. 全域參數與智慧情境控制
            item {
                SmartControlsCard(
                    brightness = brightness,
                    onBrightnessChange = {
                        brightness = it
                        prefs.edit().putFloat("global_brightness", it).apply()
                    },
                    speed = speed,
                    onSpeedChange = {
                        speed = it
                        prefs.edit().putFloat("global_speed", it).apply()
                    },
                    durationSec = durationSec,
                    onDurationChange = {
                        durationSec = it
                        prefs.edit().putInt("duration_sec", it).apply()
                    },
                    onlyScreenOff = onlyScreenOff,
                    onOnlyScreenOffChange = {
                        onlyScreenOff = it
                        prefs.edit().putBoolean("only_screen_off", it).apply()
                    },
                    respectDnd = respectDnd,
                    onRespectDndChange = {
                        respectDnd = it
                        prefs.edit().putBoolean("respect_dnd", it).apply()
                    },
                    quietHours = quietHours,
                    onQuietHoursChange = {
                        quietHours = it
                        prefs.edit().putBoolean("quiet_hours", it).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun LedRingPreviewCard(
    appName: String,
    colorInt: Int,
    effect: LightEffect,
    brightness: Float,
    speed: Float,
    onTestClick: () -> Unit,
    onOpenNotifSettings: () -> Unit
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(effect, speed) {
        val start = System.currentTimeMillis()
        while (true) {
            elapsedMs = System.currentTimeMillis() - start
            delay(33L)
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "即時預覽：$appName · ${effect.label}",
                    fontSize = 13.sp,
                    color = Color(0xFFB0BCCF)
                )
                Text(
                    "亮度 ${(brightness * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF8E9BAE)
                )
            }

            // 8 顆 LED 燈圈畫布
            Box(
                modifier = Modifier
                    .size(148.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF090B0E))
                    .border(2.dp, Color(0xFF232936), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val ringRadius = size.minDimension * 0.34f
                    val count = 8

                    for (i in 0 until count) {
                        val angle = (i * (360.0 / count) - 90.0) * PI / 180.0
                        val ledPos = Offset(
                            x = center.x + (ringRadius * cos(angle)).toFloat(),
                            y = center.y + (ringRadius * sin(angle)).toFloat()
                        )

                        val (ledColor, alpha) = computePreviewLed(
                            index = i,
                            count = count,
                            baseColor = Color(colorInt),
                            effect = effect,
                            brightness = brightness,
                            speed = speed,
                            elapsedMs = elapsedMs
                        )

                        if (alpha > 0.02f) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        ledColor.copy(alpha = alpha * 0.65f),
                                        Color.Transparent
                                    ),
                                    center = ledPos,
                                    radius = 28.dp.toPx()
                                ),
                                radius = 28.dp.toPx(),
                                center = ledPos
                            )
                        }

                        drawCircle(
                            color = if (alpha > 0.05f) ledColor.copy(alpha = alpha) else Color(0xFF1F242E),
                            radius = 6.dp.toPx(),
                            center = ledPos
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTestClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("點亮實體燈圈", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = onOpenNotifSettings,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("通知權限", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun computePreviewLed(
    index: Int,
    count: Int,
    baseColor: Color,
    effect: LightEffect,
    brightness: Float,
    speed: Float,
    elapsedMs: Long
): Pair<Color, Float> {
    return when (effect) {
        LightEffect.SOLID -> baseColor to brightness
        LightEffect.BREATHE -> {
            val phase = (elapsedMs * speed * 0.0045) % (2 * PI)
            val factor = 0.15f + 0.85f * ((sin(phase).toFloat() + 1f) / 2f)
            baseColor to (brightness * factor)
        }
        LightEffect.BLINK -> {
            val cycleMs = (1100f / speed).toLong().coerceAtLeast(300L)
            val pos = (elapsedMs % cycleMs).toFloat() / cycleMs
            val on = (pos in 0.0f..0.14f) || (pos in 0.24f..0.38f)
            baseColor to (if (on) brightness else 0f)
        }
        LightEffect.CHASE -> {
            val head = ((elapsedMs * speed * 0.008f) % count)
            var dist = abs(index - head)
            if (dist > count / 2f) dist = count - dist
            val intensity = max(0f, 1f - (dist / 2.2f))
            baseColor to (brightness * intensity * intensity)
        }
        LightEffect.RAINBOW -> {
            val hue = ((elapsedMs * speed * 0.14f) + index * (360f / count)) % 360f
            val c = Color.hsv(hue, 0.95f, 1f)
            c to brightness
        }
    }
}

@Composable
fun AppFineTuningCard(
    app: AppPreset,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    selectedEffect: LightEffect,
    onSelectEffect: (LightEffect) -> Unit,
    selectedColor: Int,
    onColorChange: (Int) -> Unit,
    onPreviewHardware: () -> Unit
) {
    val swatches = listOf(
        0xFF06C755.toInt(), // LINE 綠
        0xFFE1306C.toInt(), // IG 桃紅
        0xFF00E5FF.toInt(), // 冰晶藍
        0xFFFFAB00.toInt(), // 琥珀黃
        0xFFAA00FF.toInt(), // 霓虹紫
        0xFFFF3D00.toInt(), // 烈焰橘
        0xFFFFFFFF.toInt()  // 純白光
    )

    val red = ((selectedColor shr 16) and 0xFF).toFloat()
    val green = ((selectedColor shr 8) and 0xFF).toFloat()
    val blue = (selectedColor and 0xFF).toFloat()
    val hexString = String.format("#%06X", 0xFFFFFF and selectedColor)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${app.displayName} 通知燈效", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(app.pkg, fontSize = 11.sp, color = Color(0xFF6F7D91))
                }
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }

            HorizontalDivider(color = BorderSubtle)

            // 燈效模式選擇
            Text("動態燈效模式", fontSize = 13.sp, color = Color(0xFFB0BCCF))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LightEffect.entries) { effect ->
                    FilterChip(
                        selected = selectedEffect == effect,
                        onClick = { onSelectEffect(effect) },
                        label = { Text(effect.label, fontSize = 12.sp) }
                    )
                }
            }

            // 精選色票 + Hex 色碼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("燈光色彩微調", fontSize = 13.sp, color = Color(0xFFB0BCCF))
                Text(
                    hexString,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(selectedColor)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                swatches.forEach { swatch ->
                    val active = swatch == selectedColor
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(swatch))
                            .border(
                                width = if (active) 2.5.dp else 1.dp,
                                color = if (active) Color.White else BorderSubtle,
                                shape = CircleShape
                            )
                            .clickable {
                                onColorChange(swatch)
                                onPreviewHardware()
                            }
                    )
                }
            }

            // RGB 三原色細部滑桿
            RgbSliderRow("R", red, Color(0xFFFF5252)) { newR ->
                onColorChange(Color(newR.toInt(), green.toInt(), blue.toInt()).toArgb())
            }
            RgbSliderRow("G", green, Color(0xFF06C755)) { newG ->
                onColorChange(Color(red.toInt(), newG.toInt(), blue.toInt()).toArgb())
            }
            RgbSliderRow("B", blue, Color(0xFF448AFF)) { newB ->
                onColorChange(Color(red.toInt(), green.toInt(), newB.toInt()).toArgb())
            }
        }
    }
}

@Composable
private fun RgbSliderRow(label: String, value: Float, tint: Color, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint, modifier = Modifier.width(16.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f).height(24.dp),
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint)
        )
        Text(
            value.toInt().toString().padStart(3, ' '),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF8E9BAE),
            modifier = Modifier.width(28.dp)
        )
    }
}

@Composable
fun SmartControlsCard(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    durationSec: Int,
    onDurationChange: (Int) -> Unit,
    onlyScreenOff: Boolean,
    onOnlyScreenOffChange: (Boolean) -> Unit,
    respectDnd: Boolean,
    onRespectDndChange: (Boolean) -> Unit,
    quietHours: Boolean,
    onQuietHoursChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("全域硬體與智慧情境", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

            // 亮度滑桿
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LED 輸出亮度", fontSize = 13.sp, color = Color(0xFFB0BCCF))
                    Text("${(brightness * 100).toInt()}%", fontSize = 12.sp, color = Color.White)
                }
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.1f..1.0f
                )
            }

            // 動態速度滑桿
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("動態燈效速度", fontSize = 13.sp, color = Color(0xFFB0BCCF))
                    Text(String.format("%.1fx", speed), fontSize = 12.sp, color = Color.White)
                }
                Slider(
                    value = speed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..2.5f
                )
            }

            // 持續秒數滑桿
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("單次通知持續時間", fontSize = 13.sp, color = Color(0xFFB0BCCF))
                    Text("$durationSec 秒", fontSize = 12.sp, color = Color.White)
                }
                Slider(
                    value = durationSec.toFloat(),
                    onValueChange = { onDurationChange(it.toInt()) },
                    valueRange = 1f..15f,
                    steps = 13
                )
            }

            HorizontalDivider(color = BorderSubtle)

            SmartSwitchRow(
                title = "僅在螢幕關閉時觸發",
                subtitle = "正在操作手機時不亮背部燈圈，節省電力",
                checked = onlyScreenOff,
                onCheckedChange = onOnlyScreenOffChange
            )
            SmartSwitchRow(
                title = "跟隨系統「勿擾模式」",
                subtitle = "開啟勿擾期間自動停用通知燈效",
                checked = respectDnd,
                onCheckedChange = onRespectDndChange
            )
            SmartSwitchRow(
                title = "夜間休眠時段 (23:00 - 07:00)",
                subtitle = "深夜睡眠時間避免背部強光干擾",
                checked = quietHours,
                onCheckedChange = onQuietHoursChange
            )
        }
    }
}

@Composable
private fun SmartSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF8E9BAE))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
