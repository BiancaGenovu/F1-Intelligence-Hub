package com.example.f1predictor

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.f1predictor.databinding.ActivityDuelBinding
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

class DuelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDuelBinding
    private val BASE_URL = "http://192.168.1.160:8001/"

    private val PROMPT_CIRCUIT = "Alege Circuitul..."
    private val PROMPT_YOU     = "Selectează Pilotul Tău..."
    private val PROMPT_RIVAL   = "Selectează Rivalul..."

    private val listaPiloti = listOf(PROMPT_YOU, PROMPT_RIVAL) + listOf(
        "Max Verstappen", "Lando Norris", "Charles Leclerc", "Oscar Piastri",
        "Carlos Sainz", "George Russell", "Lewis Hamilton", "Fernando Alonso",
        "Pierre Gasly", "Esteban Ocon", "Alexander Albon", "Yuki Tsunoda",
        "Kimi Antonelli", "Liam Lawson", "Isack Hadjar", "Gabriel Bortoleto",
        "Lance Stroll", "Jack Doohan", "Oliver Bearman", "Nico Hulkenberg"
    ).sorted()

    private val listaCircuite = listOf(PROMPT_CIRCUIT) + listOf(
        "Australia", "China", "Japan", "Bahrain", "Saudi Arabia", "Miami",
        "Emilia-Romagna", "Monaco", "Spain", "Canada", "Austria", "Great Britain",
        "Belgium", "Hungary", "Netherlands", "Italy", "Azerbaijan", "Singapore",
        "United States", "Mexico", "Brazil", "Las Vegas", "Qatar", "Abu Dhabi"
    ).sorted()

    private var currentInsights: String = ""
    private var currentProvider: String = "ollama"   // NOU

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDuelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupSpinners()

        val apiService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(F1ApiService::class.java)

        binding.btnCompare.setOnClickListener { runComparison(apiService) }

        binding.btnShowCommentary.setOnClickListener { showCommentaryDialog() }
    }

    // ─────────────────────────────────────────────────────────────
    // COMPARATIE PRINCIPALA
    // ─────────────────────────────────────────────────────────────

    private fun runComparison(apiService: F1ApiService) {
        val circuit = binding.spinnerCircuit.selectedItem.toString()
        val you     = binding.spinnerYou.selectedItem.toString()
        val rival   = binding.spinnerRival.selectedItem.toString()

        if (circuit == PROMPT_CIRCUIT || you == PROMPT_YOU || rival == PROMPT_RIVAL) {
            Toast.makeText(this, "Te rog selectează toate opțiunile!", Toast.LENGTH_SHORT).show()
            return
        }
        if (you == rival) {
            Toast.makeText(this, "Nu te poți duela cu tine însuți!", Toast.LENGTH_SHORT).show()
            return
        }

        val llmProvider = if (binding.radioGemini.isChecked) "gemini" else "ollama"   // NOU

        binding.progressBar.visibility      = View.VISIBLE
        binding.resultsContainer.visibility = View.GONE
        binding.btnCompare.isEnabled        = false
        currentProvider                     = llmProvider

        lifecycleScope.launch {
            try {
                val req  = DuelRequest(
                    pilot_tu    = you,
                    pilot_rival = rival,
                    circuit     = circuit,
                    llm_provider = llmProvider   // NOU
                )
                val resp = apiService.duelPilots(req)
                if (resp.error == true) {
                    Toast.makeText(this@DuelActivity, resp.message ?: "Eroare necunoscută",
                        Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Afisam graficele imediat
                displayResults(resp)

                if (llmProvider == "gemini" && !resp.prompt_pentru_gemini.isNullOrEmpty()) {
                    // Gemini ruleaza pe telefon dupa ce graficele sunt deja vizibile
                    currentInsights = GeminiHelper.genereazaComentariu(resp.prompt_pentru_gemini)
                } else {
                    // Ollama — insight-ul vine direct din raspuns
                    currentInsights = resp.insights
                }

            } catch (e: Exception) {
                Toast.makeText(this@DuelActivity, "Eroare: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnCompare.isEnabled   = true
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // AFISARE REZULTATE
    // ─────────────────────────────────────────────────────────────

    private fun displayResults(data: DuelResponse) {
        binding.resultsContainer.visibility = View.VISIBLE

        val diffSec = abs(data.timeDiff) / 1000.0
        val pilot1Nume = binding.spinnerYou.selectedItem.toString()
        val pilot2Nume = binding.spinnerRival.selectedItem.toString()

        when (data.winner) {
            "pilot1" -> {
                binding.tvWinnerBanner.text = "🏆 $pilot1Nume a fost mai rapid cu ${String.format("%.3f", diffSec)}s!"
                binding.tvWinnerBanner.setBackgroundColor(Color.parseColor("#2E7D32"))
            }
            "pilot2" -> {
                binding.tvWinnerBanner.text = "🏆 $pilot2Nume a fost mai rapid cu ${String.format("%.3f", diffSec)}s."
                binding.tvWinnerBanner.setBackgroundColor(Color.parseColor("#C62828"))
            }
            else -> {
                binding.tvWinnerBanner.text = "🤝 EGALITATE PERFECTĂ!"
                binding.tvWinnerBanner.setBackgroundColor(Color.parseColor("#F9A825"))
            }
        }

        data.sectorComparison?.let { sc ->
            drawBar(sc.sector1, binding.barYouS1, binding.txtYouS1, binding.barRivalS1, binding.txtRivalS1, binding.diffS1)
            drawBar(sc.sector2, binding.barYouS2, binding.txtYouS2, binding.barRivalS2, binding.txtRivalS2, binding.diffS2)
            drawBar(sc.sector3, binding.barYouS3, binding.txtYouS3, binding.barRivalS3, binding.txtRivalS3, binding.diffS3)
        }
    }

    private fun drawBar(
        sector:    SectorDiff,
        barYou:    View,     txtYou:   TextView,
        barRival:  View,     txtRival: TextView,
        txtDiff:   TextView
    ) {
        val maxTime = max(sector.pilot1, sector.pilot2).toFloat()

        val weightYou   = (sector.pilot1 / maxTime) * 90f
        val weightRival = (sector.pilot2 / maxTime) * 90f

        (barYou.layoutParams   as LinearLayout.LayoutParams).weight = weightYou
        (barRival.layoutParams as LinearLayout.LayoutParams).weight = weightRival
        barYou.requestLayout()
        barRival.requestLayout()

        txtYou.text   = formatMs(sector.pilot1)
        txtRival.text = formatMs(sector.pilot2)

        val diffSec = sector.diff / 1000.0
        if (sector.winner == "pilot1") {
            txtDiff.text = String.format("-%.3fs", abs(diffSec))
            txtDiff.setTextColor(Color.parseColor("#00D2BE"))
        } else {
            txtDiff.text = String.format("+%.3fs", abs(diffSec))
            txtDiff.setTextColor(Color.parseColor("#E10600"))
        }
    }

    private fun formatMs(ms: Long): String {
        val seconds      = ms / 1000
        val milliseconds = ms % 1000
        return String.format("%d.%03d", seconds, milliseconds)
    }

    // ─────────────────────────────────────────────────────────────
    // DIALOG COMENTARIU
    // ─────────────────────────────────────────────────────────────

    private fun showCommentaryDialog() {
        if (currentInsights.isEmpty()) {
            Toast.makeText(this, "Comentariul se generează, mai așteaptă o secundă...", Toast.LENGTH_SHORT).show()
            return
        }

        val titlu = if (currentProvider == "gemini")
            "Analiză Telemetrică ✨ Gemini"
        else
            "Analiză Telemetrică 🤖 Ollama"

        AlertDialog.Builder(this)
            .setTitle(titlu)
            .setMessage(currentInsights)
            .setPositiveButton("ÎNCHIDE") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // SPINNERS
    // ─────────────────────────────────────────────────────────────

    private fun setupSpinners() {
        fun createAdapter(items: List<String>) =
            object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
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

        binding.spinnerCircuit.adapter = createAdapter(listaCircuite)
        binding.spinnerYou.adapter     = createAdapter(listaPiloti.filter { it != PROMPT_RIVAL })
        binding.spinnerRival.adapter   = createAdapter(listaPiloti.filter { it != PROMPT_YOU })
    }
}