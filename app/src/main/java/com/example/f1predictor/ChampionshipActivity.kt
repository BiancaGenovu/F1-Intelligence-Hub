package com.example.f1predictor

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.example.f1predictor.databinding.ActivityChampionshipBinding
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ChampionshipActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChampionshipBinding
    private val BASE_URL = "http://192.168.1.160:8001/"

    private val PROMPT_PILOT  = "Alege un pilot..."
    private val PROMPT_ECHIPA = "Alege o echipă..."

    private val listaPiloti = listOf(PROMPT_PILOT) + listOf(
        "Max Verstappen", "Lando Norris", "Charles Leclerc", "Oscar Piastri",
        "Carlos Sainz", "George Russell", "Lewis Hamilton", "Fernando Alonso",
        "Pierre Gasly", "Esteban Ocon", "Alexander Albon", "Yuki Tsunoda",
        "Kimi Antonelli", "Liam Lawson", "Isack Hadjar", "Gabriel Bortoleto",
        "Lance Stroll", "Jack Doohan", "Oliver Bearman", "Nico Hulkenberg"
    ).sorted()

    private val listaEchipe = listOf(PROMPT_ECHIPA) + listOf(
        "McLaren", "Ferrari", "Red Bull Racing", "Mercedes", "Aston Martin",
        "Alpine", "Haas", "RB", "Williams", "Kick Sauber"
    ).sorted()

    private data class CompetitorRowUI(val spinner: Spinner, val etPoints: EditText, val viewGroup: View)
    private val randuriGenerate = mutableListOf<CompetitorRowUI>()

    private var modPiloti       = true
    private var currentCommentary = ""
    private var currentMath       = ""
    private var currentProvider   = "ollama"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChampionshipBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

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

        binding.radioGroupType.setOnCheckedChangeListener { _, checkedId ->
            modPiloti = (checkedId == R.id.radioDrivers)
            reseteazaLista()
        }

        binding.btnAddCompetitor.setOnClickListener { adaugaRandCompetitor() }
        binding.btnCalculate.setOnClickListener     { calculeazaScenariile(apiService) }

        binding.btnShowCommentary.setOnClickListener {
            val titlu = if (currentProvider == "gemini") "Analiză Sportivă ✨ Gemini" else "Analiză Sportivă 🤖 Ollama"
            showDialog(titlu, currentCommentary)
        }

        binding.btnShowMath.setOnClickListener {
            showDialog("Calcule Matematice 🧮", currentMath)
        }

        reseteazaLista()
    }

    // ─────────────────────────────────────────────────────────────
    // CALCUL SCENARII
    // ─────────────────────────────────────────────────────────────

    private fun calculeazaScenariile(apiService: F1ApiService) {
        val curseStr = binding.etRacesLeft.text.toString()
        if (curseStr.isEmpty() || curseStr.toInt() < 1) {
            Toast.makeText(this, "Te rog introdu un număr valid de curse rămase!", Toast.LENGTH_SHORT).show()
            return
        }
        val curseRamase = curseStr.toInt()
        val llmProvider = if (binding.radioGemini.isChecked) "gemini" else "ollama"

        val listaCompetitori = mutableListOf<CompetitorPuncte>()
        val numeFolosite     = mutableSetOf<String>()

        for (rand in randuriGenerate) {
            val nume      = rand.spinner.selectedItem.toString()
            val puncteStr = rand.etPoints.text.toString()

            if (nume == PROMPT_PILOT || nume == PROMPT_ECHIPA) {
                Toast.makeText(this, "Te rog selectează un competitor valid!", Toast.LENGTH_SHORT).show(); return
            }
            if (puncteStr.isEmpty()) {
                Toast.makeText(this, "Completează punctele pentru $nume!", Toast.LENGTH_SHORT).show(); return
            }
            if (!numeFolosite.add(nume)) {
                Toast.makeText(this, "L-ai selectat pe $nume de mai multe ori!", Toast.LENGTH_SHORT).show(); return
            }
            listaCompetitori.add(CompetitorPuncte(nume, puncteStr.toInt()))
        }

        binding.btnCalculate.isEnabled          = false
        binding.btnCalculate.text               = "SE CALCULEAZĂ..."
        binding.progressChampionship.visibility = View.VISIBLE
        binding.tvResult.visibility             = View.GONE
        binding.btnShowCommentary.visibility    = View.GONE
        binding.btnShowMath.visibility          = View.GONE
        currentProvider                         = llmProvider

        lifecycleScope.launch {
            try {
                val request = ChampionshipRequest(
                    curse_ramase = curseRamase,
                    llm_provider = llmProvider,
                    piloti       = if (modPiloti) listaCompetitori else null,
                    echipe       = if (!modPiloti) listaCompetitori else null
                )

                val response = if (modPiloti)
                    apiService.calculateDriversChampionship(request)
                else
                    apiService.calculateConstructorsChampionship(request)

                currentMath = if (modPiloti)
                    response.scenarii_matematice ?: ""
                else
                    response.scenarii_matematice ?: response.rezumat_scenarii ?: ""

                if (llmProvider == "gemini" && !response.prompt_pentru_gemini.isNullOrEmpty()) {
                    binding.tvResult.text       = "✨ Se generează comentariul Gemini..."
                    binding.tvResult.visibility = View.VISIBLE
                    currentCommentary = GeminiHelper.genereazaComentariu(response.prompt_pentru_gemini)
                } else {
                    currentCommentary = if (modPiloti)
                        response.comentariu_ai ?: response.scenarii_matematice ?: ""
                    else
                        response.rezumat_scenarii ?: ""
                }

                binding.tvResult.text               = "🏆 Calcule finalizate!"
                binding.tvResult.visibility         = View.VISIBLE
                binding.btnShowCommentary.visibility = View.VISIBLE
                if (modPiloti && currentMath.isNotEmpty())
                    binding.btnShowMath.visibility  = View.VISIBLE

            } catch (e: Exception) {
                Toast.makeText(this@ChampionshipActivity, "Eroare: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnCalculate.isEnabled          = true
                binding.btnCalculate.text               = "🏆 CALCULEAZĂ ȘANSELE"
                binding.progressChampionship.visibility = View.GONE
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LISTA COMPETITORI
    // ─────────────────────────────────────────────────────────────

    private fun reseteazaLista() {
        binding.containerCompetitors.removeAllViews()
        randuriGenerate.clear()
        binding.tvResult.visibility          = View.GONE
        binding.btnShowCommentary.visibility = View.GONE
        binding.btnShowMath.visibility       = View.GONE
        adaugaRandCompetitor()
        adaugaRandCompetitor()
    }

    private fun adaugaRandCompetitor() {
        val listaNume = if (modPiloti) listaPiloti else listaEchipe

        // Container rand
        val row = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 8)
            gravity = Gravity.CENTER_VERTICAL
        }

        // Separator subtil intre randuri (nu la primul)
        if (randuriGenerate.isNotEmpty()) {
            val sep = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                ).also { it.bottomMargin = 8 }
                setBackgroundColor(Color.parseColor("#1E1E35"))
            }
            binding.containerCompetitors.addView(sep)
        }

        // Spinner stilizat
        val spinner = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 100, 1f)
            setBackgroundResource(R.drawable.bg_spinner_dark)
        }

        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, listaNume
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(if (position == 0) Color.parseColor("#30304A") else Color.WHITE)
                view.textSize = 12f
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setBackgroundColor(Color.parseColor("#16162A"))
                view.setPadding(40, 30, 40, 30)
                view.setTextColor(if (position == 0) Color.parseColor("#30304A") else Color.WHITE)
                return view
            }
        }
        spinner.adapter = adapter

        // EditText puncte stilizat
        val etPoints = EditText(this).apply {
            hint      = "Pct"
            setHintTextColor(Color.parseColor("#30304A"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER
            gravity   = Gravity.CENTER
            textSize  = 14f
            layoutParams = LinearLayout.LayoutParams(160, 100)
            setBackgroundResource(R.drawable.bg_input_login)
            setPadding(8, 0, 8, 0)
        }

        // Buton stergere stilizat
        val btnRemove = AppCompatButton(this).apply {
            text      = "✕"
            setTextColor(Color.parseColor("#7070A0"))
            textSize  = 12f
            setBackgroundResource(R.drawable.bg_logout_btn)
            layoutParams = LinearLayout.LayoutParams(90, 100).also {
                it.marginStart = 8
            }
            stateListAnimator = null
        }

        val rowUI = CompetitorRowUI(spinner, etPoints, row)

        btnRemove.setOnClickListener {
            if (randuriGenerate.size > 2) {
                // Sterge si separatorul de deasupra daca exista
                val idx = binding.containerCompetitors.indexOfChild(row)
                if (idx > 0) binding.containerCompetitors.removeViewAt(idx - 1)
                binding.containerCompetitors.removeView(row)
                randuriGenerate.remove(rowUI)
            } else {
                Toast.makeText(this, "Ai nevoie de minim 2 competitori!", Toast.LENGTH_SHORT).show()
            }
        }

        row.addView(spinner)
        row.addView(etPoints)
        row.addView(btnRemove)
        binding.containerCompetitors.addView(row)
        randuriGenerate.add(rowUI)
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER DIALOG
    // ─────────────────────────────────────────────────────────────

    private fun showDialog(title: String, message: String) {
        if (message.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("ÎNCHIDE") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}