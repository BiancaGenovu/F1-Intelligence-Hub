package com.example.f1predictor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ── Culori F1 ─────────────────────────────────────────────────
private val F1Red    = Color(0xFFE10600)
private val F1Dark   = Color(0xFF080810)
private val F1Card   = Color(0xFF16162A)
private val F1Border = Color(0xFF2A2A45)
private val F1Muted  = Color(0xFF7070A0)
private val F1Text   = Color(0xFFE8E8F0)

class CommunityDriversActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var driversList by remember { mutableStateOf<List<DriverProfileData>>(emptyList()) }
            var isLoading   by remember { mutableStateOf(true) }
            val currentUid  = auth.currentUser?.uid

            LaunchedEffect(Unit) {
                db.collection("users")
                    .whereEqualTo("isProfileComplete", true)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        driversList = snapshot.documents
                            .filter { it.id != currentUid }
                            .mapNotNull { it.toObject(DriverProfileData::class.java) }
                        isLoading = false
                    }
                    .addOnFailureListener { isLoading = false }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(F1Dark)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {

                    // ── Speed line ────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, F1Red, Color.Transparent)
                                )
                            )
                    )

                    // ── Header ────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(F1Card, RoundedCornerShape(8.dp))
                                .border(1.dp, F1Border, RoundedCornerShape(8.dp))
                                .clickable { finish() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "← ÎNAPOI",
                                color = F1Muted, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            "GRILĂ DE START",
                            color      = F1Red,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            modifier   = Modifier.weight(1f),
                            textAlign  = TextAlign.Center
                        )

                        Box(modifier = Modifier.width(80.dp))
                    }

                    // Divider rosu
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, F1Red, Color.Transparent)
                                )
                            )
                    )

                    // ── Continut ──────────────────────────────
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color    = F1Red,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        "Se încarcă grila...",
                                        color = F1Muted, fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        driversList.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Text("🏁", fontSize = 48.sp)
                                    Text(
                                        "Grila e goală momentan",
                                        color = F1Text, fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Niciun alt pilot nu și-a completat profilul încă.",
                                        color = F1Muted, fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        else -> {
                            // Counter piloți
                            Text(
                                "${driversList.size} PILOT${if (driversList.size != 1) "I" else ""} ALINIA${if (driversList.size != 1) "ȚI" else "T"}",
                                color    = F1Muted,
                                fontSize = 9.sp,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )

                            LazyColumn(
                                modifier      = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start  = 16.dp,
                                    end    = 16.dp,
                                    bottom = 24.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(driversList) { index, driver ->
                                    val team = TeamProvider.teams.find { it.id == driver.teamId }
                                        ?: TeamProvider.teams.first()

                                    DriverCard(
                                        driver    = driver,
                                        team      = team,
                                        position  = index + 1
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

// ═══════════════════════════════════════════════════════════════
// DRIVER CARD
// ═══════════════════════════════════════════════════════════════

@Composable
fun DriverCard(
    driver:   DriverProfileData,
    team:     TeamConfig,
    position: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(175.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        team.primaryColor.copy(alpha = 0.75f),
                        Color(0xFF080810)
                    )
                )
            )
            .border(1.dp, team.primaryColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        // Numar pozitie grila (fundal)
        Text(
            text     = "P$position",
            color    = Color.White.copy(alpha = 0.06f),
            fontSize = 80.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
        )

        // Foto pilot
        Box(
            modifier = Modifier
                .size(90.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .background(Color(0xFF0D0D1E))
                .border(2.dp, team.primaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (driver.profileImageUri.isNotEmpty()) {
                AsyncImage(
                    model             = Uri.parse(driver.profileImageUri),
                    contentDescription = "Avatar ${driver.firstName}",
                    modifier          = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale      = ContentScale.Crop
                )
            } else {
                Text("🏎️", fontSize = 28.sp)
            }
        }

        // Pozitie grila mica (top left deasupra pozei)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(team.primaryColor, RoundedCornerShape(bottomEnd = 8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                "P$position",
                color = if (team.primaryColor == Color.White) Color.Black else Color.White,
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Info pilot (dreapta)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                driver.firstName.uppercase(),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp, fontFamily = FontFamily.Monospace
            )
            Text(
                driver.lastName.uppercase(),
                color = Color.White,
                fontSize = 20.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                team.name.uppercase(),
                color = team.primaryColor,
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Numar pilot (bottom right)
        if (driver.driverNumber.isNotEmpty()) {
            Text(
                driver.driverNumber,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 48.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}