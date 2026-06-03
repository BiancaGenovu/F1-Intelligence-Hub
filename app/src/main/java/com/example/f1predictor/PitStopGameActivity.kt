package com.example.f1predictor

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Path
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

private val F1Red    = Color(0xFFE10600)
private val F1Dark   = Color(0xFF080810)
private val F1Card   = Color(0xFF16162A)
private val F1Border = Color(0xFF2A2A45)
private val F1Muted  = Color(0xFF7070A0)
private val F1Text   = Color(0xFFE8E8F0)
private val F1Teal   = Color(0xFF00D2BE)
private val F1Gold   = Color(0xFFFFD700)

enum class PitStopState { GATA, NUMARATOARE, START, TERMINAT }

class PitStopGameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "pitstop_game") {
                composable("pitstop_game") {
                    Box(modifier = Modifier.fillMaxSize().background(F1Dark)) {
                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = F1Card),
                            shape  = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .border(1.dp, F1Border, RoundedCornerShape(8.dp))
                        ) { Text("← ÎNAPOI", color = F1Muted, fontSize = 11.sp) }
                        PitStopUI(onNavigateToLeaderboard = { navController.navigate("pitstop_leaderboard") })
                    }
                }
                composable("pitstop_leaderboard") {
                    PitStopLeaderboardScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
fun PitStopUI(onNavigateToLeaderboard: () -> Unit) {
    val context = LocalContext.current
    val db   = FirebaseFirestore.getInstance()
    val user = FirebaseAuth.getInstance().currentUser

    var stareJoc      by remember { mutableStateOf(PitStopState.GATA) }
    var countdownText by remember { mutableStateOf("3") }
    var timpFinal     by remember { mutableLongStateOf(0L) }
    var timpStart     by remember { mutableLongStateOf(0L) }
    val roti           = remember { mutableStateListOf(false, false, false, false) }
    val coroutineScope = rememberCoroutineScope()
    var showInstructions by remember { mutableStateOf(false) }

    fun salveazaScorPitStop(timpNou: Long) {
        if (user?.email != null) {
            val ref = db.collection("clasament_pitstop").document(user.email!!)
            ref.get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val timpVechi = doc.getLong("timp") ?: Long.MAX_VALUE
                    if (timpNou < timpVechi) {
                        ref.update("timp", timpNou, "data", Date())
                        Toast.makeText(context, "🏆 Record Nou: $timpNou ms!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Nu ai bătut recordul ($timpVechi ms)", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    ref.set(hashMapOf("email" to user.email, "timp" to timpNou, "data" to Date()))
                    Toast.makeText(context, "Primul scor salvat!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun verificaFinalizare() {
        if (stareJoc == PitStopState.START && roti.all { it }) {
            timpFinal = System.currentTimeMillis() - timpStart
            stareJoc  = PitStopState.TERMINAT
            salveazaScorPitStop(timpFinal)
        }
    }

    fun pornesteJocul() {
        coroutineScope.launch {
            roti.fill(false); timpFinal = 0L
            stareJoc = PitStopState.NUMARATOARE
            countdownText = "3"; delay(800)
            countdownText = "2"; delay(800)
            countdownText = "1"; delay(800)
            countdownText = "GO!"; stareJoc = PitStopState.START
            timpStart = System.currentTimeMillis()
        }
    }

    if (showInstructions) {
        AlertDialog(
            onDismissRequest = { showInstructions = false },
            containerColor   = F1Card,
            shape            = RoundedCornerShape(12.dp),
            title = {
                Text("🔧  PIT STOP CHALLENGE", color = F1Text,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PitInstructionRow("1.", "Apasă pe START PIT STOP.")
                    PitInstructionRow("2.", "Așteaptă semnalul GO!")
                    PitInstructionRow("3.", "Apasă rapid pe toate cele 4 anvelope roșii.")
                    PitInstructionRow("4.", "Când devin verzi (✓), pit stop-ul e gata!")
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF001A18), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "⏱ Timpul se măsoară de la GO! până când ultima anvelopă e schimbată.",
                            color = F1Teal, fontSize = 12.sp
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "🔧  PIT STOP",
                color = F1Teal, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text("CHALLENGE", color = F1Muted, fontSize = 11.sp, letterSpacing = 3.sp)
        }

        val (mesajStatus, culoare) = when (stareJoc) {
            PitStopState.GATA        -> "Pregătit pentru pit stop?" to F1Muted
            PitStopState.NUMARATOARE -> countdownText to F1Gold
            PitStopState.START       -> "SCHIMBĂ ANVELOPELE!" to F1Red
            PitStopState.TERMINAT    -> "⏱ $timpFinal ms" to F1Teal
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(F1Card, RoundedCornerShape(12.dp))
                .border(1.dp, if (stareJoc == PitStopState.START) F1Red else F1Border,
                    RoundedCornerShape(12.dp))
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                mesajStatus,
                color      = culoare,
                fontSize   = if (stareJoc == PitStopState.NUMARATOARE) 48.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign  = TextAlign.Center
            )
        }

        // ── MAȘINA + ROȚI ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentAlignment = Alignment.Center
        ) {
            // ── CORP MASINA F1 ─────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .width(220.dp)
                    .height(300.dp)
            ) {
                val w = size.width
                val h = size.height

                val red       = Color(0xFFCC0000)
                val darkRed   = Color(0xFF990000)
                val accentRed = Color(0xFFFF2200)
                val wingRed   = Color(0xFFBB0000)
                val carbon    = Color(0xFF222233)
                val exhaust   = Color(0xFF333344)
                val haloColor = Color(0xFF666677)
                val cockpitCol= Color(0xFF0A0A14)
                val helmetCol = Color(0xFF2233AA)
                val helmetVis = Color(0xFF1144CC)

                // ═══ ARIPA FAȚĂ ═══
                drawRoundRect(darkRed, topLeft = Offset(w*0.36f, h*0.032f), size = Size(w*0.018f, h*0.098f), cornerRadius = CornerRadius(4f))
                drawRoundRect(darkRed, topLeft = Offset(w*0.636f, h*0.032f), size = Size(w*0.018f, h*0.098f), cornerRadius = CornerRadius(4f))
                drawRoundRect(wingRed, topLeft = Offset(w*0.356f, h*0.111f), size = Size(w*0.288f, h*0.016f), cornerRadius = CornerRadius(3f))
                drawRoundRect(wingRed, topLeft = Offset(w*0.375f, h*0.086f), size = Size(w*0.250f, h*0.016f), cornerRadius = CornerRadius(3f))
                drawRoundRect(wingRed, topLeft = Offset(w*0.394f, h*0.036f), size = Size(w*0.212f, h*0.043f), cornerRadius = CornerRadius(5f))
                drawRoundRect(darkRed, topLeft = Offset(w*0.453f, h*0.068f), size = Size(w*0.012f, h*0.061f), cornerRadius = CornerRadius(2f))
                drawRoundRect(darkRed, topLeft = Offset(w*0.535f, h*0.068f), size = Size(w*0.012f, h*0.061f), cornerRadius = CornerRadius(2f))

                // ═══ NAS ═══
                val nasPath = Path().apply {
                    moveTo(w*0.441f, h*0.125f); lineTo(w*0.559f, h*0.125f)
                    lineTo(w*0.541f, h*0.170f); lineTo(w*0.459f, h*0.170f); close()
                }
                drawPath(nasPath, darkRed)
                val corpNas = Path().apply {
                    moveTo(w*0.459f, h*0.168f); lineTo(w*0.541f, h*0.168f)
                    lineTo(w*0.523f, h*0.224f); lineTo(w*0.477f, h*0.224f); close()
                }
                drawPath(corpNas, red)

                // ═══ PODELE LATERALE ═══
                val podea1 = Path().apply {
                    moveTo(w*0.434f, h*0.220f); lineTo(w*0.477f, h*0.220f)
                    lineTo(w*0.477f, h*0.712f); lineTo(w*0.426f, h*0.731f)
                    lineTo(w*0.397f, h*0.692f); lineTo(w*0.404f, h*0.385f); close()
                }
                drawPath(podea1, Color(0xFFAA0000))
                val podea2 = Path().apply {
                    moveTo(w*0.566f, h*0.220f); lineTo(w*0.523f, h*0.220f)
                    lineTo(w*0.523f, h*0.712f); lineTo(w*0.574f, h*0.731f)
                    lineTo(w*0.603f, h*0.692f); lineTo(w*0.596f, h*0.385f); close()
                }
                drawPath(podea2, Color(0xFFAA0000))

                val corp = Path().apply {
                    moveTo(w*0.477f, h*0.220f); lineTo(w*0.523f, h*0.220f)
                    lineTo(w*0.537f, h*0.712f); lineTo(w*0.463f, h*0.712f); close()
                }
                drawPath(corp, red)

                // Sidepod stanga
                val sidepodS = Path().apply {
                    moveTo(w*0.404f, h*0.337f); lineTo(w*0.477f, h*0.320f)
                    lineTo(w*0.477f, h*0.616f); lineTo(w*0.412f, h*0.635f)
                    lineTo(w*0.375f, h*0.596f); lineTo(w*0.379f, h*0.394f); close()
                }
                drawPath(sidepodS, wingRed)
                val sidepodD = Path().apply {
                    moveTo(w*0.596f, h*0.337f); lineTo(w*0.523f, h*0.320f)
                    lineTo(w*0.523f, h*0.616f); lineTo(w*0.588f, h*0.635f)
                    lineTo(w*0.625f, h*0.596f); lineTo(w*0.621f, h*0.394f); close()
                }
                drawPath(sidepodD, wingRed)

                // Bargeboards
                drawRoundRect(carbon, topLeft = Offset(w*0.390f, h*0.349f), size = Size(w*0.012f, h*0.110f), cornerRadius = CornerRadius(2f))
                drawRoundRect(carbon, topLeft = Offset(w*0.402f, h*0.358f), size = Size(w*0.009f, h*0.092f), cornerRadius = CornerRadius(2f))
                drawRoundRect(carbon, topLeft = Offset(w*0.598f, h*0.349f), size = Size(w*0.012f, h*0.110f), cornerRadius = CornerRadius(2f))
                drawRoundRect(carbon, topLeft = Offset(w*0.589f, h*0.358f), size = Size(w*0.009f, h*0.092f), cornerRadius = CornerRadius(2f))

                // ═══ COCKPIT ═══
                drawOval(cockpitCol, topLeft = Offset(w*0.459f, h*0.378f), size = Size(w*0.082f, h*0.138f))
                val haloPath = Path().apply {
                    moveTo(w*0.463f, h*0.385f)
                    quadraticBezierTo(w*0.500f, h*0.354f, w*0.537f, h*0.385f)
                    lineTo(w*0.534f, h*0.398f)
                    quadraticBezierTo(w*0.500f, h*0.369f, w*0.466f, h*0.398f)
                    close()
                }
                drawPath(haloPath, haloColor)
                drawRoundRect(haloColor, topLeft = Offset(w*0.494f, h*0.341f), size = Size(w*0.012f, h*0.040f), cornerRadius = CornerRadius(3f))
                drawOval(helmetCol, topLeft = Offset(w*0.476f, h*0.398f), size = Size(w*0.048f, h*0.065f))
                drawOval(helmetVis.copy(alpha = 0.7f), topLeft = Offset(w*0.485f, h*0.390f), size = Size(w*0.030f, h*0.029f))

                // ═══ EVACUARE ═══
                drawRoundRect(exhaust, topLeft = Offset(w*0.508f, h*0.520f), size = Size(w*0.012f, h*0.055f), cornerRadius = CornerRadius(5f))
                drawRoundRect(exhaust, topLeft = Offset(w*0.480f, h*0.520f), size = Size(w*0.012f, h*0.055f), cornerRadius = CornerRadius(5f))

                // ═══ MOTOR SPATE ═══
                val motor = Path().apply {
                    moveTo(w*0.466f, h*0.710f); lineTo(w*0.534f, h*0.710f)
                    lineTo(w*0.528f, h*0.771f); lineTo(w*0.472f, h*0.771f); close()
                }
                drawPath(motor, darkRed)
                val difuzor = Path().apply {
                    moveTo(w*0.463f, h*0.769f); lineTo(w*0.537f, h*0.769f)
                    lineTo(w*0.528f, h*0.838f); lineTo(w*0.472f, h*0.838f); close()
                }
                drawPath(difuzor, carbon)

                // ═══ ARIPA SPATE ═══
                drawRoundRect(darkRed, topLeft = Offset(w*0.491f, h*0.771f), size = Size(w*0.018f, h*0.067f), cornerRadius = CornerRadius(2f))
                drawRoundRect(wingRed, topLeft = Offset(w*0.353f, h*0.835f), size = Size(w*0.294f, h*0.018f), cornerRadius = CornerRadius(3f))
                drawRoundRect(wingRed, topLeft = Offset(w*0.368f, h*0.851f), size = Size(w*0.264f, h*0.015f), cornerRadius = CornerRadius(3f))
                drawRoundRect(darkRed, topLeft = Offset(w*0.350f, h*0.828f), size = Size(w*0.012f, h*0.053f), cornerRadius = CornerRadius(3f))
                drawRoundRect(darkRed, topLeft = Offset(w*0.638f, h*0.828f), size = Size(w*0.012f, h*0.053f), cornerRadius = CornerRadius(3f))
                drawRoundRect(accentRed, topLeft = Offset(w*0.375f, h*0.864f), size = Size(w*0.250f, h*0.009f), cornerRadius = CornerRadius(2f))
            }

            // ── ROȚI — mai mici și mai aproape de mașină ──────────
            // Față stânga
            Box(modifier = Modifier.align(Alignment.TopStart).padding(start = 52.dp, top = 55.dp)) {
                RoataElement(schimbata = roti[0], activ = stareJoc == PitStopState.START) {
                    roti[0] = true; verificaFinalizare()
                }
            }
            // Față dreapta
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(end = 52.dp, top = 55.dp)) {
                RoataElement(schimbata = roti[1], activ = stareJoc == PitStopState.START) {
                    roti[1] = true; verificaFinalizare()
                }
            }
            // Spate stânga
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = 44.dp, bottom = 65.dp)) {
                RoataElement(schimbata = roti[2], activ = stareJoc == PitStopState.START) {
                    roti[2] = true; verificaFinalizare()
                }
            }
            // Spate dreapta
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 44.dp, bottom = 65.dp)) {
                RoataElement(schimbata = roti[3], activ = stareJoc == PitStopState.START) {
                    roti[3] = true; verificaFinalizare()
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick  = { pornesteJocul() },
                enabled  = stareJoc == PitStopState.GATA || stareJoc == PitStopState.TERMINAT,
                colors   = ButtonDefaults.buttonColors(
                    containerColor = F1Teal,
                    disabledContainerColor = Color(0xFF003D38)
                ),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("🔧  START PIT STOP", fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, color = Color.Black, letterSpacing = 1.sp)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick  = onNavigateToLeaderboard,
                    colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                        .border(1.dp, F1Border, RoundedCornerShape(10.dp))
                ) { Text("🏆  CLASAMENT", color = F1Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold) }

                Button(
                    onClick  = { showInstructions = true },
                    colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                        .border(1.dp, F1Border, RoundedCornerShape(10.dp))
                ) { Text("📖  REGULI", color = F1Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun RoataElement(schimbata: Boolean, activ: Boolean, onClick: () -> Unit) {
    val culoareBorder = if (schimbata) F1Teal else F1Red

    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(Color(0xFF0A0A14))
            .border(3.dp, culoareBorder, CircleShape)
            .clickable(
                enabled           = activ && !schimbata,
                onClick           = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication        = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF111111))
                .border(2.dp, Color(0xFF222222), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (schimbata) Color(0xFF004D40) else Color(0xFF1A0A0A))
                    .border(1.dp, culoareBorder.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF888899))
                )
            }
        }
        if (schimbata) {
            Text("✓", color = F1Teal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PitInstructionRow(numar: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(numar, color = F1Teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text, color = F1Muted, fontSize = 13.sp)
    }
}

@Composable
fun PitStopLeaderboardScreen(onBack: () -> Unit) {
    val db      = FirebaseFirestore.getInstance()
    val scoruri = remember { mutableStateListOf<JucatorScor>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("clasament_pitstop")
            .orderBy("timp", Query.Direction.ASCENDING)
            .limit(20).get()
            .addOnSuccessListener { res ->
                scoruri.clear()
                for (doc in res)
                    scoruri.add(JucatorScor(doc.getString("email") ?: "---", doc.getLong("timp") ?: 0L))
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
                .background(Brush.horizontalGradient(
                    listOf(Color.Transparent, F1Teal, Color.Transparent)))
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "🔧  TOP MECANICI",
            color = F1Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "CLASAMENT GLOBAL · PIT STOP CHALLENGE",
            color = F1Muted, fontSize = 9.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 24.dp)
        )
        if (isLoading) {
            CircularProgressIndicator(
                color = F1Teal,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(scoruri) { index, scor ->
                    val medalIcon = when (index) {
                        0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}."
                    }
                    val borderColor = when (index) {
                        0 -> F1Gold; 1 -> Color(0xFFC0C0C0)
                        2 -> Color(0xFFCD7F32); else -> F1Border
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
                                    scor.email.split("@")[0].uppercase(),
                                    color = F1Text, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                )
                            }
                            Text(
                                "${scor.timp} ms",
                                color = F1Teal, fontWeight = FontWeight.Bold,
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
        ) {
            Text("← ÎNAPOI", color = Color.White,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}