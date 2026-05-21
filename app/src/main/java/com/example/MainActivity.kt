package com.example

import android.Manifest
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.camera.CameraPreview
import com.example.camera.MockSceneGenerator
import com.example.speech.SpeechHelper
import com.example.viewmodel.AtlasViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F141C))
                ) { innerPadding ->
                    AtlasMainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Colors representing a high-tech Glass HUD
val HudDarkBg = Color(0xFF090D14)
val HudCardBg = Color(0xCC121822)
val HudBorderCyan = Color(0x3300E5FF)
val HudNeonCyan = Color(0xFF00E5FF)
val HudNeonGreen = Color(0xFF00FF9D)
val HudNeonRed = Color(0xFFFF3366)
val HudTextGrey = Color(0xFF94A3B8)
val HudTextWhite = Color(0xFFF1F5F9)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AtlasMainScreen(
    modifier: Modifier = Modifier,
    viewModel: AtlasViewModel = viewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    val micPermissionState = rememberPermissionState(permission = Manifest.permission.RECORD_AUDIO)

    // Reference to CameraX PreviewView to capture actual live frame
    var activePreviewView: PreviewView? by remember { mutableStateOf(null) }

    // Speech-to-text recording state
    var isRecordingSpeech by remember { mutableStateOf(false) }
    var speechRecognizerErr by remember { mutableStateOf<String?>(null) }
    var partialSpeechText by remember { mutableStateOf("") }

    // Speech helper initialization
    val speechHelper = remember {
        SpeechHelper(
            context = context,
            onResult = { finalResult ->
                isRecordingSpeech = false
                viewModel.queryText = finalResult
                // Automatically send speech prompt
                val liveBitmap = if (viewModel.selectedScene == "live_camera") activePreviewView?.bitmap else null
                viewModel.processQuery(finalResult, liveBitmap)
                partialSpeechText = ""
            },
            onError = { errMsg ->
                isRecordingSpeech = false
                speechRecognizerErr = errMsg
                Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                partialSpeechText = ""
            },
            onPartialResult = { partial ->
                partialSpeechText = partial
            }
        )
    }

    // Reticle pulsation animation
    val infiniteTransition = rememberInfiniteTransition(label = "hud_pulse")
    val reticleScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val compassRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "compass_rot"
    )

    // Listen to ViewModel state
    val historyItems by viewModel.history.collectAsState()
    val listState = rememberLazyListState()

    // Trigger scroll downward on new history
    LaunchedEffect(historyItems.size) {
        if (historyItems.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Auto-stop speech recognition on dispose
    DisposableEffect(Unit) {
        onDispose {
            speechHelper.destroy()
        }
    }

    Column(
        modifier = modifier
            .background(HudDarkBg)
            .fillMaxSize()
    ) {
        // ---------------- HUD HEADER PANEL ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Bleeding LED status indicator
                    val ledAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "led_pulse"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(HudNeonGreen.copy(alpha = ledAlpha))
                            .border(1.dp, HudNeonGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ATLAS GLASSES CORE",
                        color = HudTextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "neural telemetry live stream",
                    color = HudNeonCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            // Connection Diagnostic States
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // API Secret status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (viewModel.isApiKeyConfigured) Color(0x2200FF9D) else Color(0x22FF3366))
                        .border(
                            1.dp,
                            if (viewModel.isApiKeyConfigured) HudNeonGreen else HudNeonRed,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .clickable { viewModel.showDiagnosticDialog = true }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (viewModel.isApiKeyConfigured) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = "Status Key",
                            tint = if (viewModel.isApiKeyConfigured) HudNeonGreen else HudNeonRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (viewModel.isApiKeyConfigured) "NEURAL ONLINE" else "KEY ABSENT",
                            color = if (viewModel.isApiKeyConfigured) HudNeonGreen else HudNeonRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.showDiagnosticDialog = true },
                    modifier = Modifier.size(28.dp).testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "API diagnostics info",
                        tint = HudNeonCyan
                    )
                }
            }
        }

        Divider(color = HudBorderCyan, thickness = 1.dp)

        // ---------------- SMART GLASSES VIEWPORT (16:9 VIEW) ----------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .background(Color.Black)
                .border(1.dp, HudBorderCyan)
        ) {
            if (viewModel.selectedScene == "live_camera") {
                // If live camera is requested, ask and show CameraX Feed
                if (cameraPermissionState.status.isGranted) {
                    CameraPreview(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_preview_view")
                    ) { previewView ->
                        activePreviewView = previewView
                    }
                } else {
                    // Ask permission state layout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF131822))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Camera Permission Needed",
                            tint = HudNeonCyan,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ATLAS GLASSES VIEWPORT",
                            color = HudTextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Camera feed permission is needed to stream real-time smart glasses frames.",
                            color = HudTextGrey,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = HudNeonCyan),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("grant_camera_button")
                        ) {
                            Text(
                                "GRANT CAMERA FEED",
                                color = HudDarkBg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // Synthesize/Draw Vector mock scene for smart glasses frame
                val synthesizedBitmap = remember(viewModel.selectedScene) {
                    MockSceneGenerator.generateSceneBitmap(viewModel.selectedScene)
                }
                Image(
                    bitmap = synthesizedBitmap.asImageBitmap(),
                    contentDescription = "Glasses Simulated Viewport Scene",
                    modifier = Modifier.fillMaxSize().testTag("synthesized_scene_image"),
                    contentScale = ContentScale.Crop
                )
            }

            // ---------------- HUD IMPERSIVE FOREGROUND GLASS OVERLAYS ----------------
            // Dynamic Grid Lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridStroke = 0.5f
                val gridColor = HudNeonCyan.copy(alpha = 0.15f)
                val width = size.width
                val height = size.height
                
                // Vertical grid bars
                for (x in 1..4) {
                    val xPos = width * (x / 5f)
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(xPos, 0f), end = androidx.compose.ui.geometry.Offset(xPos, height), strokeWidth = gridStroke)
                }
                // Horizontal grid bars
                for (y in 1..4) {
                    val yPos = height * (y / 5f)
                    drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, yPos), end = androidx.compose.ui.geometry.Offset(width, yPos), strokeWidth = gridStroke)
                }

                // Focal points corner brackets
                val bracketLen = 30f
                val strokeW = 3f
                val bracketColor = HudNeonCyan.copy(alpha = 0.6f)
                val padding = 20f

                // Top-left bracket
                drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(padding, padding), end = androidx.compose.ui.geometry.Offset(padding + bracketLen, padding), strokeWidth = strokeW)
                drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(padding, padding), end = androidx.compose.ui.geometry.Offset(padding, padding + bracketLen), strokeWidth = strokeW)

                // Top-right
                drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(width - padding, padding), end = androidx.compose.ui.geometry.Offset(width - padding - bracketLen, padding), strokeWidth = strokeW)
                drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(width - padding, padding), end = androidx.compose.ui.geometry.Offset(width - padding, padding + bracketLen), strokeWidth = strokeW)

                // Bottom-left
                drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(padding, height - padding), end = androidx.compose.ui.geometry.Offset(padding + bracketLen, height - padding), strokeWidth = strokeW)
                drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(padding, height - padding), end = androidx.compose.ui.geometry.Offset(padding, height - padding - bracketLen), strokeWidth = strokeW)

                // Bottom-right
                drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(width - padding, height - padding), end = androidx.compose.ui.geometry.Offset(width - padding - bracketLen, height - padding), strokeWidth = strokeW)
                drawLine(bracketColor, start = androidx.compose.ui.geometry.Offset(width - padding, height - padding), end = androidx.compose.ui.geometry.Offset(width - padding, height - padding - bracketLen), strokeWidth = strokeW)
            }

            // Scanning breathing target box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                // Spinning Dial Compass Outer Ring
                Box(
                    modifier = Modifier
                        .size(140.dp * reticleScale)
                        .border(
                            1.dp,
                            Brush.sweepGradient(
                                listOf(
                                    HudNeonCyan.copy(alpha = 0.5f),
                                    Color.Transparent,
                                    HudNeonCyan.copy(alpha = 0.1f),
                                    HudNeonCyan.copy(alpha = 0.6f)
                                )
                            ),
                            CircleShape
                        )
                        .scale(reticleScale)
                        .rotate(compassRotation)
                )

                // Core Reticle Crosshair
                Canvas(modifier = Modifier.size(50.dp)) {
                    val reticleColor = HudNeonCyan.copy(alpha = 0.7f)
                    val w = size.width
                    val h = size.height

                    // Dynamic Cross Lines
                    drawLine(reticleColor, start = androidx.compose.ui.geometry.Offset(w / 2f, 0f), end = androidx.compose.ui.geometry.Offset(w / 2f, 12f), strokeWidth = 2f)
                    drawLine(reticleColor, start = androidx.compose.ui.geometry.Offset(w / 2f, h), end = androidx.compose.ui.geometry.Offset(w / 2f, h - 12f), strokeWidth = 2f)
                    drawLine(reticleColor, start = androidx.compose.ui.geometry.Offset(0f, h / 2f), end = androidx.compose.ui.geometry.Offset(12f, h / 2f), strokeWidth = 2f)
                    drawLine(reticleColor, start = androidx.compose.ui.geometry.Offset(w, h / 2f), end = androidx.compose.ui.geometry.Offset(w - 12f, h / 2f), strokeWidth = 2f)

                    // Target circle center
                    drawCircle(reticleColor, radius = 4f)
                }
            }

            // Real-Time HUD Diagnostic Diagnostics
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(6.dp)
            ) {
                Text(
                    text = "CAM_FPS: 30 / RESOLUTION: HD_720P",
                    color = HudNeonCyan,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "GPS: 31°37'N 7°58'W SOUK REGION",
                    color = HudTextGrey,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Location Pin",
                        tint = HudNeonRed,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = when (viewModel.selectedScene) {
                            "argan_press" -> "MARRAKECH SOUK"
                            "cafe_menu" -> "MEDINA SOUK CAFE"
                            "price_sign" -> "BERBER ARTISANS"
                            "melon_stand" -> "FRUIT VENDOR STAND"
                            else -> "LIVE GPS FRAME"
                        },
                        color = HudTextWhite,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Divider(color = HudBorderCyan, thickness = 1.dp)

        // ---------------- CONTROL DECK (VIEWPORTS SELECTION) ----------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Remaining weight for scroll area
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                text = "SWAP VIEWPORT SIMULATION SCENARIO:",
                color = HudTextWhite,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Carousel list of scenes
            val scenes = listOf(
                "argan_press" to "Argan Press 🏺",
                "cafe_menu" to "Chalk Menu 📜",
                "price_sign" to "Rug Crafts 🏷️",
                "melon_stand" to "Ripe Melon 🍈",
                "live_camera" to "Live Camera 📷"
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("scene_carousel_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scenes) { (key, label) ->
                    val isSelected = viewModel.selectedScene == key
                    val chipBorders = if (isSelected) HudNeonCyan else HudBorderCyan
                    val chipBackgrounds = if (isSelected) Color(0x3300E5FF) else HudCardBg

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBackgrounds)
                            .border(1.dp, chipBorders, RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.selectedScene = key
                                viewModel.stopSpeaking()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("scene_chip_$key")
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) HudNeonCyan else HudTextWhite,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---------------- SPEECH COGNITIVE PROMPT SUBTITLE ----------------
            if (viewModel.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = HudNeonCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ATLAS GLASSES ARE SOLVING FRAME TELEMETRY...",
                        color = HudNeonCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Subtitle spoken prompt text
            AnimatedVisibility(
                visible = viewModel.responseText.isNotEmpty() || viewModel.isLoading,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HudCardBg)
                        .border(1.dp, HudBorderCyan, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Atlas speaking icon",
                                    tint = HudNeonCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "ATLAS COMPANION FEEDBACK",
                                    color = HudTextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (viewModel.responseText.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.speakResponse(viewModel.responseText) },
                                        modifier = Modifier.size(26.dp).testTag("replay_speech_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Replay Speech",
                                            tint = HudNeonGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                if (viewModel.isTtsActive) {
                                    IconButton(
                                        onClick = { viewModel.stopSpeaking() },
                                        modifier = Modifier.size(26.dp).testTag("stop_speech_button")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(HudNeonRed, RoundedCornerShape(1.dp))
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (viewModel.isLoading) "Processing video frame coordinates..." else viewModel.responseText,
                            color = HudTextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Speech rate tuning slider to feel friendly
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SPEAK RATE: ${String.format("%.2f", viewModel.ttsRate)}x",
                                color = HudTextGrey,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = viewModel.ttsRate,
                                onValueChange = { viewModel.ttsRate = it },
                                valueRange = 0.75f..1.5f,
                                modifier = Modifier.weight(1f).height(12.dp).testTag("tts_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = HudNeonCyan,
                                    activeTrackColor = HudNeonCyan,
                                    inactiveTrackColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ---------------- REAL-WORLD QUERIES SYSTEM LOG ----------------
            Text(
                text = "CLICK QUICK SPATIAL ASK PRESET:",
                color = HudTextGrey,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Lazy row list of trigger triggers
            val currentPresets = viewModel.getPresetsForCurrentScene()
            LazyRow(
                modifier = Modifier.fillMaxWidth().testTag("presets_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentPresets) { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1B2330))
                            .border(1.dp, HudBorderCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .clickable {
                                viewModel.queryText = preset
                                val liveBitmap = if (viewModel.selectedScene == "live_camera") activePreviewView?.bitmap else null
                                viewModel.processQuery(preset, liveBitmap)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("preset_chip_${preset.take(15)}")
                    ) {
                        Text(
                            text = preset,
                            color = HudTextWhite,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // History Log Lists
            Text(
                text = "LOG RUNTIME GRAPH:",
                color = HudTextGrey,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (historyItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0C1017), RoundedCornerShape(6.dp))
                        .border(1.dp, HudBorderCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "History feed clear. Ask Atlas coordinates visually or trigger presets to store logs.",
                        color = HudTextGrey,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0C1017), RoundedCornerShape(6.dp))
                        .border(1.dp, HudBorderCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyItems) { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xBB182232))
                                .border(1.dp, HudBorderCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Q: ${item.query}",
                                        color = HudNeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = item.sceneKey.uppercase().replace("_", " "),
                                        color = HudTextGrey,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.response,
                                    color = HudTextWhite,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // ---------------- BARGE-IN MICROPHONE & DIALOG CONTROL DECK ----------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0C1017))
                .border(1.dp, HudBorderCyan)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Manual typing prompt textfield
                    TextField(
                        value = viewModel.queryText,
                        onValueChange = { viewModel.queryText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("query_text_field"),
                        placeholder = {
                            Text(
                                "Ask Atlas about view...",
                                color = HudTextGrey,
                                fontSize = 13.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF131A26),
                            unfocusedContainerColor = Color(0xFF131A26),
                            focusedTextColor = HudTextWhite,
                            unfocusedTextColor = HudTextWhite,
                            cursorColor = HudNeonCyan,
                            focusedIndicatorColor = HudNeonCyan,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Pulsating microphone gesture button
                    val recordingPulse by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "recording_ring"
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.testTag("microphone_button_container")
                    ) {
                        if (isRecordingSpeech) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp * recordingPulse)
                                    .clip(CircleShape)
                                    .background(HudNeonRed.copy(alpha = 0.3f))
                            )
                        }

                        IconButton(
                            onClick = {
                                if (isRecordingSpeech) {
                                    speechHelper.stopListening()
                                    isRecordingSpeech = false
                                } else {
                                    if (micPermissionState.status.isGranted) {
                                        speechHelper.startListening()
                                        isRecordingSpeech = true
                                        speechRecognizerErr = null
                                    } else {
                                        micPermissionState.launchPermissionRequest()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isRecordingSpeech) HudNeonRed else HudNeonCyan)
                                .testTag("mic_trigger_btn")
                        ) {
                            if (isRecordingSpeech) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(HudDarkBg, RoundedCornerShape(1.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val strokeW = 2.dp.toPx()
                                        val color = HudDarkBg
                                        // Draw mic capsule
                                        drawRoundRect(
                                            color = color,
                                            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - 3.5.dp.toPx(), 4.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(7.dp.toPx(), 11.dp.toPx()),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
                                        )
                                        // Draw stand loop
                                        drawArc(
                                            color = color,
                                            startAngle = 0f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2f - 6.5.dp.toPx(), 8.5.dp.toPx()),
                                            size = androidx.compose.ui.geometry.Size(13.dp.toPx(), 9.dp.toPx()),
                                            style = Stroke(width = strokeW)
                                        )
                                        // Draw stand base stem
                                        drawLine(
                                            color = color,
                                            start = androidx.compose.ui.geometry.Offset(size.width / 2f, 17.5.dp.toPx()),
                                            end = androidx.compose.ui.geometry.Offset(size.width / 2f, 20.5.dp.toPx()),
                                            strokeWidth = strokeW
                                        )
                                        drawLine(
                                            color = color,
                                            start = androidx.compose.ui.geometry.Offset(size.width / 2f - 3.dp.toPx(), 20.5.dp.toPx()),
                                            end = androidx.compose.ui.geometry.Offset(size.width / 2f + 3.dp.toPx(), 20.5.dp.toPx()),
                                            strokeWidth = strokeW
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Keyboard sending button
                    IconButton(
                        onClick = {
                            if (viewModel.queryText.isNotEmpty()) {
                                keyboardController?.hide()
                                val liveBitmap = if (viewModel.selectedScene == "live_camera") activePreviewView?.bitmap else null
                                viewModel.processQuery(viewModel.queryText, liveBitmap)
                                viewModel.queryText = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1F2937))
                            .testTag("keyboard_send_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Submit written query",
                            tint = HudNeonCyan
                        )
                    }
                }

                // If recording speech, show live waveform simulation subtitled preview
                if (isRecordingSpeech) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Atlas micro feed listening: ",
                            color = HudNeonRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (partialSpeechText.isNotEmpty()) "\"$partialSpeechText\"" else "Speak clearly into smart glasses... (tap RED button to finish)",
                            color = HudTextWhite,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }

    // ---------------- SYSTEM TELEMETRY DIAGNOSTICS CONTROL DIALOG ----------------
    if (viewModel.showDiagnosticDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDiagnosticDialog = false },
            title = {
                Text(
                    text = "ATLAS GLASSES COGNITIVE PORT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This screen validates Atlas's connection status, spatial awareness telemetry parameters, and credentials.",
                        fontSize = 12.sp,
                        color = HudTextWhite
                    )

                    Divider(color = HudBorderCyan)

                    Text(
                        text = "1. AI STUDIO SYSTEM CREDENTIAL:",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HudNeonCyan
                    )

                    if (viewModel.isApiKeyConfigured) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Active Key OK",
                                tint = HudNeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GEMINI_API_KEY detected & verified.",
                                fontSize = 11.sp,
                                color = HudNeonGreen
                            )
                        }
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Missing Secret Key",
                                    tint = HudNeonRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "GEMINI_API_KEY IS ABSENT OR DEFAULT PIN.",
                                    fontSize = 11.sp,
                                    color = HudNeonRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please enter your valid GEMINI_API_KEY securely in the API Key Secrets panel of the Google AI Studio console to engage Atlas with the live Gemini LLM network.",
                                fontSize = 10.sp,
                                color = HudTextGrey
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "2. REAL-TIME TTS AUDIO ENGINE:",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HudNeonCyan
                    )
                    Text(
                        text = "Android Text-to-Speech Engine allows Atlas to read context feedback like high-yield smart glasses companion audio. Use the Slider to alter reading velocity.",
                        fontSize = 10.sp,
                        color = HudTextGrey
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "3. CONVERSATIONAL LOG CLEARER:",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = HudNeonCyan
                    )
                    Button(
                        onClick = {
                            viewModel.clearLog()
                            Toast.makeText(context, "Atlas conversation log wiped.", Toast.LENGTH_SHORT).show()
                            viewModel.showDiagnosticDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HudNeonRed),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().testTag("clear_history_btn")
                    ) {
                        Text(
                            "WIPE CONVERSATION SYSTEM MEMORY",
                            color = HudTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }

                    // Strict APK Decompiler extraction warning (MANDATORY per secret mgmt skill)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Security Warning: I have included your API keys in the generated APK file for this prototype. Please be aware that Android APKs can be easily decompiled, and these keys can be extracted by anyone who has access to the file. Do not share this APK file publicly or with unauthorized individuals to prevent potential misuse.",
                        fontSize = 9.sp,
                        color = HudNeonRed,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        lineHeight = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.showDiagnosticDialog = false },
                    modifier = Modifier.testTag("diagnostics_close_btn")
                ) {
                    Text("DISMISS DIALOG", color = HudNeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF131A26),
            textContentColor = HudTextWhite,
            titleContentColor = HudTextWhite,
            shape = RoundedCornerShape(12.dp)
        )
    }
}
