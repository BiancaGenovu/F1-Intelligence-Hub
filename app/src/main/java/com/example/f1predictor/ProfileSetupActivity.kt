package com.example.f1predictor

import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// ── Culori F1 ─────────────────────────────────────────────────
private val F1Red    = Color(0xFFE10600)
private val F1Dark   = Color(0xFF080810)
private val F1Card   = Color(0xFF16162A)
private val F1Border = Color(0xFF2A2A45)
private val F1Muted  = Color(0xFF7070A0)
private val F1Text   = Color(0xFFE8E8F0)
private val F1Gold   = Color(0xFFFFD700)

class ProfileSetupActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isEditMode = intent.getBooleanExtra("IS_EDIT_MODE", false)

        setContent {
            var firstName       by remember { mutableStateOf("") }
            var lastName        by remember { mutableStateOf("") }
            var age             by remember { mutableStateOf("") }
            var driverNumber    by remember { mutableStateOf("") }
            var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
            var selectedTeam    by remember { mutableStateOf(TeamProvider.teams.first()) }

            var pitStopRank   by remember { mutableStateOf("—") }
            var reactionRank  by remember { mutableStateOf("—") }
            var steeringRank  by remember { mutableStateOf("—") }
            var quizRank      by remember { mutableStateOf("—") }

            val userId    = auth.currentUser?.uid
            val userEmail = auth.currentUser?.email

            LaunchedEffect(userId, userEmail) {
                if (userId != null && isEditMode) {
                    db.collection("users").document(userId).get()
                        .addOnSuccessListener { doc ->
                            if (doc != null && doc.exists()) {
                                firstName    = doc.getString("firstName") ?: ""
                                lastName     = doc.getString("lastName") ?: ""
                                age          = doc.getString("age") ?: ""
                                driverNumber = doc.getString("driverNumber") ?: ""
                                val imgStr   = doc.getString("profileImageUri") ?: ""
                                if (imgStr.isNotEmpty()) selectedImageUri = Uri.parse(imgStr)
                                val teamId   = doc.getString("teamId") ?: "1"
                                selectedTeam = TeamProvider.teams.find { it.id == teamId }
                                    ?: TeamProvider.teams.first()
                            }
                        }
                }

                if (userEmail != null && isEditMode) {
                    db.collection("clasament_pitstop")
                        .orderBy("timp", Query.Direction.ASCENDING).get()
                        .addOnSuccessListener { r ->
                            val i = r.documents.indexOfFirst { it.id == userEmail }
                            pitStopRank = if (i != -1) "#${i + 1}" else "Fără scor"
                        }
                    db.collection("clasament")
                        .orderBy("timp", Query.Direction.ASCENDING).get()
                        .addOnSuccessListener { r ->
                            val i = r.documents.indexOfFirst { it.id == userEmail }
                            reactionRank = if (i != -1) "#${i + 1}" else "Fără scor"
                        }
                    db.collection("clasament_volan")
                        .orderBy("scor", Query.Direction.DESCENDING).get()
                        .addOnSuccessListener { r ->
                            val i = r.documents.indexOfFirst { it.id == userEmail }
                            steeringRank = if (i != -1) "#${i + 1}" else "Fără scor"
                        }
                    db.collection("clasament_quiz").get()
                        .addOnSuccessListener { r ->
                            val sorted = r.documents
                                .sortedWith(
                                    compareByDescending<com.google.firebase.firestore.DocumentSnapshot> {
                                        it.getLong("scorCorect") ?: 0L
                                    }.thenBy { it.getLong("timpTotal") ?: Long.MAX_VALUE }
                                )
                            val i = sorted.indexOfFirst { it.id == userEmail }
                            quizRank = if (i != -1) "#${i + 1}" else "Fără scor"
                        }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().background(F1Dark)
            ) {
                ProfileContent(
                    isEditMode       = isEditMode,
                    firstName        = firstName,
                    onFirstNameChange = { firstName = it },
                    lastName         = lastName,
                    onLastNameChange = { lastName = it },
                    age              = age,
                    onAgeChange      = { age = it },
                    driverNumber     = driverNumber,
                    onDriverNumberChange = { driverNumber = it },
                    selectedImageUri = selectedImageUri,
                    onImageUriChange = { selectedImageUri = it },
                    selectedTeam     = selectedTeam,
                    onTeamChange     = { selectedTeam = it },
                    pitStopRank      = pitStopRank,
                    reactionRank     = reactionRank,
                    steeringRank     = steeringRank,
                    quizRank         = quizRank,
                    onBack = {
                        if (!isEditMode) {
                            startActivity(android.content.Intent(this@ProfileSetupActivity, DashboardActivity::class.java))
                        }
                        finish()
                    },
                    onSave           = { fName, lName, vArsta, nrPilot, tId, imgUri ->
                        saveProfile(fName, lName, vArsta, nrPilot, tId, imgUri, isEditMode)
                    }
                )
            }
        }
    }

    private fun saveProfile(
        firstName: String, lastName: String, age: String,
        driverNumber: String, teamId: String, imageUri: Uri?, isEditMode: Boolean
    ) {
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Eroare: utilizator neautentificat", Toast.LENGTH_SHORT).show()
            return
        }

        if (imageUri != null && !imageUri.toString().startsWith("https")) {
            // Upload la Cloudinary
            kotlinx.coroutines.MainScope().launch {
                try {
                    val url = uploadToCloudinary(imageUri)
                    saveToFirestore(firstName, lastName, age, driverNumber,
                        teamId, url, isEditMode, userId)
                } catch (e: Exception) {
                    Toast.makeText(this@ProfileSetupActivity,
                        "Eroare upload poză: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            saveToFirestore(firstName, lastName, age, driverNumber,
                teamId, imageUri?.toString() ?: "", isEditMode, userId)
        }
    }

    private suspend fun uploadToCloudinary(imageUri: Uri): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val cloudName = "deyj11t0d"
            val uploadPreset = "F1 Hub"

            val inputStream = contentResolver.openInputStream(imageUri)
                ?: throw Exception("Nu s-a putut deschide imaginea")
            val bytes = inputStream.readBytes()
            inputStream.close()

            val requestBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("upload_preset", uploadPreset)
                .addFormDataPart(
                    "file", "profile.jpg",
                    okhttp3.RequestBody.create("image/jpeg".toMediaType(), bytes)
                )
                .build()

            val request = okhttp3.Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(requestBody)
                .build()

            val client = okhttp3.OkHttpClient()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw Exception("Răspuns gol de la Cloudinary")

            val json = org.json.JSONObject(responseBody)
            json.getString("secure_url")
        }
    }

    private fun saveToFirestore(
        firstName: String, lastName: String, age: String,
        driverNumber: String, teamId: String, imageUrl: String,
        isEditMode: Boolean, userId: String
    ) {
        val profileData = hashMapOf(
            "firstName"         to firstName,
            "lastName"          to lastName,
            "age"               to age,
            "driverNumber"      to driverNumber,
            "teamId"            to teamId,
            "profileImageUri"   to imageUrl,
            "isProfileComplete" to true
        )
        db.collection("users").document(userId).set(profileData)
            .addOnSuccessListener {
                if (isEditMode) {
                    Toast.makeText(this, "✅ Profil actualizat!", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "🏎️ Bun venit în F1 HUB!", Toast.LENGTH_SHORT).show()
                    startActivity(android.content.Intent(this, DashboardActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Eroare: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

// ═══════════════════════════════════════════════════════════════
// CONTINUT PRINCIPAL
// ═══════════════════════════════════════════════════════════════

@Composable
fun ProfileContent(
    isEditMode:          Boolean,
    firstName:           String, onFirstNameChange:    (String) -> Unit,
    lastName:            String, onLastNameChange:     (String) -> Unit,
    age:                 String, onAgeChange:          (String) -> Unit,
    driverNumber:        String, onDriverNumberChange: (String) -> Unit,
    selectedImageUri:    Uri?,   onImageUriChange:     (Uri?)   -> Unit,
    selectedTeam:        TeamConfig, onTeamChange:     (TeamConfig) -> Unit,
    pitStopRank:         String,
    reactionRank:        String,
    steeringRank:        String,
    quizRank:            String,
    onBack:              () -> Unit,
    onSave:              (String, String, String, String, String, Uri?) -> Unit
) {
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: Exception) { e.printStackTrace() }
        }
        onImageUriChange(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Speed line ────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth().height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, F1Red, Color.Transparent)
                    )
                )
        )

        Spacer(Modifier.height(16.dp))

        // ── Header ────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(F1Card, RoundedCornerShape(8.dp))
                    .border(1.dp, F1Border, RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("← ÎNAPOI", color = F1Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                if (isEditMode) "PROFIL PILOT" else "PROFIL NOU",
                color      = F1Red,
                fontSize   = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.weight(1f),
                textAlign  = TextAlign.Center
            )

            // Spacer simetric
            Box(modifier = Modifier.width(80.dp))
        }

        Spacer(Modifier.height(20.dp))

        // ── DRIVER CARD ───────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(selectedTeam.primaryColor.copy(alpha = 0.85f), Color(0xFF080810))
                    )
                )
                .border(2.dp, selectedTeam.primaryColor.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            // Foto pilot
            Box(
                modifier = Modifier
                    .size(95.dp)
                    .align(Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(F1Card)
                    .border(3.dp, selectedTeam.primaryColor, CircleShape)
                    .clickable { photoPickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(
                        model         = ImageRequest.Builder(LocalContext.current)
                            .data(selectedImageUri).crossfade(true).build(),
                        contentDescription = "Poză profil",
                        modifier      = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale  = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 24.sp)
                        Text("FOTO", color = F1Muted, fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }

            // Info pilot
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    firstName.ifEmpty { "PRENUME" }.uppercase(),
                    color = F1Text.copy(alpha = if (firstName.isEmpty()) 0.3f else 1f),
                    fontSize = 14.sp, fontFamily = FontFamily.Monospace
                )
                Text(
                    lastName.ifEmpty { "NUME" }.uppercase(),
                    color = if (lastName.isEmpty()) F1Text.copy(alpha = 0.3f) else Color.White,
                    fontSize = 20.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    selectedTeam.name.uppercase(),
                    color = selectedTeam.primaryColor,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                )
            }

            // Numar pilot
            if (driverNumber.isNotEmpty()) {
                Text(
                    driverNumber,
                    color = Color.White.copy(alpha = 0.15f),
                    fontSize = 72.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp)
                )
            }
        }

        // ── CLASAMENTE (doar in Edit Mode) ────────────────────
        if (isEditMode) {
            Spacer(Modifier.height(20.dp))

            SectionLabel("CLASAMENTE MINIJOCURI")

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RankCard("🚦 Reaction",  reactionRank,  Modifier.weight(1f))
                RankCard("🔧 Pit Stop",  pitStopRank,   Modifier.weight(1f))
                RankCard("🏎️ Steering",  steeringRank,  Modifier.weight(1f))
                RankCard("🏁 Quiz",      quizRank,       Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── DATE PERSONALE ────────────────────────────────────
        SectionLabel("DATE PERSONALE")
        Spacer(Modifier.height(10.dp))

        // Prenume + Nume
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileTextField(
                value    = firstName,
                onChange = onFirstNameChange,
                label    = "PRENUME",
                modifier = Modifier.weight(1f)
            )
            ProfileTextField(
                value    = lastName,
                onChange = onLastNameChange,
                label    = "NUME",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        // Varsta + Numar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileTextField(
                value       = age,
                onChange    = { if (it.length <= 2) onAgeChange(it) },
                label       = "VÂRSTĂ",
                keyboardType = KeyboardType.Number,
                modifier    = Modifier.weight(1f)
            )
            ProfileTextField(
                value       = driverNumber,
                onChange    = { if (it.length <= 2) onDriverNumberChange(it) },
                label       = "NUMĂR PILOT",
                keyboardType = KeyboardType.Number,
                modifier    = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── ECHIPA ────────────────────────────────────────────
        SectionLabel("ECHIPA PREFERATĂ")
        Spacer(Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(TeamProvider.teams) { team ->
                val isSelected = team.id == selectedTeam.id
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(F1Card)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) team.primaryColor else F1Border,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onTeamChange(team) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        // Pata de culoare echipa
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(team.primaryColor)
                        )
                        Text(
                            team.name,
                            color = if (isSelected) F1Text else F1Muted,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── BUTON SALVARE ─────────────────────────────────────
        Button(
            onClick = {
                if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                    onSave(firstName, lastName, age, driverNumber, selectedTeam.id, selectedImageUri)
                } else {
                    Toast.makeText(context, "Te rog completează prenumele și numele!", Toast.LENGTH_SHORT).show()
                }
            },
            colors   = ButtonDefaults.buttonColors(containerColor = F1Red),
            shape    = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(58.dp)
        ) {
            Text(
                if (isEditMode) "💾  SALVEAZĂ MODIFICĂRILE" else "✅  FINALIZEAZĂ PROFILUL",
                color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, letterSpacing = 1.sp
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
// COMPONENTE HELPER
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text,
            color = F1Muted, fontSize = 9.sp, letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier.weight(1f).height(1.dp).background(F1Border)
        )
    }
}

@Composable
private fun RankCard(label: String, rank: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(F1Card, RoundedCornerShape(10.dp))
            .border(1.dp, F1Border, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = F1Muted, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(
            rank,
            color = if (rank.startsWith("#")) F1Gold else F1Muted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ProfileTextField(
    value:        String,
    onChange:     (String) -> Unit,
    label:        String,
    modifier:     Modifier      = Modifier,
    keyboardType: KeyboardType  = KeyboardType.Text
) {
    Column(modifier = modifier) {
        Text(
            label, color = F1Muted, fontSize = 9.sp,
            letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value       = value,
            onValueChange = onChange,
            textStyle   = TextStyle(color = F1Text, fontSize = 14.sp),
            singleLine  = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors      = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = F1Red,
                unfocusedBorderColor = F1Border,
                focusedTextColor     = F1Text,
                unfocusedTextColor   = F1Text,
                cursorColor          = F1Red,
                focusedContainerColor   = F1Card,
                unfocusedContainerColor = F1Card,
            ),
            shape    = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        )
    }
}