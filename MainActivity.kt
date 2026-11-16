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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var lightBridge by mutableStateOf<ILightBridge?>(null)

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.custom.pixelhilight", HiLightUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("hilight_service")
        .version(1)

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
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HiLightControlScreen(
                        onRequestShizuku = {
                            if (Shizuku.pingBinder()) {
                                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                                    Shizuku.bindUserService(userServiceArgs, serviceConnection)
                                } else {
                                    Shizuku.requestPermission(1001)
                                }
                            }
                        },
                        onTestLight = { colorInt, durationSec ->
                            lightBridge?.triggerHiLight(colorInt, durationSec * 1000)
                        },
                        isConnected = lightBridge != null
                    )
                }
            }
        }
    }
}

@Composable
fun HiLightControlScreen(
    onRequestShizuku: () -> Unit,
    onTestLight: (Int, Int) -> Unit,
    isConnected: Boolean
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hilight_prefs", Context.MODE_PRIVATE) }

    var lineEnabled by remember { mutableStateOf(prefs.getBoolean("line_enabled", true)) }
    var lineColor by remember { mutableIntStateOf(prefs.getInt("line_color", 0xFF06C755.toInt())) }
    var igEnabled by remember { mutableStateOf(prefs.getBoolean("ig_enabled", true)) }
    var igColor by remember { mutableIntStateOf(prefs.getInt("ig_color", 0xFFE1306C.toInt())) }
    var durationSec by remember { mutableFloatStateOf(prefs.getInt("duration_sec", 4).toFloat()) }

    val presetColors = listOf(
        0xFF06C755.toInt(), // LINE 綠
        0xFFE1306C.toInt(), // IG 桃紅
        0xFF00E5FF.toInt(), // 冰晶藍
        0xFFFFAB00.toInt(), // 琥珀黃
        0xFFAA00FF.toInt(), // 霓虹紫
        0xFFFFFFFF.toInt()  // 純白光
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pixel 11 Pro HiLight 通知燈", style = MaterialTheme.typography.headlineSmall)

        // 1. 權限設定卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isConnected) "● 硬體燈圈已連線 (8 LEDs Ready)" else "○ 尚未連線至硬體燈圈",
                    color = if (isConnected) Color(0xFF06C755) else Color(0xFFFF5252)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestShizuku) {
                        Text("1. 授權 Shizuku")
                    }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Text("2. 開啟通知讀取")
                    }
                }
            }
        }

        // 2. LINE 通知燈設定
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LINE 通知發光", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = lineEnabled, onCheckedChange = {
                        lineEnabled = it
                        prefs.edit().putBoolean("line_enabled", it).apply()
                    })
                }
                ColorPickerRow(presetColors, lineColor) { selected ->
                    lineColor = selected
                    prefs.edit().putInt("line_color", selected).apply()
                    onTestLight(selected, 2)
                }
            }
        }

        // 3. Instagram 通知燈設定
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Instagram 通知發光", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = igEnabled, onCheckedChange = {
                        igEnabled = it
                        prefs.edit().putBoolean("ig_enabled", it).apply()
                    })
                }
                ColorPickerRow(presetColors, igColor) { selected ->
                    igColor = selected
                    prefs.edit().putInt("ig_color", selected).apply()
                    onTestLight(selected, 2)
                }
            }
        }

        // 4. 發光持續時間與即時測試
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("通知亮燈秒數：${durationSec.toInt()} 秒")
                Slider(
                    value = durationSec,
                    onValueChange = {
                        durationSec = it
                        prefs.edit().putInt("duration_sec", it.toInt()).apply()
                    },
                    valueRange = 1f..15f,
                    steps = 13
                )
                Button(
                    onClick = { onTestLight(igColor, durationSec.toInt()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("立即測試點亮手機背部 HiLight")
                }
            }
        }
    }
}

@Composable
fun ColorPickerRow(colors: List<Int>, selectedColor: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { colorInt ->
            val isSelected = colorInt == selectedColor
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(colorInt))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onSelect(colorInt) }
            )
        }
    }
}
