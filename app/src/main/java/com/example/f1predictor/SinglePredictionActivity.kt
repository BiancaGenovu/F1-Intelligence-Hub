package com.example.f1predictor

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.f1predictor.databinding.ActivitySinglePredictionBinding
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class SinglePredictionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySinglePredictionBinding

    private val BASE_URL = "http://192.168.1.160:8001/"

    private val PROMPT_PILOT   = "Alege un pilot..."
    private val PROMPT_CIRCUIT = "Alege un circuit..."
    private val PROMPT_VREME   = "Alege vremea..."

    private val listaPiloti = listOf(PROMPT_PILOT) + listOf(
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

    private val listaVreme = listOf(PROMPT_VREME, "Vreme Uscată", "Ploaie")

    private var currentCommentary: String = ""
    private var currentProvider:   String = "ollama"

    // Retinem selectiile pentru a le afisa in result card
    private var pilotCurent   = ""
    private var circuitCurent = ""
    private var vremeCurenta  = ""
    private var gridCurent    = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySinglePredictionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupSpinners()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(240, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)
            .writeTimeout(240, TimeUnit.SECONDS)
            .build()

        val apiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(F1ApiService::class.java)

        binding.btnAnalyze.setOnClickListener {
            val pilotSelectat   = binding.spinnerPiloti.selectedItem.toString()
            val circuitSelectat = binding.spinnerCircuite.selectedItem.toString()
            val vremeSelectata  = binding.spinnerVreme.selectedItem.toString()
            val gridInput       = binding.etStartingGrid.text.toString()

            if (pilotSelectat == PROMPT_PILOT) {
                Toast.makeText(this, "Te rog selectează un pilot!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (circuitSelectat == PROMPT_CIRCUIT) {
                Toast.makeText(this, "Te rog selectează un circuit!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (vremeSelectata == PROMPT_VREME) {
                Toast.makeText(this, "Te rog selectează vremea!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (gridInput.isEmpty()) {
                Toast.makeText(this, "Te rog introdu poziția de start!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val gridPosition = gridInput.toInt()
            if (gridPosition !in 1..20) {
                Toast.makeText(this, "Poziția pe grilă trebuie să fie între 1 și 20!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            pilotCurent   = pilotSelectat
            circuitCurent = circuitSelectat
            vremeCurenta  = vremeSelectata
            gridCurent    = gridPosition

            val llmProvider = if (binding.radioGemini.isChecked) "gemini" else "ollama"
            simulateSinglePilot(apiService, pilotSelectat, circuitSelectat, vremeSelectata, gridPosition, llmProvider)
        }

        binding.btnShowCommentary.setOnClickListener {
            showCommentaryDialog()
        }
    }

    private fun setupSpinners() {
        fun createAdapter(items: List<String>): ArrayAdapter<String> {
            return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(if (position == 0) Color.GRAY else Color.WHITE)
                    return view
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent) as TextView
                    view.setBackgroundColor(Color.parseColor("#1A1A1A"))
                    view.setPadding(40, 40, 40, 40)
                    view.setTextColor(if (position == 0) Color.GRAY else Color.WHITE)
                    return view
                }
            }
        }

        binding.spinnerPiloti.adapter   = createAdapter(listaPiloti)
        binding.spinnerCircuite.adapter = createAdapter(listaCircuite)
        binding.spinnerVreme.adapter    = createAdapter(listaVreme)
    }

    private fun simulateSinglePilot(
        apiService:      F1ApiService,
        pilotSelectat:   String,
        circuitSelectat: String,
        vremeSelectata:  String,
        gridPosition:    Int,
        llmProvider:     String
    ) {
        val ploua = if (vremeSelectata == "Ploaie") 1 else 0

        // Loading state
        binding.layoutResult.visibility        = View.GONE
        binding.tvResult.visibility            = View.VISIBLE
        binding.tvResult.text                  = if (llmProvider == "gemini")
            "Se calculează predicția XGBoost..."
        else
            "Se contactează serverul XGBoost și LLaMA 3...\nAcest proces poate dura până la un minut."
        binding.btnAnalyze.isEnabled           = false
        binding.btnShowCommentary.visibility   = View.GONE
        binding.progressBar.visibility         = View.VISIBLE
        currentCommentary                      = ""
        currentProvider                        = llmProvider

        lifecycleScope.launch {
            try {
                val cerere = SinglePredictionRequest(
                    Nume_Pilot           = pilotSelectat,
                    Starting_Grid        = gridPosition,
                    Nume_Circuit         = circuitSelectat,
                    Vreme_Cursa_1_ploaie = ploua,
                    llm_provider         = llmProvider
                )

                val raspuns = apiService.predictSingle(cerere)
                val pozitie = raspuns.pozitie_estimata
                val scor    = raspuns.scor_brut
                afiseazaResultCard(pozitie, scor, pilotSelectat, circuitSelectat, vremeSelectata, gridPosition)

                if (llmProvider == "gemini" && !raspuns.prompt_pentru_gemini.isNullOrEmpty()) {
                    binding.tvResult.text    = "✨ Se generează comentariul Gemini..."
                    binding.tvResult.visibility = View.VISIBLE
                    currentCommentary = GeminiHelper.genereazaComentariu(raspuns.prompt_pentru_gemini)
                } else {
                    currentCommentary = raspuns.analiza_ai ?: ""
                }

                binding.tvResult.visibility        = View.GONE
                binding.btnShowCommentary.visibility = View.VISIBLE

            } catch (e: Exception) {
                binding.layoutResult.visibility = View.GONE
                binding.tvResult.visibility     = View.VISIBLE
                binding.tvResult.text = "Eroare la simulare. Verifică dacă serverul Python rulează.\nDetalii: ${e.localizedMessage}"
            } finally {
                binding.btnAnalyze.isEnabled   = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun afiseazaResultCard(
        pozitie:    Int,
        scor:       Double,
        pilot:      String,
        circuit:    String,
        vreme:      String,
        grid:       Int
    ) {
        // Pozitie + sufix
        val suffix = when {
            pozitie % 10 == 1 && pozitie % 100 != 11 -> "ST"
            pozitie % 10 == 2 && pozitie % 100 != 12 -> "ND"
            pozitie % 10 == 3 && pozitie % 100 != 13 -> "RD"
            else -> "TH"
        }
        binding.tvPositionNum.text    = pozitie.toString()
        binding.tvPositionSuffix.text = suffix

        // Pilot name uppercase
        binding.tvResultPilotName.text = pilot.uppercase()

        // Meta: circuit · vreme · P{grid}
        val vremeScurt = if (vreme == "Vreme Uscată") "Uscată" else vreme
        binding.tvResultMeta.text = "🏁 $circuit · $vremeScurt · P$grid"

        // Bara scor — scorul e pozitia estimata bruta (1.0 - 20.0), mai mic = mai bun
        val progressVal = ((20.0 - scor) / 19.0 * 100).toInt().coerceIn(5, 100)
        binding.progressScore.progress = progressVal
        binding.tvScoreValue.text      = String.format("%.2f XGB", scor)

        // Arata cardul
        binding.layoutResult.visibility = View.VISIBLE
        binding.tvResult.visibility     = View.GONE
    }

    private fun showCommentaryDialog() {
        if (currentCommentary.isEmpty()) return

        val titlu = if (currentProvider == "gemini")
            "Comentariu Sportiv ✨ Gemini"
        else
            "Comentariu Sportiv 🤖 Ollama"

        AlertDialog.Builder(this)
            .setTitle(titlu)
            .setMessage(currentCommentary)
            .setPositiveButton("ÎNCHIDE") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}