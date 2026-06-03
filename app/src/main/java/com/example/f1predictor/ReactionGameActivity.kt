package com.example.f1predictor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.random.Random
import com.example.f1predictor.R

// ── Culori F1 ────────────────────────────────────────────────────
private val F1Red      = Color(0xFFE10600)
private val F1Dark     = Color(0xFF080810)
private val F1Card     = Color(0xFF16162A)
private val F1Border   = Color(0xFF2A2A45)
private val F1Muted    = Color(0xFF7070A0)
private val F1Text     = Color(0xFFE8E8F0)
private val F1Teal     = Color(0xFF00D2BE)
private val F1Gold     = Color(0xFFFFD700)

enum class StareJoc { GATA, START, CURSA, MOTOR_PORNIT, FURAT, ORDINE_GRESITA, TERMINAT }
data class JucatorScor(val email: String, val timp: Long)

class ReactionGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "game") {
                composable("game") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(F1Dark)
                    ) {
                        // Back button
                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = F1Card),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .border(1.dp, F1Border, RoundedCornerShape(8.dp))
                        ) {
                            Text("← ÎNAPOI", color = F1Muted, fontSize = 11.sp)
                        }

                        SemaforUI(onNavigateToLeaderboard = { navController.navigate("leaderboard") })
                    }
                }
                composable("leaderboard") {
                    LeaderboardScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
fun SemaforUI(onNavigateToLeaderboard: () -> Unit) {
    val context = LocalContext.current
    val db   = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showInstructions by remember { mutableStateOf(false) }

    fun redaSunet(idResursa: Int, loop: Boolean = false) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(context, idResursa)
        mediaPlayer?.setVolume(1.0f, 1.0f)
        mediaPlayer?.isLooping = loop
        mediaPlayer?.start()
    }

    fun salveazaScorInCloud(timpNou: Long) {
        if (user != null && user.email != null) {
            val ref = db.collection("clasament").document(user.email!!)
            ref.get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val timpVechi = doc.getLong("timp") ?: Long.MAX_VALUE
                    if (timpNou < timpVechi) {
                        ref.update("timp", timpNou, "data", java.util.Date())
                            .addOnSuccessListener { Toast.makeText(context, "🏆 Record Nou: $timpNou ms!", Toast.LENGTH_SHORT).show() }
                            .addOnFailureListener { e -> Toast.makeText(context, "Eroare: ${e.message}", Toast.LENGTH_LONG).show() }
                    } else {
                        Toast.makeText(context, "Nu ai bătut recordul ($timpVechi ms)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    ref.set(hashMapOf("email" to user.email, "timp" to timpNou, "data" to java.util.Date()))
                        .addOnSuccessListener { Toast.makeText(context, "Primul scor salvat!", Toast.LENGTH_SHORT).show() }
                        .addOnFailureListener { e -> Toast.makeText(context, "Eroare: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
        } else {
            Toast.makeText(context, "Nu ești autentificat! Scorul nu a fost salvat.", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) { onDispose { mediaPlayer?.release() } }

    val culoriBuline  = remember { mutableStateListOf(Color.DarkGray, Color.DarkGray, Color.DarkGray, Color.DarkGray, Color.DarkGray) }
    val coroutineScope = rememberCoroutineScope()
    var stareJoc     by remember { mutableStateOf(StareJoc.GATA) }
    var timpReactie  by remember { mutableLongStateOf(0L) }
    var timpStart    by remember { mutableLongStateOf(0L) }

    DisposableEffect(stareJoc) {
        val sensorManager  = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val sensorListener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                if (kotlin.math.sqrt(x * x + y * y + z * z) > 14.0) {
                    if (stareJoc == StareJoc.CURSA) {
                        stareJoc = StareJoc.ORDINE_GRESITA; redaSunet(R.raw.error)
                    } else if (stareJoc == StareJoc.MOTOR_PORNIT) {
                        timpReactie = System.currentTimeMillis() - timpStart
                        stareJoc = StareJoc.TERMINAT; redaSunet(R.raw.engine_start)
                        salveazaScorInCloud(timpReactie)
                    }
                }
            }
        }
        if (stareJoc == StareJoc.CURSA || stareJoc == StareJoc.MOTOR_PORNIT)
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(sensorListener) }
    }

    fun pornesteStartul() {
        coroutineScope.launch {
            timpReactie = 0L; culoriBuline.fill(Color.DarkGray); stareJoc = StareJoc.START
            for (i in 0..4) {
                if (stareJoc == StareJoc.FURAT) return@launch
                delay(1000); culoriBuline[i] = F1Red
            }
            delay(Random.nextLong(1000, 4000))
            if (stareJoc == StareJoc.FURAT) return@launch
            culoriBuline.fill(Color.DarkGray); stareJoc = StareJoc.CURSA; timpStart = System.currentTimeMillis()
        }
    }

    // ── DIALOG INSTRUCTIUNI ────────────────────────────────────────
    if (showInstructions) {
        AlertDialog(
            onDismissRequest = { showInstructions = false },
            containerColor   = F1Card,
            shape            = RoundedCornerShape(12.dp),
            title = {
                Text(
                    "📖  CUM SE JOACĂ",
                    color      = F1Text,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstructionRow("1.", "Apasă pe butonul START.")
                    InstructionRow("2.", "Așteaptă aprinderea și stingerea celor 5 lumini roșii.")
                    InstructionRow("3.", "Când s-au stins, APASĂ PE ECRAN pentru a porni motorul.")
                    InstructionRow("4.", "Apoi AGITĂ RAPID TELEFONUL pentru a accelera!")
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A0505), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "⚠️ Dacă agiți telefonul înainte să apeși pe ecran, primești penalizare!",
                            color    = F1Red,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInstructions = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = F1Red),
                    shape   = RoundedCornerShape(8.dp)
                ) { Text("AM ÎNȚELES", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        )
    }

    // ── UI PRINCIPAL ───────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ) {
                if (stareJoc == StareJoc.CURSA) {
                    stareJoc = StareJoc.MOTOR_PORNIT; redaSunet(R.raw.engine_idle, loop = true)
                } else if (stareJoc == StareJoc.START) {
                    stareJoc = StareJoc.FURAT; redaSunet(R.raw.error)
                }
            },
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center
    ) {

        // Titlu
        Text(
            "🚦  LIGHTS OUT",
            color      = F1Red,
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        Text(
            "REACTION TEST",
            color    = F1Muted,
            fontSize = 11.sp,
            letterSpacing = 3.sp
        )

        Spacer(Modifier.height(32.dp))

        // ── SEMAFORUL ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .background(F1Card, RoundedCornerShape(16.dp))
                .border(1.dp, F1Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Label sus
                Text(
                    "FORMULA 1",
                    color    = F1Muted,
                    fontSize = 9.sp,
                    letterSpacing = 3.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Bulele verticale cu glow
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for (i in 0..4) {
                        val isOn = culoriBuline[i] == F1Red
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isOn) F1Red else Color(0xFF1A1A2E)
                                )
                                .border(
                                    2.dp,
                                    if (isOn) F1Red else Color(0xFF2A2A45),
                                    CircleShape
                                )
                        )
                    }
                }

                // Label jos
                Text(
                    "START PROCEDURE",
                    color    = F1Muted,
                    fontSize = 9.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── MESAJE STATUS ──────────────────────────────────────────
        val (mesajStatus, culoareStatus) = when (stareJoc) {
            StareJoc.TERMINAT      -> "⏱ $timpReactie ms" to F1Teal
            StareJoc.FURAT         -> "🚫 START FURAT!" to F1Red
            StareJoc.ORDINE_GRESITA -> "⚠️ EROARE SENZOR!" to F1Red
            StareJoc.CURSA         -> "👆 APASĂ PE ECRAN!" to F1Gold
            StareJoc.MOTOR_PORNIT  -> "📱 AGITĂ TELEFONUL!" to F1Gold
            else                   -> "" to Color.Transparent
        }

        if (mesajStatus.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(F1Card, RoundedCornerShape(10.dp))
                    .border(1.dp, F1Border, RoundedCornerShape(10.dp))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mesajStatus,
                    color      = culoareStatus,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
            }

            if (stareJoc == StareJoc.TERMINAT) {
                Spacer(Modifier.height(6.dp))
                Text("Scor salvat în clasament!", color = F1Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── BUTON START ────────────────────────────────────────────
        val startEnabled = stareJoc != StareJoc.START && stareJoc != StareJoc.CURSA && stareJoc != StareJoc.MOTOR_PORNIT
        Button(
            enabled = startEnabled,
            onClick = { pornesteStartul() },
            colors  = ButtonDefaults.buttonColors(
                containerColor = F1Red,
                disabledContainerColor = Color(0xFF4A0000)
            ),
            shape   = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                "⚡  START",
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                letterSpacing = 2.sp
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── BUTOANE SECUNDARE ──────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick  = onNavigateToLeaderboard,
                colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, F1Border, RoundedCornerShape(10.dp))
            ) {
                Text("🏆  CLASAMENT", color = F1Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick  = { showInstructions = true },
                colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, F1Border, RoundedCornerShape(10.dp))
            ) {
                Text("📖  REGULI", color = F1Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── COMPONENT HELPER ──────────────────────────────────────────────
@Composable
private fun InstructionRow(numar: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(numar, color = F1Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text, color = F1Muted, fontSize = 13.sp)
    }
}

// ── LEADERBOARD SCREEN ────────────────────────────────────────────
@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val db           = FirebaseFirestore.getInstance()
    val listaScoruri = remember { mutableStateListOf<JucatorScor>() }
    var isLoading    by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("clasament")
            .orderBy("timp", Query.Direction.ASCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { result ->
                listaScoruri.clear()
                for (doc in result)
                    listaScoruri.add(JucatorScor(doc.getString("email") ?: "Anonim", doc.getLong("timp") ?: 0L))
                isLoading = false
            }.addOnFailureListener { isLoading = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(F1Dark)
            .padding(20.dp)
    ) {
        // Speed line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(listOf(Color.Transparent, F1Red, Color.Transparent))
                )
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "🏆  TOP PILOȚI",
            color      = F1Gold,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            modifier   = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "CLASAMENT GLOBAL · REACTION TEST",
            color    = F1Muted,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(
                color    = F1Red,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(listaScoruri) { index, scor ->
                    val medalIcon = when (index) {
                        0 -> "🥇"
                        1 -> "🥈"
                        2 -> "🥉"
                        else -> "${index + 1}."
                    }
                    val borderColor = when (index) {
                        0 -> F1Gold
                        1 -> Color(0xFFC0C0C0)
                        2 -> Color(0xFFCD7F32)
                        else -> F1Border
                    }

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
                                    scor.email.substringBefore("@"),
                                    color      = F1Text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 14.sp
                                )
                            }
                            Text(
                                "${scor.timp} ms",
                                color      = F1Teal,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 14.sp,
                                fontFamily = FontFamily.Monospace
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
        ) {
            Text("← ÎNAPOI", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}