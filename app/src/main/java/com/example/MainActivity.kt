package com.example

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity(), SensorEventListener {

    private val viewModel: GameViewModel by viewModels()
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var canTriggerSensor = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup the physical motion sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        setContent {
            MyApplicationTheme {
                // Force Right-to-Left Layout for professional Arabic localization
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("main_scaffold"),
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { innerPadding ->
                        GameScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        
        // Active tilt-detection only during active gameplay and when no transition feedback is flashing
        if (viewModel.gameStage.value == GameStage.GAMEPLAY && viewModel.feedbackState.value == null) {
            val z = event.values[2] // Z-axis acceleration

            if (canTriggerSensor) {
                if (z > 5.5f) { // Tilting forward/down (Correct!)
                    canTriggerSensor = false
                    viewModel.recordCorrect()
                } else if (z < -5.5f) { // Tilting backward/up (Skip!)
                    canTriggerSensor = false
                    viewModel.recordSkip()
                }
            } else {
                // Reset flag when the phone returns to near-upright vertical position on forehead
                if (Math.abs(z) < 2.5f) {
                    canTriggerSensor = true
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}

// Confetti Particle representation
data class ConfettiParticle(
    var x: Float,
    var y: Float,
    val color: Color,
    val speedY: Float,
    val speedX: Float,
    val size: Float,
    var rotation: Float,
    val rotationSpeed: Float
)

@Composable
fun ConfettiEffect() {
    val particles = remember {
        List(80) {
            ConfettiParticle(
                x = Random.nextFloat() * 1200f,
                y = Random.nextFloat() * -1000f,
                color = listOf(
                    Color(0xFFFFD700), Color(0xFFFF5722), Color(0xFF4CAF50), 
                    Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFE91E63)
                ).random(),
                speedY = Random.nextFloat() * 6f + 4f,
                speedX = Random.nextFloat() * 4f - 2f,
                size = Random.nextFloat() * 15f + 10f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = Random.nextFloat() * 5f - 2.5f
            )
        }
    }

    var triggerAnimate by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(16) // ~60fps
            particles.forEach { p ->
                p.y += p.speedY
                p.x += p.speedX
                p.rotation += p.rotationSpeed
                if (p.y > 1000f) {
                    p.y = -50f
                    p.x = Random.nextFloat() * 1200f
                }
            }
            triggerAnimate++
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // force redraw on triggerAnimate increment
        triggerAnimate.let {
            particles.forEach { p ->
                rotate(p.rotation, pivot = Offset(p.x, p.y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(p.x, p.y),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel, modifier: Modifier = Modifier) {
    val stage by viewModel.gameStage.collectAsState()
    val showHowToPlay by viewModel.showHowToPlay.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Very rich dark slate blue
                        Color(0xFF1E293B)
                    )
                )
            )
    ) {
        when (stage) {
            GameStage.REGISTRATION -> RegistrationView(viewModel)
            GameStage.CATEGORY_SELECTION -> CategorySelectionView(viewModel)
            GameStage.ROUND_READY -> RoundReadyView(viewModel)
            GameStage.GAMEPLAY -> GameplayView(viewModel)
            GameStage.ROUND_SUMMARY -> RoundSummaryView(viewModel)
            GameStage.PODIUM -> PodiumView(viewModel)
        }

        // Global Info pop-up dialog
        if (showHowToPlay) {
            HowToPlayDialog(onDismiss = { viewModel.toggleHowToPlay(false) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationView(viewModel: GameViewModel) {
    val players by viewModel.players.collectAsState()
    var newPlayerName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left Side: Registration Input
        Card(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ملك التخمين 👑",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF5AF19),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "لعبة تجمعات حماسية لأصدقائك وعائلتك!",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "تسجيل أسماء اللاعبين (لاعبين أو أكثر):",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = newPlayerName,
                        onValueChange = { newPlayerName = it },
                        placeholder = { Text("أدخل اسم اللاعب الحالي...", color = Color(0xFF64748B)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF5AF19),
                            unfocusedBorderColor = Color(0xFF475569),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("player_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (newPlayerName.trim().isNotEmpty()) {
                                        viewModel.addPlayer(newPlayerName)
                                        newPlayerName = ""
                                    }
                                },
                                modifier = Modifier.testTag("add_player_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "إضافة", tint = Color(0xFFF5AF19))
                            }
                        }
                    )
                }

                Button(
                    onClick = {
                        if (players.size >= 2) {
                            viewModel.nextTurn()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (players.size >= 2) Color(0xFFF5AF19) else Color(0xFF475569)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_game_button"),
                    shape = RoundedCornerShape(16.dp),
                    enabled = players.size >= 2
                ) {
                    Text(
                        text = if (players.size >= 2) "اختر الفئة وابدأ التحدي! 🚀" else "يرجى إضافة لاعبين على الأقل!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (players.size >= 2) Color(0xFF0F172A) else Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Right Side: Current Players List
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.9f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اللاعبين المسجلين (${players.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = { viewModel.toggleHowToPlay(true) },
                        modifier = Modifier.testTag("how_to_play_button")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "شرح اللعبة", tint = Color(0xFF38EF7D))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (players.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا يوجد لاعبين مضافين حالياً!\nأضف الأسماء في اليمين للبدء.",
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(players) { player ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFFF5AF19).copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = player.name.firstOrNull()?.toString() ?: "👤",
                                            color = Color(0xFFF5AF19),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = player.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 16.sp
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removePlayer(player) },
                                    modifier = Modifier.testTag("remove_${player.name}")
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "حذف",
                                        tint = Color(0xFFEF5350)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategorySelectionView(viewModel: GameViewModel) {
    val players by viewModel.players.collectAsState()
    val currentPlayerIndex by viewModel.currentPlayerIndex.collectAsState()
    val categories = remember { GameRepository.categories }

    val activePlayer = players.getOrNull(currentPlayerIndex) ?: Player("", "غير معروف")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Custom Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.toggleHowToPlay(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.testTag("how_to_play_button")
            ) {
                Icon(Icons.Default.Info, contentDescription = "شرح", tint = Color(0xFF38EF7D), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("شرح اللعبة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ملك التخمين 👑",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "اختر الفئة وابدأ التحدي!",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Button(
                onClick = { viewModel.backToMainMenu() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350).copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, Color(0xFFEF5350).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(50.dp)
            ) {
                Text("الرئيسية", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Player Turn Reminder
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3C72).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFF2A5298))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👤 الدور الآن على: ",
                    color = Color(0xFF93F9B9),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = activePlayer.name,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
                Text(
                    text = " .. جهّز نفسك وضع الهاتف على رأسك! 📱",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Grid of 8 categories
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(categories) { category ->
                CategoryCard(category = category, onClick = { viewModel.selectCategory(category) })
            }
        }
    }
}

@Composable
fun CategoryCard(category: Category, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable { onClick() }
            .testTag("category_${category.id}"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(category.startColor, category.endColor)
                    )
                )
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.icon,
                        fontSize = 28.sp
                    )
                    Text(
                        text = category.wordsCountText,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Column {
                    Text(
                        text = category.name.substringAfter(" ").trim(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.subtitle,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun RoundReadyView(viewModel: GameViewModel) {
    val startCountdown by viewModel.startCountdown.collectAsState()
    val players by viewModel.players.collectAsState()
    val currentPlayerIndex by viewModel.currentPlayerIndex.collectAsState()
    val category by viewModel.selectedCategory.collectAsState()

    val activePlayer = players.getOrNull(currentPlayerIndex) ?: Player("", "غير معروف")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        // Circular animated aura background
        val infiniteTransition = rememberInfiniteTransition()
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = Modifier
                .size(350.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF7971E).copy(alpha = 0.15f * pulseScale),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "استعد يا ${activePlayer.name}! 📱",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "الفئة المختارة: ${category?.name ?: ""}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5AF19),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .border(1.5.dp, Color(0xFFF5AF19), RoundedCornerShape(50.dp))
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "ضع الهاتف على رأسك فوراً بحيث يراه أصدقاؤك!",
                fontSize = 16.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )

            // Huge Countdown text with scales
            AnimatedContent(
                targetState = startCountdown,
                transitionSpec = {
                    scaleIn(animationSpec = spring()) togetherWith scaleOut()
                }
            ) { targetValue ->
                Text(
                    text = targetValue.toString(),
                    fontSize = 100.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF38EF7D),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("countdown_text")
                )
            }

            Text(
                text = "سيتم تفعيل مستشعر الحركة تلقائياً للعب!",
                fontSize = 14.sp,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun GameplayView(viewModel: GameViewModel) {
    val currentWord by viewModel.currentWord.collectAsState()
    val currentWordIndex by viewModel.currentWordIndex.collectAsState()
    val score by viewModel.currentRoundScore.collectAsState()
    val feedbackState by viewModel.feedbackState.collectAsState()
    val players by viewModel.players.collectAsState()
    val currentPlayerIndex by viewModel.currentPlayerIndex.collectAsState()

    val activePlayer = players.getOrNull(currentPlayerIndex) ?: Player("", "غير معروف")

    // Dynamic background color depending on tilt feedback
    val backgroundColor = when (feedbackState) {
        FeedbackType.CORRECT -> Color(0xFF2E7D32) // Soft Emerald Green
        FeedbackType.SKIP -> Color(0xFFC62828) // Soft Crimson Red
        null -> Color(0xFF0F172A) // Sleek dark blueprint blue
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        // 1. Core Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Player Badge
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(50.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("👤 اللاعب: ", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Text(activePlayer.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            // Central Pulsing Word Counter
            val isFinalWord = currentWordIndex == 10
            val infiniteTransition = rememberInfiniteTransition()
            val timerScale by if (isFinalWord) {
                infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            } else {
                remember { mutableStateOf(1.0f) }
            }

            Box(
                modifier = Modifier
                    .scale(timerScale)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isFinalWord) Color(0xFFEF5350) else Color(0xFFF5AF19))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📝 الكلمة: $currentWordIndex / 10",
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    modifier = Modifier.testTag("game_timer")
                )
            }

            // Score Tracker
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(50.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏆 النقاط: ", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    Text(score.toString(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }

        // 2. Main content: WORD display or feedback indicators
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            if (feedbackState != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (feedbackState == FeedbackType.CORRECT) "🎉 إجابة صحيحة!" else "⚠️ تخطي للكلمة التالية",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (feedbackState == FeedbackType.CORRECT) "+1 نقطة" else "لا تحتسب نقاط",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    text = currentWord,
                    fontSize = 62.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 74.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag("active_word_text")
                )
            }
        }

        // 3. Bottom controls: Manual controls in case they don't tilt
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Correct manual button
            Button(
                onClick = { viewModel.recordCorrect() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("manual_correct_button"),
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("إجابة صحيحة 👍 (إمالة لأسفل)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Skip manual button
            Button(
                onClick = { viewModel.recordSkip() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("manual_skip_button"),
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("تخطي 👎 (إمالة لأعلى)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun RoundSummaryView(viewModel: GameViewModel) {
    val players by viewModel.players.collectAsState()
    val currentPlayerIndex by viewModel.currentPlayerIndex.collectAsState()
    val answers by viewModel.currentRoundAnswers.collectAsState()
    val score by viewModel.currentRoundScore.collectAsState()

    val activePlayer = players.getOrNull(currentPlayerIndex) ?: Player("", "غير معروف")

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Left Panel: Big score display & next player reminder
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ملخص جولة اللاعب 🎉",
                        fontSize = 18.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activePlayer.name,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.background(Color(0xFF0F172A), RoundedCornerShape(16.dp)).padding(24.dp)
                ) {
                    Text("النقاط التي أحرزتها:", color = Color(0xFF94A3B8), fontSize = 14.sp)
                    Text(
                        text = "$score نقاط",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF38EF7D)
                    )
                }

                Button(
                    onClick = { viewModel.nextTurn() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5AF19)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("next_player_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    val isLastPlayer = currentPlayerIndex == players.size - 1
                    Text(
                        text = if (isLastPlayer) "رؤية منصة التتويج 🏆" else "الدور على اللاعب التالي ➡️",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }

        // Right Panel: Words detailed summary (Guessed vs Passed)
        Card(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "تفاصيل إجابات الـ 10 كلمات:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (answers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لم يتم تخمين أي كلمات في هذه الجولة!", color = Color(0xFF64748B), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(answers) { answer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (answer.isCorrect) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                        else Color(0xFFEF5350).copy(alpha = 0.15f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (answer.isCorrect) Color(0xFF4CAF50).copy(alpha = 0.4f)
                                        else Color(0xFFEF5350).copy(alpha = 0.4f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = answer.word,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )

                                Text(
                                    text = if (answer.isCorrect) "صح 👍" else "تخطي 👎",
                                    color = if (answer.isCorrect) Color(0xFF38EF7D) else Color(0xFFEF5350),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PodiumView(viewModel: GameViewModel) {
    val players by viewModel.players.collectAsState()
    val sortedPlayers = remember(players) { players.sortedByDescending { it.score } }

    val winner = sortedPlayers.firstOrNull() ?: Player("", "مجهول")

    // Show custom confetti!
    ConfettiEffect()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "👑 منصة تتويج ملك التخمين 👑",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFF5AF19),
                textAlign = TextAlign.Center
            )
            Text(
                text = "لقد انتهت المواجهة التاريخية وحان وقت التكريم!",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            // 2nd Place (Silver)
            if (sortedPlayers.size >= 2) {
                val p2 = sortedPlayers[1]
                PodiumColumn(player = p2, rank = 2, height = 110.dp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.width(16.dp))
            }

            // 1st Place (Gold / King)
            PodiumColumn(player = winner, rank = 1, height = 160.dp, color = Color(0xFFF5AF19))

            // 3rd Place (Bronze)
            if (sortedPlayers.size >= 3) {
                Spacer(modifier = Modifier.width(16.dp))
                val p3 = sortedPlayers[2]
                PodiumColumn(player = p3, rank = 3, height = 80.dp, color = Color(0xFFCD7F32))
            }
        }

        // Leaderboard action controls
        Row(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.restartGame() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5AF19)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .testTag("play_again_button")
            ) {
                Text("إعادة اللعب بالدور 🔄", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Button(
                onClick = { viewModel.backToMainMenu() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("main_menu_button")
            ) {
                Text("تغيير اللاعبين 👥", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun PodiumColumn(player: Player, rank: Int, height: androidx.compose.ui.unit.Dp, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (rank == 1) {
            Text("👑", fontSize = 42.sp)
        }
        Text(
            text = player.name,
            fontSize = if (rank == 1) 20.sp else 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text(
            text = "${player.score} نقاط",
            fontSize = 14.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Card(
            modifier = Modifier
                .width(100.dp)
                .height(height),
            colors = CardDefaults.cardColors(containerColor = color),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rank.toString(),
                    fontSize = if (rank == 1) 48.sp else 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}

@Composable
fun HowToPlayDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = { onDismiss() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, Color(0xFFF5AF19)),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .testTag("how_to_play_popup")
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📖 شرح لعبة ملك التخمين 👑",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF5AF19),
                    textAlign = TextAlign.Center
                )

                Divider(color = Color(0xFF334155))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InstructionRow(step = "1", text = "سجّل أسماء اللاعبين أولاً (لاعبين على الأقل لتفعيل المنافسة).")
                    InstructionRow(step = "2", text = "ضع الهاتف على جبهتك (رأسك) بحيث يرى أصدقاؤك الشاشة وتكون الكلمة موجهة لهم.")
                    InstructionRow(step = "3", text = "سيقوم أصدقاؤك بتمثيل الكلمة أو شرحها لك دون نطق اسمها الحقيقي.")
                    InstructionRow(step = "4", text = "إذا عرفت الكلمة وتخمينها صحيح، أمل الشاشة لأسفل (Tilt Down) أو اضغط على الزر الأخضر لحساب نقطة.")
                    InstructionRow(step = "5", text = "إذا صعبت عليك الكلمة وتريد تخطيها، أمل الشاشة لأعلى (Tilt Up) أو اضغط على الزر الأحمر.")
                    InstructionRow(step = "6", text = "لكل لاعب 10 جولات (10 كلمات)، وتخمين واحد فقط لكل كلمة (إما صحيحة أو تخطي) وبدون حد زمني للوقت!")
                }

                Divider(color = Color(0xFF334155))

                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38EF7D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(150.dp)
                ) {
                    Text("فهمت اللعبة! 👍", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun InstructionRow(step: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Color(0xFFF5AF19), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
