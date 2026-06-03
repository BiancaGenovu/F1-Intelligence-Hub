package com.example.f1predictor

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.f1predictor.databinding.ActivityRaceSimulationBinding
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RaceSimulationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRaceSimulationBinding
    private val BASE_URL = "http://192.168.1.160:8001/"

    private var currentClasament: List<PilotClasat>? = null
    private var currentRezumat:   String = ""
    private var currentGifUrl:    String = ""
    private var currentProvider:  String = "ollama"

    private val startingGridMap = mutableMapOf<String, Int>()

    private val PROMPT_CIRCUIT = "Alege un circuit..."
    private val PROMPT_VREME   = "Alege vremea..."

    private val listaPiloti = listOf(
        "Max Verstappen", "Lando Norris", "Charles Leclerc", "Oscar Piastri",
        "Carlos Sainz", "George Russell", "Lewis Hamilton", "Fernando Alonso",
        "Pierre Gasly", "Esteban Ocon", "Alexander Albon", "Yuki Tsunoda",
        "Lance Stroll", "Niko Hulkenberg", "Isack Hadjar", "Kimi Antonelli",
        "Oliver Bearman", "Jack Doohan", "Gabriel Bortoleto", "Liam Lawson"
    ).sorted()

    private val listaCircuite = listOf(PROMPT_CIRCUIT) + listOf(
        "Australia", "China", "Japan", "Bahrain", "Saudi Arabia", "Miami",
        "Emilia-Romagna", "Monaco", "Spain", "Canada", "Austria", "Great Britain",
        "Belgium", "Hungary", "Netherlands", "Italy", "Azerbaijan", "Singapore",
        "United States", "Mexico", "Brazil", "Las Vegas", "Qatar", "Abu Dhabi"
    ).sorted()

    private val listaVreme    = listOf(PROMPT_VREME, "Vreme Uscată", "Ploaie")
    private val gridInputList = mutableListOf<Pair<String, EditText>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRaceSimulationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupSpinners()
        genereazaListaPilotiGrid()

        binding.btnClearGrid.setOnClickListener {
            for (pair in gridInputList) pair.second.text.clear()
        }

        binding.btnRandomGrid.setOnClickListener {
            val pozitii = (1..20).shuffled()
            for ((index, pair) in gridInputList.withIndex()) {
                pair.second.setText(pozitii[index].toString())
            }
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1000, TimeUnit.SECONDS)
            .readTimeout(1000, TimeUnit.SECONDS)
            .writeTimeout(1000, TimeUnit.SECONDS)
            .build()

        val apiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(F1ApiService::class.java)

        binding.btnSimulateRace.setOnClickListener { lanseazaSimularea(apiService) }
        binding.btnShowTable.setOnClickListener      { arataDialogClasament() }
        binding.btnShowGif.setOnClickListener        { verificaSiDeschideGif() }
        binding.btnShowCommentary.setOnClickListener { arataDialogComentariu() }
    }

    private fun lanseazaSimularea(apiService: F1ApiService) {
        val circuitSelectat = binding.spinnerCircuiteGrid.selectedItem.toString()
        val vremeSelectata  = binding.spinnerVremeGrid.selectedItem.toString()

        if (circuitSelectat == PROMPT_CIRCUIT || vremeSelectata == PROMPT_VREME) {
            Toast.makeText(this, "Selectează circuitul și vremea!", Toast.LENGTH_SHORT).show(); return
        }

        val ploua = if (vremeSelectata == "Ploaie") 1 else 0
        val llmProvider = if (binding.radioGemini.isChecked) "gemini" else "ollama"

        val listaPentruServer = mutableListOf<SinglePredictionRequest>()
        val pozitiiFolosite = mutableSetOf<Int>()
        startingGridMap.clear()

        for (pair in gridInputList) {
            val nume = pair.first
            val gridStr = pair.second.text.toString()
            if (gridStr.isEmpty()) { Toast.makeText(this, "Completează grila pentru $nume!", Toast.LENGTH_SHORT).show(); return }
            val gridInt = gridStr.toInt()
            if (gridInt !in 1..20 || !pozitiiFolosite.add(gridInt)) { Toast.makeText(this, "Grilă invalidă!", Toast.LENGTH_SHORT).show(); return }
            startingGridMap[nume] = gridInt
            listaPentruServer.add(SinglePredictionRequest(nume, gridInt, circuitSelectat, ploua, llmProvider))
        }

        binding.btnSimulateRace.isEnabled = false
        binding.btnSimulateRace.text = "SE CALCULEAZĂ..."
        binding.progressRace.visibility = View.VISIBLE
        binding.containerResultsButtons.visibility = View.GONE
        currentProvider = llmProvider

        lifecycleScope.launch {
            try {
                val raspuns = apiService.simulateFullRace(listaPentruServer)
                currentClasament = raspuns.clasament
                currentGifUrl = BASE_URL.removeSuffix("/") + raspuns.animatie_url

                // Podium
                if (raspuns.clasament.size >= 3) {
                    binding.tvPodium1Name.text = raspuns.clasament[0].Nume_Pilot.split(" ").last().uppercase()
                    binding.tvPodium1Team.text = getEchipaPilot(raspuns.clasament[0].Nume_Pilot)
                    binding.tvPodium2Name.text = raspuns.clasament[1].Nume_Pilot.split(" ").last().uppercase()
                    binding.tvPodium2Team.text = getEchipaPilot(raspuns.clasament[1].Nume_Pilot)
                    binding.tvPodium3Name.text = raspuns.clasament[2].Nume_Pilot.split(" ").last().uppercase()
                    binding.tvPodium3Team.text = getEchipaPilot(raspuns.clasament[2].Nume_Pilot)
                }

                currentRezumat = if (llmProvider == "gemini") GeminiHelper.genereazaComentariu(raspuns.prompt_pentru_gemini ?: "") else raspuns.rezumat_cursa
                binding.containerResultsButtons.visibility = View.VISIBLE
                Toast.makeText(this@RaceSimulationActivity, "✅ Simulare gata!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@RaceSimulationActivity, "Eroare: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnSimulateRace.isEnabled = true
                binding.btnSimulateRace.text = "🏁 START SIMULARE NOUĂ"
                binding.progressRace.visibility = View.GONE
            }
        }
    }

    private fun verificaSiDeschideGif() {
        val client = OkHttpClient()
        val request = okhttp3.Request.Builder().url("${BASE_URL}check_animation_ready").build()
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                runOnUiThread { Toast.makeText(this@RaceSimulationActivity, "Eroare server!", Toast.LENGTH_SHORT).show() }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val isReady = response.body?.string()?.contains("\"ready\":true") == true
                runOnUiThread { if (isReady) arataDialogGif() else Toast.makeText(this@RaceSimulationActivity, "Se procesează, " +
                        "mai așteaptă puțin...", Toast.LENGTH_SHORT).show() }
            }
        })
    }

    private fun arataDialogGif() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val rootLayout = RelativeLayout(this).apply { setBackgroundColor(Color.parseColor("#080810")) }

        val btnClose = androidx.appcompat.widget.AppCompatButton(this).apply {
            id = View.generateViewId()
            text = "ÎNCHIDE ANIMAȚIA"
            setBackgroundColor(Color.parseColor("#E10600"))
            setTextColor(Color.WHITE)
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (55 * resources.displayMetrics.density).toInt()).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins(40, 40, 40, 40)
            }
            setOnClickListener { dialog.dismiss() }
        }
        val imageView = ImageView(this).apply {
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT).apply { addRule(RelativeLayout.ABOVE, btnClose.id) }
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        rootLayout.addView(imageView); rootLayout.addView(btnClose)
        dialog.setContentView(rootLayout)

        Glide.with(this).load(currentGifUrl)
            .error(Glide.with(this).load(currentGifUrl).skipMemoryCache(true).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
            .into(imageView)
        dialog.show()
    }

    // ─────────────────────────────────────────────────────────────
    // DIALOG COMENTARIU
    // ─────────────────────────────────────────────────────────────

    private fun arataDialogComentariu() {
        if (currentRezumat.isEmpty()) return
        val titlu = if (currentProvider == "gemini") "Comentariu Sportiv ✨ Gemini" else "Comentariu Sportiv 🤖 Ollama"
        AlertDialog.Builder(this)
            .setTitle(titlu)
            .setMessage(currentRezumat)
            .setPositiveButton("ÎNCHIDE") { d, _ -> d.dismiss() }
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // DIALOG CLASAMENT
    // ─────────────────────────────────────────────────────────────

    private fun arataDialogClasament() {
        val clasament = currentClasament ?: return
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(Color.parseColor("#15151E"))
        }

        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, 20) }
        headerRow.addView(headerTv("POS", 0.10f))
        headerRow.addView(headerTv("PILOT/ECHIPĂ", 0.40f))
        headerRow.addView(headerTv("GRID", 0.15f, Gravity.CENTER))
        headerRow.addView(headerTv("+/-", 0.15f, Gravity.CENTER))
        headerRow.addView(headerTv("PTS", 0.20f, Gravity.CENTER))
        mainLayout.addView(headerRow)
        mainLayout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).also { it.bottomMargin = 20 }
            setBackgroundColor(Color.parseColor("#38383F"))
        })

        for (pilot in clasament) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 15, 0, 15)
            }

            row.addView(TextView(this).apply {
                text = pilot.loc_final.toString(); setTextColor(Color.WHITE); textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.10f)
            })

            val driverTeam = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.40f)
            }
            driverTeam.addView(TextView(this).apply {
                text = pilot.Nume_Pilot; setTextColor(Color.WHITE); textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            driverTeam.addView(TextView(this).apply {
                text = getEchipaPilot(pilot.Nume_Pilot).uppercase()
                setTextColor(Color.parseColor("#8A8A8A")); textSize = 10f
            })
            row.addView(driverTeam)

            val startPos = startingGridMap[pilot.Nume_Pilot] ?: 0
            row.addView(TextView(this).apply {
                text = startPos.toString(); setTextColor(Color.parseColor("#8A8A8A")); textSize = 14f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.15f)
            })

            val diff = startPos - pilot.loc_final
            row.addView(TextView(this).apply {
                text = if (diff > 0) "+$diff" else if (diff < 0) "$diff" else "-"
                setTextColor(if (diff > 0) Color.parseColor("#00D2BE") else if (diff < 0) Color.parseColor("#E10600") else Color.GRAY)
                textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.15f)
            })

            val puncte = getPuncte(pilot.loc_final)
            row.addView(TextView(this).apply {
                text = puncte.toString()
                setTextColor(if (puncte > 0) Color.WHITE else Color.GRAY)
                textSize = 15f; setTypeface(null, android.graphics.Typeface.BOLD); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.20f)
            })

            mainLayout.addView(row)
            mainLayout.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(Color.parseColor("#2A2A32"))
            })
        }

        scrollView.addView(mainLayout)
        AlertDialog.Builder(this).setView(scrollView)
            .setPositiveButton("ÎNCHIDE") { d, _ -> d.dismiss() }.show()
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private fun headerTv(text: String, weight: Float, grav: Int = Gravity.START) =
        TextView(this).apply {
            this.text = text; setTextColor(Color.GRAY); textSize = 11f; gravity = grav
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
        }

    private fun getPuncte(pozitie: Int) = when (pozitie) {
        1 -> 25; 2 -> 18; 3 -> 15; 4 -> 12; 5 -> 10
        6 -> 8;  7 -> 6;  8 -> 4;  9 -> 2;  10 -> 1
        else -> 0
    }

    private fun getEchipaPilot(nume: String) = when (nume) {
        "Max Verstappen", "Yuki Tsunoda"        -> "Red Bull"
        "Lando Norris", "Oscar Piastri"          -> "McLaren"
        "Charles Leclerc", "Lewis Hamilton"      -> "Ferrari"
        "George Russell", "Kimi Antonelli"       -> "Mercedes"
        "Fernando Alonso", "Lance Stroll"        -> "Aston Martin"
        "Pierre Gasly", "Jack Doohan"            -> "Alpine"
        "Alexander Albon", "Carlos Sainz"        -> "Williams"
        "Liam Lawson", "Isack Hadjar"            -> "RB"
        "Esteban Ocon", "Oliver Bearman"         -> "Haas"
        "Niko Hulkenberg", "Gabriel Bortoleto"   -> "Kick Sauber"
        else                                     -> ""
    }

    private fun setupSpinners() {
        fun createAdapter(items: List<String>) =
            object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(if (position == 0) Color.GRAY else Color.WHITE); return view
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent) as TextView
                    view.setBackgroundColor(Color.parseColor("#1A1A1A")); view.setPadding(40, 40, 40, 40)
                    view.setTextColor(if (position == 0) Color.GRAY else Color.WHITE); return view
                }
            }
        binding.spinnerCircuiteGrid.adapter = createAdapter(listaCircuite)
        binding.spinnerVremeGrid.adapter    = createAdapter(listaVreme)
    }

    private fun genereazaListaPilotiGrid() {
        for (pilot in listaPiloti) {
            val row = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setPadding(0, 10, 0, 10); gravity = Gravity.CENTER_VERTICAL
            }
            val tvNume = TextView(this).apply {
                text = pilot; setTextColor(Color.WHITE); textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val etGrid = EditText(this).apply {
                hint = "1-20"; setHintTextColor(Color.parseColor("#30304A")); setTextColor(Color.WHITE)
                inputType = InputType.TYPE_CLASS_NUMBER; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(130, LinearLayout.LayoutParams.WRAP_CONTENT)
                setBackgroundResource(0)
                background = resources.getDrawable(R.drawable.bg_input_login, null)
            }
            row.addView(tvNume); row.addView(etGrid)
            binding.containerPiloti.addView(row)
            gridInputList.add(Pair(pilot, etGrid))
        }
    }
}