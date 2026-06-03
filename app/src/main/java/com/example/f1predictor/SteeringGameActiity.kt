package com.example.f1predictor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs
import kotlin.random.Random

// ── Culori F1 ─────────────────────────────────────────────────────
private val F1Red    = Color(0xFFE10600)
private val F1Dark   = Color(0xFF080810)
private val F1Card   = Color(0xFF16162A)
private val F1Border = Color(0xFF2A2A45)
private val F1Muted  = Color(0xFF7070A0)
private val F1Text   = Color(0xFFE8E8F0)
private val F1Teal   = Color(0xFF00D2BE)
private val F1Gold   = Color(0xFFFFD700)
private val F1Blue   = Color(0xFF3498DB)  // accent specific steering

enum class SteeringState { MENIU, JOC, GAME_OVER }

class SteeringGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "steering_game") {
                composable("steering_game") {
                    Box(modifier = Modifier.fillMaxSize().background(F1Dark)) {
                        Button(
                            onClick = { finish() },
                            colors  = ButtonDefaults.buttonColors(containerColor = F1Card),
                            shape   = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .border(1.dp, F1Border, RoundedCornerShape(8.dp))
                        ) { Text("← ÎNAPOI", color = F1Muted, fontSize = 11.sp) }

                        SteeringUI(onNavigateToLeaderboard = { navController.navigate("steering_leaderboard") })
                    }
                }
                composable("steering_leaderboard") {
                    SteeringLeaderboardScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
fun SteeringUI(onNavigateToLeaderboard: () -> Unit) {
    val context = LocalContext.current
    val db      = FirebaseFirestore.getInstance()
    val user    = FirebaseAuth.getInstance().currentUser

    var stareJoc      by remember { mutableStateOf(SteeringState.MENIU) }
    var scor          by remember { mutableIntStateOf(0) }
    var currentAngle  by remember { mutableFloatStateOf(0f) }
    var targetAngle   by remember { mutableFloatStateOf(0f) }
    var timeRemaining by remember { mutableFloatStateOf(100f) }

    val coroutineScope = rememberCoroutineScope()
    val animatedAngle  by animateFloatAsState(
        targetValue = currentAngle,
        animationSpec = tween(durationMillis = 80),
        label = "steering"
    )

    val esteInZona = abs(currentAngle - targetAngle) < 15f

    fun salveazaScorSteering(scorFinal: Int) {
        if (user?.email != null && scorFinal > 0) {
            val ref = db.collection("clasament_volan").document(user.email!!)
            ref.get().addOnSuccessListener { doc ->
                val scorVechi = doc.getLong("scor")?.toInt() ?: 0
                if (scorFinal > scorVechi) {
                    ref.set(hashMapOf("email" to user.email, "scor" to scorFinal, "data" to Date()))
                    Toast.makeText(context, "🏆 Record Nou: $scorFinal viraje!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Nu ai bătut recordul ($scorVechi viraje)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── SENZOR ACCELEROMETRU ───────────────────────────────────────
    DisposableEffect(Unit) {
        val sensorManager  = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensorListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || stareJoc != SteeringState.JOC) return
                val tiltX = event.values[0]
                currentAngle = ((tiltX / 9.81f) * -90f).coerceIn(-90f, 90f)
            }
        }
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(sensorListener) }
    }

    fun pornesteJocul() {
        scor = 0; stareJoc = SteeringState.JOC
        coroutineScope.launch {
            while (stareJoc == SteeringState.JOC) {
                targetAngle = Random.nextInt(-70, 70).toFloat()
                val cornerTimeMs = maxOf(1000L, 3000L - (scor * 100L))
                val intervalMs   = 50L
                val steps        = cornerTimeMs / intervalMs
                var successTime  = 0f

                for (i in 0 until steps.toInt()) {
                    timeRemaining = 100f - ((i.toFloat() / steps.toFloat()) * 100f)
                    if (abs(currentAngle - targetAngle) < 15f) successTime += intervalMs
                    delay(intervalMs)
                    if (stareJoc != SteeringState.JOC) break
                }

                if (successTime >= cornerTimeMs * 0.6f) {
                    scor++
                } else {
                    stareJoc = SteeringState.GAME_OVER
                    salveazaScorSteering(scor)
                    break
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // MENIU
    // ══════════════════════════════════════════════════════════════
    if (stareJoc == SteeringState.MENIU) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 72.dp, start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Titlu
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "🏎️  STEERING",
                    color = F1Blue, fontSize = 24.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text("CHALLENGE", color = F1Muted, fontSize = 11.sp, letterSpacing = 3.sp)
            }

            // Volanul decorativ in meniu
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    // Inel exterior
                    drawCircle(color = Color(0xFF2A2A45), style = Stroke(width = 28f))
                    drawCircle(color = F1Blue.copy(alpha = 0.3f), style = Stroke(width = 32f))
                    // Spoke orizontal
                    drawRoundRect(
                        color = Color(0xFF3A3A5A),
                        topLeft = Offset(30f, size.height / 2 - 14f),
                        size = Size(size.width - 60f, 28f),
                        cornerRadius = CornerRadius(8f)
                    )
                    // Spoke vertical
                    drawRoundRect(
                        color = Color(0xFF3A3A5A),
                        topLeft = Offset(size.width / 2 - 14f, 30f),
                        size = Size(28f, size.height - 60f),
                        cornerRadius = CornerRadius(8f)
                    )
                    // Accente rosii F1
                    drawArc(color = F1Red, startAngle = 140f, sweepAngle = 80f, useCenter = false, style = Stroke(width = 32f))
                    drawArc(color = F1Red, startAngle = -40f, sweepAngle = 80f, useCenter = false, style = Stroke(width = 32f))
                    // Hub central
                    drawCircle(color = Color(0xFF1A1A2E), radius = 40f)
                    drawCircle(color = F1Blue.copy(alpha = 0.5f), radius = 40f, style = Stroke(width = 2f))
                    drawCircle(color = Color(0xFF888899), radius = 16f)
                }
            }

            // Descriere
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(F1Card, RoundedCornerShape(12.dp))
                    .border(1.dp, F1Border, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    "Înclină telefonul stânga/dreapta pentru a vira.\nMenține unghiul indicat de punctul albastru.\nSurvivuiește cât mai multe viraje!",
                    color = F1Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }

            // Butoane
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick  = { pornesteJocul() },
                    colors   = ButtonDefaults.buttonColors(containerColor = F1Blue),
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("🏎️  START CURSĂ", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = Color.White, letterSpacing = 1.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick  = onNavigateToLeaderboard,
                        colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                            .border(1.dp, F1Border, RoundedCornerShape(10.dp))
                    ) { Text("🏆  CLASAMENT", color = F1Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        return
    }

    // ══════════════════════════════════════════════════════════════
    // JOC + GAME OVER
    // ══════════════════════════════════════════════════════════════
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // ── HUD ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(F1Card, RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (esteInZona && stareJoc == SteeringState.JOC) F1Teal else F1Border,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unghi actual
                Column(horizontalAlignment = Alignment.Start) {
                    Text("ACTUAL", color = F1Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(
                        "${currentAngle.toInt()}°",
                        color = F1Text, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Scor central
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VIRAJE", color = F1Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(
                        "$scor",
                        color = F1Gold, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Unghi tinta
                Column(horizontalAlignment = Alignment.End) {
                    Text("ȚINTĂ", color = F1Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(
                        if (stareJoc == SteeringState.JOC) "${targetAngle.toInt()}°" else "---",
                        color = F1Blue, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // ── PROGRESS BAR TIMING ────────────────────────────────────
        if (stareJoc == SteeringState.JOC) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TIMP VIRAJ", color = F1Muted, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text(
                        if (esteInZona) "✓ IN ZONĂ" else "× CORECTEAZĂ",
                        color = if (esteInZona) F1Teal else F1Red,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                }
                LinearProgressIndicator(
                    progress = { timeRemaining / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color    = if (esteInZona) F1Teal else F1Red,
                    trackColor = F1Border
                )
            }
        }

        // ── VOLAN ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(F1Card, CircleShape)
                .border(1.dp, F1Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Volan care se roteste
            Canvas(
                modifier = Modifier
                    .size(240.dp)
                    .graphicsLayer { rotationZ = animatedAngle }
            ) {
                // Inel exterior
                drawCircle(color = Color(0xFF2A2A45), style = Stroke(width = 30f))
                drawCircle(
                    color = if (esteInZona && stareJoc == SteeringState.JOC)
                        F1Teal.copy(alpha = 0.6f) else F1Red.copy(alpha = 0.4f),
                    style = Stroke(width = 34f)
                )

                // Spoke orizontal
                drawRoundRect(
                    color = Color(0xFF3A3A5A),
                    topLeft = Offset(32f, size.height / 2 - 12f),
                    size    = Size(size.width - 64f, 24f),
                    cornerRadius = CornerRadius(8f)
                )
                // Spoke vertical
                drawRoundRect(
                    color = Color(0xFF3A3A5A),
                    topLeft = Offset(size.width / 2 - 12f, 32f),
                    size    = Size(24f, size.height - 64f),
                    cornerRadius = CornerRadius(8f)
                )

                // Accente rosii F1
                drawArc(color = F1Red, startAngle = 140f, sweepAngle = 80f, useCenter = false, style = Stroke(width = 34f))
                drawArc(color = F1Red, startAngle = -40f, sweepAngle = 80f, useCenter = false, style = Stroke(width = 34f))

                // Hub central
                drawCircle(color = Color(0xFF12122A), radius = 38f)
                drawCircle(color = Color(0xFF2A2A45), radius = 38f, style = Stroke(width = 2f))
                drawCircle(color = Color(0xFF888899), radius = 14f)
            }

            // Indicatorul tintei (punct albastru, nu se roteste odata cu volanul)
            if (stareJoc == SteeringState.JOC) {
                Canvas(
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer { rotationZ = targetAngle }
                ) {
                    drawCircle(
                        color  = F1Blue,
                        radius = 14f,
                        center = Offset(size.width / 2, 16f)
                    )
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.6f),
                        radius = 6f,
                        center = Offset(size.width / 2, 16f)
                    )
                }
            }
        }

        // ── GAME OVER / BUTOANE ────────────────────────────────────
        if (stareJoc == SteeringState.GAME_OVER) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A0505), RoundedCornerShape(12.dp))
                    .border(1.dp, F1Red, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "💥  AI DERAPAT!",
                        color = F1Red, fontSize = 22.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "Scor final: $scor viraje",
                        color = F1Gold, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick  = { pornesteJocul() },
                    colors   = ButtonDefaults.buttonColors(containerColor = F1Blue),
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("🔄  REÎNCEARCĂ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }

                Button(
                    onClick  = { stareJoc = SteeringState.MENIU },
                    colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(52.dp)
                        .border(1.dp, F1Border, RoundedCornerShape(10.dp))
                ) { Text("← MENIU", color = F1Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        } else {
            // Placeholder pentru spacing in timpul jocului
            Spacer(Modifier.height(52.dp))
        }
    }
}

// ── LEADERBOARD ──────────────────────────────────────────────────
@Composable
fun SteeringLeaderboardScreen(onBack: () -> Unit) {
    val db      = FirebaseFirestore.getInstance()
    val scoruri = remember { mutableStateListOf<JucatorScor>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("clasament_volan")
            .orderBy("scor", Query.Direction.DESCENDING)
            .limit(20).get()
            .addOnSuccessListener { res ->
                scoruri.clear()
                for (doc in res)
                    scoruri.add(JucatorScor(doc.getString("email") ?: "---", doc.getLong("scor") ?: 0L))
                isLoading = false
            }.addOnFailureListener { isLoading = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(F1Dark)
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, F1Blue, Color.Transparent)))
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "🏎️  TOP PILOȚI",
            color = F1Blue, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "CLASAMENT GLOBAL · STEERING CHALLENGE",
            color = F1Muted, fontSize = 9.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(color = F1Blue, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(scoruri) { index, scor ->
                    val medalIcon   = when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}." }
                    val borderColor = when (index) { 0 -> F1Gold; 1 -> Color(0xFFC0C0C0); 2 -> Color(0xFFCD7F32); else -> F1Border }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(F1Card, RoundedCornerShape(10.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(medalIcon, fontSize = 18.sp)
                                Text(
                                    scor.email.split("@")[0].uppercase(),
                                    color = F1Text, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                )
                            }
                            Text(
                                "${scor.timp}  viraje",
                                color = F1Blue, fontWeight = FontWeight.Bold,
                                fontSize = 14.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick  = onBack,
            colors   = ButtonDefaults.buttonColors(containerColor = F1Red),
            shape    = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("← ÎNAPOI", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
    }
}