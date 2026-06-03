package com.example.f1predictor

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.util.Date

// ── Culori F1 ─────────────────────────────────────────────────
private val F1Red    = Color(0xFFE10600)
private val F1Dark   = Color(0xFF080810)
private val F1Card   = Color(0xFF16162A)
private val F1Border = Color(0xFF2A2A45)
private val F1Muted  = Color(0xFF7070A0)
private val F1Text   = Color(0xFFE8E8F0)
private val F1Gold   = Color(0xFFFFD700)
private val F1Green  = Color(0xFF00C853)
private val F1Teal   = Color(0xFF00D2BE)

private val TOATE_CIRCUITELE = listOf(
    "Australia", "China", "Japan", "Bahrain", "Saudi Arabia", "Miami",
    "Emilia-Romagna", "Monaco", "Spain", "Canada", "Austria", "Great Britain",
    "Belgium", "Hungary", "Netherlands", "Italy", "Azerbaijan", "Singapore",
    "United States", "Mexico", "Brazil", "Las Vegas", "Qatar", "Abu Dhabi"
)

private const val NR_RUNDE           = 10
private const val SECUNDE_RUNDA      = 10
private const val PAUZA_FEEDBACK_MS  = 2000L

enum class StaraQuiz { MENIU, JOC, FEEDBACK, FINAL, CLASAMENT }

data class RundaQuiz(
    val circuit:     String,
    val variante:    List<String>,
    val indexCorect: Int
)

data class QuizScor(
    val email:      String,
    val scorCorect: Int,
    val timpTotal:  Long
)

class CircuitQuizActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize().background(F1Dark)) {
                CircuitQuizGame(onExit = { finish() })
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// JOC PRINCIPAL
// ═══════════════════════════════════════════════════════════════

@Composable
fun CircuitQuizGame(onExit: () -> Unit) {
    val context        = LocalContext.current
    val db             = FirebaseFirestore.getInstance()
    val auth           = FirebaseAuth.getInstance()

    var stare          by remember { mutableStateOf(StaraQuiz.MENIU) }
    var runde          by remember { mutableStateOf<List<RundaQuiz>>(emptyList()) }
    var indexRunda     by remember { mutableIntStateOf(0) }
    var scor           by remember { mutableIntStateOf(0) }
    var timpTotal      by remember { mutableLongStateOf(0L) }
    var timpStartRunda by remember { mutableLongStateOf(0L) }
    var indexAles      by remember { mutableIntStateOf(-1) }
    var timpRamas      by remember { mutableFloatStateOf(SECUNDE_RUNDA.toFloat()) }
    var timerActiv     by remember { mutableStateOf(false) }

    fun genereazaRunde(): List<RundaQuiz> {
        val circuiteAmestecate = TOATE_CIRCUITELE.shuffled().take(NR_RUNDE)
        return circuiteAmestecate.map { circuitCorect ->
            val gresite       = TOATE_CIRCUITELE.filter { it != circuitCorect }.shuffled().take(2)
            val toateVariante = (listOf(circuitCorect) + gresite).shuffled()
            RundaQuiz(
                circuit     = circuitCorect,
                variante    = toateVariante,
                indexCorect = toateVariante.indexOf(circuitCorect)
            )
        }
    }

    fun salveazaScor(scorFinal: Int, timpFinal: Long) {
        val email = auth.currentUser?.email ?: return
        val ref   = db.collection("clasament_quiz").document(email)
        ref.get().addOnSuccessListener { doc ->
            val scorVechi = doc.getLong("scorCorect")?.toInt() ?: -1
            val timpVechi = doc.getLong("timpTotal") ?: Long.MAX_VALUE
            // Salvam daca: scor mai bun SAU scor egal cu timp mai bun
            val eBetter = scorFinal > scorVechi ||
                    (scorFinal == scorVechi && timpFinal < timpVechi)
            if (eBetter) {
                ref.set(hashMapOf(
                    "email"      to email,
                    "scorCorect" to scorFinal,
                    "timpTotal"  to timpFinal,
                    "data"       to Date()
                )).addOnSuccessListener {
                    Toast.makeText(context, "🏆 Record personal nou!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun porneste() {
        runde          = genereazaRunde()
        indexRunda     = 0
        scor           = 0
        timpTotal      = 0L
        indexAles      = -1
        timpRamas      = SECUNDE_RUNDA.toFloat()
        timpStartRunda = System.currentTimeMillis()
        timerActiv     = true
        stare          = StaraQuiz.JOC
    }

    // ── Timer countdown ───────────────────────────────────────
    LaunchedEffect(timerActiv, indexRunda) {
        if (!timerActiv) return@LaunchedEffect
        timpRamas = SECUNDE_RUNDA.toFloat()
        while (timpRamas > 0f && timerActiv) {
            delay(100)
            timpRamas = (timpRamas - 0.1f).coerceAtLeast(0f)
        }
        if (timerActiv && indexAles == -1) {
            timerActiv = false
            indexAles  = -2
            stare      = StaraQuiz.FEEDBACK
            delay(PAUZA_FEEDBACK_MS)
            if (indexRunda + 1 < NR_RUNDE) {
                indexRunda++
                indexAles      = -1
                timpRamas      = SECUNDE_RUNDA.toFloat()
                timpStartRunda = System.currentTimeMillis()
                timerActiv     = true
                stare          = StaraQuiz.JOC
            } else {
                salveazaScor(scor, timpTotal)
                stare = StaraQuiz.FINAL
            }
        }
    }

    fun alegVarianta(idx: Int) {
        if (stare != StaraQuiz.JOC || indexAles != -1) return
        timerActiv = false
        indexAles  = idx
        val runda  = runde[indexRunda]
        if (idx == runda.indexCorect) {
            scor++
            timpTotal += ((System.currentTimeMillis() - timpStartRunda) / 1000f)
                .coerceAtMost(SECUNDE_RUNDA.toFloat()).toLong()
        }
        stare = StaraQuiz.FEEDBACK
    }

    LaunchedEffect(stare) {
        if (stare == StaraQuiz.FEEDBACK) {
            delay(PAUZA_FEEDBACK_MS)
            if (indexRunda + 1 < NR_RUNDE) {
                indexRunda++
                indexAles      = -1
                timpRamas      = SECUNDE_RUNDA.toFloat()
                timpStartRunda = System.currentTimeMillis()
                timerActiv     = true
                stare          = StaraQuiz.JOC
            } else {
                salveazaScor(scor, timpTotal)
                stare = StaraQuiz.FINAL
            }
        }
    }

    when (stare) {
        StaraQuiz.MENIU    -> MenuQuiz(
            onStart      = { porneste() },
            onClasament  = { stare = StaraQuiz.CLASAMENT },
            onExit       = onExit
        )
        StaraQuiz.JOC,
        StaraQuiz.FEEDBACK -> {
            if (runde.isNotEmpty()) {
                RundaScreen(
                    runda      = runde[indexRunda],
                    indexRunda = indexRunda,
                    timpRamas  = timpRamas,
                    indexAles  = indexAles,
                    onAlegere  = { idx -> alegVarianta(idx) }
                )
            }
        }
        StaraQuiz.FINAL    -> FinalQuiz(
            scor        = scor,
            timpTotal   = timpTotal,
            onRestart   = { porneste() },
            onClasament = { stare = StaraQuiz.CLASAMENT },
            onExit      = onExit
        )
        StaraQuiz.CLASAMENT -> QuizLeaderboardScreen(
            onBack = { stare = StaraQuiz.MENIU }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// ECRAN MENIU
// ═══════════════════════════════════════════════════════════════

@Composable
fun MenuQuiz(onStart: () -> Unit, onClasament: () -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, F1Red, Color.Transparent)))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🏁", fontSize = 64.sp)
            Text("GHICEȘTE", color = F1Text, fontSize = 28.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("CIRCUITUL", color = F1Red, fontSize = 28.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(F1Card, RoundedCornerShape(12.dp))
                    .border(1.dp, F1Border, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RegulaMeniu("🎬", "Urmărește cum se desenează circuitul")
                    RegulaMeniu("⏱️", "Ai 10 secunde să ghicești")
                    RegulaMeniu("✅", "3 variante — alege cea corectă")
                    RegulaMeniu("🏆", "10 runde — încearcă să le ghicești pe toate!")
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick  = onStart,
                colors   = ButtonDefaults.buttonColors(containerColor = F1Red),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("🏎️  START QUIZ", color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 1.sp)
            }

            Button(
                onClick  = onClasament,
                colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .border(1.dp, F1Border, RoundedCornerShape(10.dp))
            ) {
                Text("🏆  CLASAMENT", color = F1Gold,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick  = onExit,
                colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .border(1.dp, F1Border, RoundedCornerShape(10.dp))
            ) {
                Text("← ÎNAPOI", color = F1Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RegulaMeniu(icon: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp)
        Text(text, color = F1Muted, fontSize = 13.sp)
    }
}

// ═══════════════════════════════════════════════════════════════
// ECRAN RUNDA
// ═══════════════════════════════════════════════════════════════

@Composable
fun RundaScreen(
    runda:      RundaQuiz,
    indexRunda: Int,
    timpRamas:  Float,
    indexAles:  Int,
    onAlegere:  (Int) -> Unit
) {
    val answered = indexAles != -1

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(F1Card, RoundedCornerShape(8.dp))
                    .border(1.dp, F1Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("RUNDA ${indexRunda + 1} / $NR_RUNDE",
                    color = F1Muted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            }
            Text(
                "${timpRamas.toInt()}s",
                color = if (timpRamas <= 3f) F1Red else F1Text,
                fontSize = 24.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(8.dp))

        // Progress bar timer
        LinearProgressIndicator(
            progress = { timpRamas / SECUNDE_RUNDA },
            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color     = when {
                timpRamas <= 3f -> F1Red
                timpRamas <= 6f -> F1Gold
                else            -> F1Teal
            },
            trackColor = F1Border
        )

        Spacer(Modifier.height(12.dp))

        // GIF Circuit
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(F1Card, RoundedCornerShape(16.dp))
                .border(1.dp, F1Border, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            key(runda.circuit) {
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    },
                    update = { imageView ->
                        Glide.with(imageView.context)
                            .asGif()
                            .load(Uri.parse("file:///android_asset/quiz_circuite/${runda.circuit}.gif"))
                            .listener(object : RequestListener<GifDrawable> {
                                override fun onLoadFailed(
                                    e: GlideException?, model: Any?,
                                    target: Target<GifDrawable>, isFirstResource: Boolean
                                ): Boolean = false

                                override fun onResourceReady(
                                    resource: GifDrawable, model: Any,
                                    target: Target<GifDrawable>?,
                                    dataSource: DataSource, isFirstResource: Boolean
                                ): Boolean {
                                    resource.setLoopCount(1)
                                    return false
                                }
                            })
                            .into(imageView)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Variante raspuns
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            runda.variante.forEachIndexed { idx, varianta ->
                val culoare by animateColorAsState(
                    targetValue = when {
                        !answered                -> F1Card
                        idx == runda.indexCorect -> F1Green
                        idx == indexAles         -> F1Red
                        else                     -> F1Card
                    },
                    animationSpec = tween(300), label = "btn_$idx"
                )
                val border = when {
                    !answered                -> F1Border
                    idx == runda.indexCorect -> F1Green
                    idx == indexAles         -> F1Red
                    else                     -> F1Border
                }

                Button(
                    onClick  = { onAlegere(idx) },
                    enabled  = !answered,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = culoare,
                        disabledContainerColor = culoare
                    ),
                    shape    = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        if (answered && idx == runda.indexCorect)
                            Text("✓  ", color = Color.White, fontWeight = FontWeight.Bold)
                        else if (answered && idx == indexAles && idx != runda.indexCorect)
                            Text("✗  ", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(varianta, color = F1Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ECRAN FINAL
// ═══════════════════════════════════════════════════════════════

@Composable
fun FinalQuiz(
    scor:       Int,
    timpTotal:  Long,
    onRestart:  () -> Unit,
    onClasament: () -> Unit,
    onExit:     () -> Unit
) {
    val procentCorect = (scor.toFloat() / NR_RUNDE * 100).toInt()
    val mesaj = when {
        scor == NR_RUNDE -> "PERFECT! 🏆 Cunoști toate circuitele!"
        scor >= 8        -> "EXCELENT! 🔥 Ești un adevărat fan F1!"
        scor >= 6        -> "BINE! 👍 Mai exersează puțin!"
        scor >= 4        -> "DECENT! 🤔 Mai ai de învățat!"
        else             -> "AI NEVOIE DE PRACTICĂ! 📚"
    }
    val medaliu = when {
        scor == 10 -> "🏆"; scor >= 8 -> "🥇"; scor >= 6 -> "🥈"
        scor >= 4  -> "🥉"; else      -> "🎯"
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, F1Red, Color.Transparent)))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(medaliu, fontSize = 72.sp)
            Text(mesaj, color = F1Text, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(F1Card, RoundedCornerShape(16.dp))
                    .border(1.dp, F1Gold, RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("SCOR FINAL", color = F1Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("$scor / $NR_RUNDE", color = F1Gold, fontSize = 52.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("$procentCorect% răspunsuri corecte", color = F1Muted, fontSize = 13.sp)

                    if (scor > 0) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0A0A14), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Timp mediu per răspuns corect: ${timpTotal / scor}s",
                                color = F1Teal, fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick  = onRestart,
                colors   = ButtonDefaults.buttonColors(containerColor = F1Red),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("🔄  JOACĂ DIN NOU", color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Button(
                onClick  = onClasament,
                colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .border(1.dp, F1Gold, RoundedCornerShape(10.dp))
            ) {
                Text("🏆  VEZI CLASAMENT", color = F1Gold,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick  = onExit,
                colors   = ButtonDefaults.buttonColors(containerColor = F1Card),
                shape    = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .border(1.dp, F1Border, RoundedCornerShape(10.dp))
            ) {
                Text("← ÎNAPOI LA MENIU", color = F1Muted,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// ECRAN CLASAMENT
// ═══════════════════════════════════════════════════════════════

@Composable
fun QuizLeaderboardScreen(onBack: () -> Unit) {
    val db        = FirebaseFirestore.getInstance()
    val scoruri   = remember { mutableStateListOf<QuizScor>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("clasament_quiz")
            .get()
            .addOnSuccessListener { result ->
                val lista = result.documents.mapNotNull { doc ->
                    val email  = doc.getString("email") ?: return@mapNotNull null
                    val corect = doc.getLong("scorCorect")?.toInt() ?: 0
                    val timp   = doc.getLong("timpTotal") ?: 0L
                    QuizScor(email, corect, timp)
                }
                // Sortare: 1) raspunsuri corecte descrescator, 2) timp total crescator
                val sorted = lista
                    .sortedWith(
                        compareByDescending<QuizScor> { it.scorCorect }
                            .thenBy { it.timpTotal }
                    )
                    .take(20)

                scoruri.clear()
                scoruri.addAll(sorted)
                isLoading = false
            }
            .addOnFailureListener { isLoading = false }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(F1Dark).padding(20.dp)
    ) {
        // Speed line
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, F1Gold, Color.Transparent)))
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "🏁  CLASAMENT QUIZ",
            color = F1Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            "GHICEȘTE CIRCUITUL · TOP 20",
            color = F1Muted, fontSize = 9.sp, letterSpacing = 2.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .padding(top = 4.dp, bottom = 6.dp)
        )

        // Header coloane
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("#   PILOT", color = F1Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("CORECTE", color = F1Muted, fontSize = 10.sp, letterSpacing = 1.sp)
                Text("TIMP", color = F1Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(F1Border))

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            CircularProgressIndicator(
                color    = F1Gold,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp)
            )
        } else if (scoruri.isEmpty()) {
            Text(
                "Nu există scoruri înregistrate încă.\nFii primul!",
                color = F1Muted, fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp)
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
                        0 -> F1Gold
                        1 -> Color(0xFFC0C0C0)
                        2 -> Color(0xFFCD7F32)
                        else -> F1Border
                    }
                    val culoareScor = when {
                        scor.scorCorect == NR_RUNDE -> F1Gold
                        scor.scorCorect >= 8        -> F1Green
                        scor.scorCorect >= 5        -> F1Teal
                        else                        -> F1Muted
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(F1Card, RoundedCornerShape(10.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rank + Nume
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(medalIcon, fontSize = 18.sp)
                                Text(
                                    scor.email.substringBefore("@"),
                                    color = F1Text, fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp, maxLines = 1
                                )
                            }

                            // Scor + Timp
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Scor corect
                                Text(
                                    "${scor.scorCorect}/$NR_RUNDE",
                                    color = culoareScor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                // Timp total
                                Text(
                                    "${scor.timpTotal}s",
                                    color = F1Muted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
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