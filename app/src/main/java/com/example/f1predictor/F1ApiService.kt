package com.example.f1predictor

import retrofit2.http.Body
import retrofit2.http.POST

interface F1ApiService {
    @POST("predict_and_analyze")
    suspend fun predictSingle(@Body request: SinglePredictionRequest): SinglePredictionResponse

    @POST("simulate_race")
    suspend fun simulateFullRace(@Body request: List<SinglePredictionRequest>): RaceSimulationResponse

    @POST("championship_scenarios")
    suspend fun calculateDriversChampionship(@Body request: ChampionshipRequest): ChampionshipResponse

    @POST("constructors_championship")
    suspend fun calculateConstructorsChampionship(@Body request: ChampionshipRequest): ChampionshipResponse

    @POST("duel")
    suspend fun duelPilots(@Body request: DuelRequest): DuelResponse
}

// ─────────────────────────────────────────────────────────────
// 1. CLASE PENTRU SINGLE PREDICTION
// ─────────────────────────────────────────────────────────────

data class SinglePredictionRequest(
    val Nume_Pilot: String,
    val Starting_Grid: Int,
    val Nume_Circuit: String,
    val Vreme_Cursa_1_ploaie: Int,
    val llm_provider: String = "ollama"   // NOU: "ollama" sau "gemini"
)

data class SinglePredictionResponse(
    val pozitie_estimata: Int,
    val scor_brut: Double,
    val analiza_ai: String,
    val prompt_pentru_gemini: String?     // NOU: non-null doar cand llm_provider="gemini"
)

// ─────────────────────────────────────────────────────────────
// 2. CLASE PENTRU RACE SIMULATION
// ─────────────────────────────────────────────────────────────

data class RaceSimulationResponse(
    val status: String,
    val rezumat_cursa: String,
    val clasament: List<PilotClasat>,
    val animatie_url: String,
    val prompt_pentru_gemini: String?     // NOU
)

data class PilotClasat(
    val Nume_Pilot: String,
    val Starting_Grid: Int,
    val loc_final: Int,
    val pozitii_castigate: Int
)

// ─────────────────────────────────────────────────────────────
// 3. CLASE PENTRU CHAMPIONSHIP
// ─────────────────────────────────────────────────────────────

data class CompetitorPuncte(
    val nume: String,
    val puncte: Int
)

data class ChampionshipRequest(
    val curse_ramase: Int,
    val llm_provider: String = "ollama",  // NOU
    val piloti: List<CompetitorPuncte>? = null,
    val echipe: List<CompetitorPuncte>? = null
)

data class ChampionshipResponse(
    val status: String,
    val scenarii_matematice: String? = null,  // Calculele brute (piloti)
    val comentariu_ai: String? = null,        // Comentariu LLM (piloti)
    val rezumat_scenarii: String? = null,     // Comentariu LLM (constructori)
    val prompt_pentru_gemini: String?         // NOU
)

// ─────────────────────────────────────────────────────────────
// 4. CLASE PENTRU DUEL (PILOT VS PILOT)
// ─────────────────────────────────────────────────────────────

data class DuelRequest(
    val pilot_tu: String,
    val pilot_rival: String,
    val circuit: String,
    val llm_provider: String = "ollama"   // NOU
)

data class SectorDiff(
    val pilot1: Long,
    val pilot2: Long,
    val diff: Long,
    val winner: String
)

data class SectorComp(
    val sector1: SectorDiff,
    val sector2: SectorDiff,
    val sector3: SectorDiff
)

data class PilotTime(
    val lapTimeMs: Long,
    val sector1Ms: Long,
    val sector2Ms: Long,
    val sector3Ms: Long
)

data class DuelResponse(
    val error: Boolean = false,
    val message: String? = null,
    val winner: String = "",
    val timeDiff: Long = 0,
    val insights: String = "",
    val myTime: PilotTime? = null,
    val rivalTime: PilotTime? = null,
    val sectorComparison: SectorComp? = null,
    val prompt_pentru_gemini: String? = null
)