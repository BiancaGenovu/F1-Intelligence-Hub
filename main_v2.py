"""
main_v2.py — F1 Intelligence Hub (versiune noua cu F1_2025Grid.xlsx)
Pornire: python main_v2.py
Swagger UI: http://localhost:8001/docs
"""

import pandas as pd
import numpy as np
import xgboost as xgb
import ollama
import time
import os

from fastapi import FastAPI, HTTPException
from fastapi.staticfiles import StaticFiles
from typing import List
import uvicorn

from race_animator import genereaza_animatie_cursa, CULORI_ECHIPE

app = FastAPI(
    title="F1 Intelligence Hub v2 — Grila 2025",
    description="Backend cu date reale FastF1 (2020-2025). Testare: /docs",
    version="2.0"
)

app.mount("/static", StaticFiles(directory="."), name="static")


model_v2 = xgb.XGBRegressor()
model_v2.load_model("model_f1_nou.json")
print(f"✅ Model încărcat: {model_v2.n_features_in_} features, {model_v2.n_estimators} arbori")

try:
    df_toate = pd.read_excel("F1_2025Grid.xlsx", sheet_name="Date_Complete")
    print(f"✅ Excel încărcat: {len(df_toate)} rânduri | "
          f"{df_toate['Abbreviation'].nunique()} piloți | "
          f"{df_toate['CircuitName'].nunique()} circuite")
except Exception as e:
    print(f"❌ Eroare Excel: {e}")
    df_toate = pd.DataFrame()

# ─────────────────────────────────────────────────────────────
# 2. MAPARI ANDROID → EXCEL
# ─────────────────────────────────────────────────────────────

PILOT_TO_ABBR = {
    "Max Verstappen":    "VER",
    "Liam Lawson":       "LAW",
    "Charles Leclerc":   "LEC",
    "Lewis Hamilton":    "HAM",
    "Lando Norris":      "NOR",
    "Oscar Piastri":     "PIA",
    "George Russell":    "RUS",
    "Kimi Antonelli":    "ANT",
    "Fernando Alonso":   "ALO",
    "Lance Stroll":      "STR",
    "Pierre Gasly":      "GAS",
    "Jack Doohan":       "DOO",
    "Yuki Tsunoda":      "TSU",
    "Isack Hadjar":      "HAD",
    "Alexander Albon":   "ALB",
    "Carlos Sainz":      "SAI",
    "Nico Hulkenberg":   "HUL",
    "Niko Hulkenberg":   "HUL",
    "Gabriel Bortoleto": "BOR",
    "Esteban Ocon":      "OCO",
    "Oliver Bearman":    "BEA",
}

CIRCUIT_TO_FASTF1 = {
    "Bahrain":          "Sakhir",
    "Saudi Arabia":     "Jeddah",
    "Australia":        "Melbourne",
    "Japan":            "Suzuka",
    "China":            "Shanghai",
    "Miami":            "Miami",
    "Emilia-Romagna":   "Imola",
    "Monaco":           "Monaco",
    "Spain":            "Barcelona",
    "Canada":           "Montréal",
    "Austria":          "Spielberg",
    "Great Britain":    "Silverstone",
    "Belgium":          "Spa-Francorchamps",
    "Hungary":          "Budapest",
    "Netherlands":      "Zandvoort",
    "Italy":            "Monza",
    "Azerbaijan":       "Baku",
    "Singapore":        "Marina Bay",
    "United States":    "Austin",
    "Mexico":           "Mexico City",
    "Brazil":           "São Paulo",
    "Las Vegas":        "Las Vegas",
    "Qatar":            "Lusail",
    "Abu Dhabi":        "Yas Island",
}

FASTF1_TO_ANIMATOR = {
    "Sakhir":            "Bahrain",
    "Jeddah":            "Saudi Arabia",
    "Melbourne":         "Australia",
    "Suzuka":            "Japan",
    "Shanghai":          "China",
    "Miami":             "Miami",
    "Imola":             "Emilia-Romagna",
    "Monaco":            "Monaco",
    "Barcelona":         "Spain",
    "Montréal":          "Canada",
    "Spielberg":         "Austria",
    "Silverstone":       "Great Britain",
    "Spa-Francorchamps": "Belgium",
    "Budapest":          "Hungary",
    "Zandvoort":         "Netherlands",
    "Monza":             "Italy",
    "Baku":              "Azerbaijan",
    "Marina Bay":        "Singapore",
    "Austin":            "United States",
    "Mexico City":       "Mexico",
    "São Paulo":         "Brazil",
    "Las Vegas":         "Las Vegas",
    "Lusail":            "Qatar",
    "Yas Island":        "Abu Dhabi",
}

CULORI_ECHIPE_V2 = {
    "Red Bull Racing":  "#3671C6",
    "Ferrari":          "#E8002D",
    "McLaren":          "#FF8000",
    "Mercedes":         "#27F4D2",
    "Aston Martin":     "#229971",
    "Alpine":           "#0093CC",
    "Williams":         "#64C4FF",
    "RB":               "#6692FF",
    "Haas F1 Team":     "#B6BABD",
    "Sauber":           "#52E252",
    "Kick Sauber":      "#52E252",
    "Haas":             "#B6BABD",
    "AlphaTauri":       "#6692FF",
    "Alfa Romeo":       "#C92D4B",
}

# ─────────────────────────────────────────────────────────────
# 3. FEATURES FOLOSITE DE MODEL
# ─────────────────────────────────────────────────────────────

FEATURES_MODEL = [
    'GridPosition',
    'BestQ_normalized',
    'GapToPole_sec',
    'GapToPole_pct',
    'QualiGapToTeammate_sec',
    'QualiGapToFastest_sec',
    'MedianLap_normalized',
    'MedianLapGapToFastest_sec',
    'NumPitStops',
    'StartCompound',
    'NumSafetyCarLaps',
    'Rainfall',
    'AirTemp',
    'TrackTemp',
    'RollingAvgPos_5',
    'RollingAvgPos_10',
    'SeasonPoints_before',
    'SeasonWins_before',
    'SeasonPodiums_before',
    'SeasonDNF_rate',
    'WDC_Standing_before',
    'WCC_Standing_before',
    'TeamSeasonPoints_before',
    'TeamSeasonPodiums_before',
    'DriverCircuitRaces_before',
    'DriverCircuitAvgPos_before',
    'DriverCircuitBestPos_before',
    'CareerRaces_before',
    'CareerWins_before',
    'CareerPodiums_before',
    'SprintPosition',
    'IsSprintWeekend',
]

# ─────────────────────────────────────────────────────────────
# 4. LOGICA DE PREDICTIE
# ─────────────────────────────────────────────────────────────

def traduce_input(nume_pilot: str, nume_circuit: str):
    abbr = PILOT_TO_ABBR.get(nume_pilot)
    if abbr is None:
        raise HTTPException(
            status_code=400,
            detail=f"Pilot necunoscut: '{nume_pilot}'. Disponibili: {sorted(PILOT_TO_ABBR.keys())}"
        )
    circuit = CIRCUIT_TO_FASTF1.get(nume_circuit)
    if circuit is None:
        raise HTTPException(
            status_code=400,
            detail=f"Circuit necunoscut: '{nume_circuit}'. Disponibile: {sorted(CIRCUIT_TO_FASTF1.keys())}"
        )
    return abbr, circuit


def get_features_pilot(abbr: str, circuit_fastf1: str,
                       grid_position: int, rainfall: int) -> dict:
    features = {f: 0.0 for f in FEATURES_MODEL}

    features['GridPosition']     = float(grid_position)
    features['Rainfall']         = float(rainfall)
    features['IsSprintWeekend']  = 0.0
    features['SprintPosition']   = 10.0

    if df_toate.empty:
        return features

    df_pilot = df_toate[df_toate['Abbreviation'] == abbr].sort_values(['An', 'Round'])

    if df_pilot.empty:
        features['BestQ_normalized']            = 1.020
        features['GapToPole_sec']               = 1.5
        features['MedianLap_normalized']        = 1.020
        features['RollingAvgPos_5']             = 12.0
        features['RollingAvgPos_10']            = 12.0
        features['WDC_Standing_before']         = 15.0
        features['DriverCircuitAvgPos_before']  = 12.0
        features['DriverCircuitBestPos_before'] = 12.0
        return features

    ultima_cursa = df_pilot.iloc[-1]

    form5  = df_pilot['RollingAvgPos_5'].dropna()
    form10 = df_pilot['RollingAvgPos_10'].dropna()
    features['RollingAvgPos_5']  = float(form5.median())  if not form5.empty  else 10.0
    features['RollingAvgPos_10'] = float(form10.median()) if not form10.empty else 10.0

    features['WDC_Standing_before'] = float(ultima_cursa.get('WDC_Standing_before', 10) or 10)
    features['WCC_Standing_before'] = float(ultima_cursa.get('WCC_Standing_before',  5) or 5)

    features['SeasonPoints_before']  = float(ultima_cursa.get('SeasonPoints_before',  0) or 0)
    features['SeasonWins_before']    = float(ultima_cursa.get('SeasonWins_before',     0) or 0)
    features['SeasonPodiums_before'] = float(ultima_cursa.get('SeasonPodiums_before',  0) or 0)
    features['SeasonDNF_rate']       = float(ultima_cursa.get('SeasonDNF_rate',        0) or 0)

    features['TeamSeasonPoints_before']  = float(ultima_cursa.get('TeamSeasonPoints_before',  0) or 0)
    features['TeamSeasonPodiums_before'] = float(ultima_cursa.get('TeamSeasonPodiums_before', 0) or 0)

    features['CareerRaces_before']   = float(ultima_cursa.get('CareerRaces_before',   0) or 0)
    features['CareerWins_before']    = float(ultima_cursa.get('CareerWins_before',     0) or 0)
    features['CareerPodiums_before'] = float(ultima_cursa.get('CareerPodiums_before',  0) or 0)

    BQ_PER_GRID = {
         1: 1.002619,  2: 1.005375,  3: 1.006140,  4: 1.007338,
         5: 1.010299,  6: 1.010589,  7: 1.011380,  8: 1.015174,
         9: 1.015074, 10: 1.019768, 11: 1.018013, 12: 1.018624,
        13: 1.020789, 14: 1.025999, 15: 1.030481, 16: 1.026848,
        17: 1.029540, 18: 1.032754, 19: 1.034018, 20: 1.037158,
    }
    GAP_PER_GRID = {
         1: 0.223,  2: 0.440,  3: 0.510,  4: 0.595,
         5: 0.846,  6: 0.873,  7: 0.932,  8: 1.231,
         9: 1.233, 10: 1.579, 11: 1.492, 12: 1.516,
        13: 1.712, 14: 2.122, 15: 2.563, 16: 2.237,
        17: 2.397, 18: 2.715, 19: 2.839, 20: 3.263,
    }
    gp = max(1, min(20, grid_position))
    features['BestQ_normalized']       = BQ_PER_GRID.get(gp, 1.020)
    features['GapToPole_sec']          = GAP_PER_GRID.get(gp, 1.5)
    features['GapToPole_pct']          = features['GapToPole_sec'] / 90.0 * 100
    features['QualiGapToFastest_sec']  = GAP_PER_GRID.get(gp, 1.5)
    features['QualiGapToTeammate_sec'] = 0.0

    ml_toate = df_pilot['MedianLap_normalized'].dropna()
    ml_clean = ml_toate[ml_toate < 1.05]
    features['MedianLap_normalized']      = float(ml_clean.median()) if not ml_clean.empty else 1.020
    mlg_toate = df_pilot['MedianLapGapToFastest_sec'].dropna()
    features['MedianLapGapToFastest_sec'] = float(mlg_toate.median()) if not mlg_toate.empty else 1.5

    df_cir = df_toate[df_toate['CircuitName'] == circuit_fastf1]
    features['AirTemp']   = float(df_cir['AirTemp'].mean())   if not df_cir.empty else 25.0
    features['TrackTemp'] = float(df_cir['TrackTemp'].mean()) if not df_cir.empty else 35.0

    np_col = df_pilot['NumPitStops'].dropna()
    features['NumPitStops']      = float(np_col.median()) if not np_col.empty else 1.5
    features['StartCompound']    = 2.0
    features['NumSafetyCarLaps'] = 3.0

    df_pc = df_pilot[df_pilot['CircuitName'] == circuit_fastf1]
    if not df_pc.empty:
        features['DriverCircuitRaces_before']   = float(len(df_pc))
        features['DriverCircuitAvgPos_before']  = float(df_pc['FinalPosition'].mean())
        features['DriverCircuitBestPos_before'] = float(df_pc['FinalPosition'].min())
    else:
        features['DriverCircuitRaces_before']   = 0.0
        avg_gen = df_pilot['FinalPosition'].mean()
        features['DriverCircuitAvgPos_before']  = float(avg_gen) if not np.isnan(avg_gen) else 10.0
        features['DriverCircuitBestPos_before'] = float(df_pilot['FinalPosition'].min())

    return features


def construieste_df_ml(lista_features: list) -> pd.DataFrame:
    df = pd.DataFrame(lista_features)
    for col in FEATURES_MODEL:
        if col not in df.columns:
            df[col] = 0.0
        else:
            df[col] = df[col].fillna(0.0)
    return df[FEATURES_MODEL]


# ─────────────────────────────────────────────────────────────
# 5. ENDPOINT 1 — PREDICTIE PILOT SINGUR
# Suporta llm_provider: "ollama" (implicit) sau "gemini"
# ─────────────────────────────────────────────────────────────

@app.post("/predict_and_analyze", tags=["Predictie Individuala"])
def predict_and_analyze(data: dict):
    nume_pilot    = data.get('Nume_Pilot', '')
    grid          = int(data.get('Starting_Grid', 10))
    circuit       = data.get('Nume_Circuit', '')
    rainfall      = int(data.get('Vreme_Cursa_1_ploaie', 0))
    llm_provider  = data.get('llm_provider', 'ollama')  # NOU
    este_ploaie   = "ploaie" if rainfall == 1 else "vreme uscată"

    abbr, circuit_fastf1 = traduce_input(nume_pilot, circuit)

    features = get_features_pilot(abbr, circuit_fastf1, grid, rainfall)
    df_ml    = construieste_df_ml([features])

    scor_brut = float(model_v2.predict(df_ml)[0])
    loc_final = max(1, min(20, round(scor_brut)))

    podiumuri   = int(features.get('CareerPodiums_before', 0))
    victorii    = int(features.get('CareerWins_before',    0))
    pozitie_wdc = int(features.get('WDC_Standing_before',  0))
    form_recent = round(features.get('RollingAvgPos_5',    10), 1)

    prompt = (
        f"Ești un jurnalist sportiv român, expert în Formula 1. "
        f"Analizează această predicție: {nume_pilot} pleacă de pe locul {grid} "
        f"la {circuit} în condiții de {este_ploaie}. "
        f"Date pilot: {victorii} victorii în carieră, {podiumuri} podiumuri, "
        f"poziția {pozitie_wdc} în campionat, formă recentă (medie ultimele 5 curse): {form_recent}. "
        f"Predicție model XGBoost: locul {loc_final}. "
        f"Scrie o analiză scurtă de 3-4 propoziții în română impecabilă, "
        f"naturală și fluentă, ca un comentator TV profesionist. "
        f"Fără traduceri literale din engleză."
    )

    if llm_provider == 'gemini':
        return {
            "pozitie_estimata":     loc_final,
            "scor_brut":            round(scor_brut, 3),
            "analiza_ai":           "",
            "prompt_pentru_gemini": prompt
        }

    try:
        response = ollama.generate(
            model="llama3.1",
            prompt=prompt,
            options={'temperature': 0.5}
        )
        analiza = response['response']
    except Exception as e:
        analiza = f"Analiza AI nu este disponibilă (Ollama offline): {e}"

    return {
        "pozitie_estimata":     loc_final,
        "scor_brut":            round(scor_brut, 3),
        "analiza_ai":           analiza,
        "prompt_pentru_gemini": None
    }


# ─────────────────────────────────────────────────────────────
# 6. ENDPOINT 2 — SIMULARE CURSA COMPLETA (20 piloti)
# Suporta llm_provider in primul obiect din lista
# ─────────────────────────────────────────────────────────────

@app.post("/simulate_race", tags=["Simulare Cursa Completa"])
def simulate_race(drivers_data: List[dict]):
    if not drivers_data:
        raise HTTPException(status_code=400, detail="Lista goală")

    llm_provider = drivers_data[0].get('llm_provider', 'ollama') 

    lista_features = []
    meta_piloti    = []

    for d in drivers_data:
        nume_pilot = d.get('Nume_Pilot', '')
        grid       = int(d.get('Starting_Grid', 10))
        circuit    = d.get('Nume_Circuit', '')
        rainfall   = int(d.get('Vreme_Cursa_1_ploaie', 0))

        abbr, circuit_fastf1 = traduce_input(nume_pilot, circuit)
        features = get_features_pilot(abbr, circuit_fastf1, grid, rainfall)
        lista_features.append(features)

        df_pilot = df_toate[df_toate['Abbreviation'] == abbr]
        team = df_pilot.iloc[-1]['TeamName'] if not df_pilot.empty else "Unknown"

        meta_piloti.append({
            'Nume_Pilot':    nume_pilot,
            'Abbreviation':  abbr,
            'Starting_Grid': grid,
            'TeamName':      team,
        })

    df_ml   = construieste_df_ml(lista_features)
    scoruri = model_v2.predict(df_ml)

    rezultate = []
    for i, meta in enumerate(meta_piloti):
        rezultate.append({**meta, 'scor_brut': float(scoruri[i])})

    rezultate_sortate = sorted(rezultate, key=lambda x: x['scor_brut'])
    for idx, r in enumerate(rezultate_sortate):
        r['loc_final']         = idx + 1
        r['pozitii_castigate'] = r['Starting_Grid'] - r['loc_final']

    # Animatie GIF (se genereaza intotdeauna, indiferent de LLM)
    date_animatie = []
    for r in rezultate_sortate:
        culoare = CULORI_ECHIPE_V2.get(r['TeamName'], '#FFFFFF')
        date_animatie.append({
            'nume':    r['Abbreviation'],
            'start':   r['Starting_Grid'],
            'end':     r['loc_final'],
            'culoare': culoare,
        })

    circuit_display  = drivers_data[0].get('Nume_Circuit', 'Circuit')
    circuit_fastf1   = CIRCUIT_TO_FASTF1.get(circuit_display, circuit_display)
    circuit_animator = FASTF1_TO_ANIMATOR.get(circuit_fastf1, circuit_display)
    genereaza_animatie_cursa(date_animatie, circuit_animator, "race_animation.gif")

    # Clasament JSON
    clasament_json = [
        {
            'Nume_Pilot':        r['Nume_Pilot'],
            'Starting_Grid':     r['Starting_Grid'],
            'loc_final':         r['loc_final'],
            'pozitii_castigate': r['pozitii_castigate'],
            'scor_brut':         round(r['scor_brut'], 3),
        }
        for r in rezultate_sortate
    ]

    gif_url = f"/static/race_animation.gif?v={int(time.time())}"

    # Construim promptul (acelasi indiferent de LLM)
    podium      = [r['Nume_Pilot'] for r in rezultate_sortate[:3]]
    pilot_zilei = max(rezultate_sortate, key=lambda x: x['pozitii_castigate'])

    prompt = (
        f"Ești un comentator sportiv român pasionat de Formula 1. "
        f"S-a încheiat Marele Premiu de la {circuit_display}. "
        f"Podiumul: 1. {podium[0]}, 2. {podium[1]}, 3. {podium[2]}. "
        f"Revelația cursei: {pilot_zilei['Nume_Pilot']} a urcat {pilot_zilei['pozitii_castigate']} poziții, "
        f"terminând pe locul {pilot_zilei['loc_final']}. "
        f"Scrie un rezumat entuziast de 4-5 propoziții în română naturală, "
        f"cursivă și corectă gramatical, ca un comentator TV real din România."
    )

    # ── ROUTING LLM ─────────────────────────────────────────
    if llm_provider == 'gemini':
        return {
            "status":               "success",
            "rezumat_cursa":        "",
            "clasament":            clasament_json,
            "animatie_url":         gif_url,
            "prompt_pentru_gemini": prompt
        }

    # Ollama (comportament original)
    try:
        response = ollama.generate(
            model="llama3.1",
            prompt=prompt,
            options={'temperature': 0.6}
        )
        rezumat = response['response']
    except Exception as e:
        rezumat = f"Comentariul AI nu este disponibil (Ollama offline): {e}"

    return {
        "status":               "success",
        "rezumat_cursa":        rezumat,
        "clasament":            clasament_json,
        "animatie_url":         gif_url,
        "prompt_pentru_gemini": None
    }


# ─────────────────────────────────────────────────────────────
# 7. ENDPOINT 3 — SCENARII CAMPIONAT PILOTI
# Suporta llm_provider: "ollama" (implicit) sau "gemini"
# ─────────────────────────────────────────────────────────────

@app.post("/championship_scenarios", tags=["Campionat"])
def championship_scenarios(data: dict):
   
    curse_ramase = int(data.get("curse_ramase", 1))
    piloti       = data.get("piloti", [])
    llm_provider = data.get("llm_provider", "ollama")  # NOU

    if len(piloti) < 2:
        raise HTTPException(status_code=400, detail="Ai nevoie de cel puțin 2 piloți.")

    piloti_sortati = sorted(piloti, key=lambda x: int(x["puncte"]), reverse=True)
    lider          = piloti_sortati[0]
    urmaritori     = piloti_sortati[1:]
    puncte_maxime  = curse_ramase * 26
    puncte_f1      = {1:25, 2:18, 3:15, 4:12, 5:10, 6:8, 7:6, 8:4, 9:2, 10:1, "DNF":0}

    scenarii = []

    if curse_ramase == 1:
        scenarii.append("Este ultima cursă a sezonului!")
        locuri_sigure = []

        for loc_lider, pct_lider in puncte_f1.items():
            total_lider = lider["puncte"] + pct_lider
            conditii = []

            for u in urmaritori:
                puncte_necesare = total_lider - u["puncte"]
                if puncte_necesare <= 25:
                    loc_necesar = "Imposibil"
                    for loc_u, pct_u in puncte_f1.items():
                        if loc_u != "DNF" and pct_u >= puncte_necesare:
                            loc_necesar = str(loc_u)
                    if loc_necesar != "Imposibil":
                        conditii.append(f"{u['nume']} termină pe {loc_necesar}")

            if not conditii:
                locuri_sigure.append(str(loc_lider))
            else:
                eticheta = f"locul {loc_lider}" if loc_lider != "DNF" else "Abandon"
                scenarii.append(
                    f"Dacă {lider['nume']} obține {eticheta} -> titlul se decide dacă "
                    + " SAU ".join(conditii) + "."
                )

        if locuri_sigure:
            scenarii.insert(1, f"{lider['nume']} ia titlul sigur dacă termină pe locurile: {', '.join(locuri_sigure)}.")
    else:
        scenarii.append(f"Puncte maxime rămase: {puncte_maxime}.")
        cel_mai_apropiat = urmaritori[0]
        puncte_tinta = cel_mai_apropiat["puncte"] + puncte_maxime
        magie = puncte_tinta - lider["puncte"] + 1

        if magie <= 0:
            scenarii.append(f"{lider['nume']} este DEJA CAMPION MONDIAL!")
        else:
            medie = magie / curse_ramase
            scenarii.append(f"{lider['nume']} are nevoie de {magie} puncte ({medie:.1f}/cursă).")

        for u in urmaritori:
            diferenta = lider["puncte"] - u["puncte"]
            if diferenta >= puncte_maxime:
                scenarii.append(f"{u['nume']} ({diferenta} pct în urmă) -> ELIMINAT.")
            else:
                scenarii.append(
                    f"{u['nume']} ({diferenta} pct în urmă) -> trebuie să recupereze "
                    f"{diferenta/curse_ramase:.1f} pct/cursă."
                )

    text_matematic = "\n".join(scenarii)

    prompt = (
        f"Ești un comentator sportiv de Formula 1 din România. "
        f"Iată un rezumat matematic al luptei la titlul piloților cu {curse_ramase} curse rămase:\n\n"
        f"{text_matematic}\n\n"
        f"Transformă aceste date într-o știre/analiză scurtă, entuziastă și ușor de citit (maxim 3-4 propoziții). "
        f"Concentrează-te pe ce trebuie să facă liderul ({lider['nume']}) pentru a câștiga și cine sunt principalii amenințători. "
        f"Nu prezenta o listă, ci un text fluid."
    )

    # ── ROUTING LLM ─────────────────────────────────────────
    if llm_provider == 'gemini':
        return {
            "status":               "success",
            "scenarii_matematice":  text_matematic,
            "comentariu_ai":        "",
            "prompt_pentru_gemini": prompt
        }

    # Ollama (comportament original)
    try:
        response = ollama.generate(model="llama3.1", prompt=prompt, options={'temperature': 0.6})
        comentariu = response['response'].strip()
    except Exception as e:
        print(f"❌ OLLAMA ERROR: {e}")
        comentariu = text_matematic

    return {
        "status":               "success",
        "scenarii_matematice":  text_matematic,
        "comentariu_ai":        comentariu,
        "prompt_pentru_gemini": None
    }


# ─────────────────────────────────────────────────────────────
# 8. ENDPOINT 4 — SCENARII CAMPIONAT CONSTRUCTORI
# Suporta llm_provider: "ollama" (implicit) sau "gemini"
# ─────────────────────────────────────────────────────────────

@app.post("/constructors_championship", tags=["Campionat"])
def constructors_championship(data: dict):
 
    curse_ramase = int(data.get("curse_ramase", 1))
    echipe       = data.get("echipe", [])
    llm_provider = data.get("llm_provider", "ollama")  # NOU

    if len(echipe) < 2:
        raise HTTPException(status_code=400, detail="Ai nevoie de cel puțin 2 echipe.")

    echipe_sortate = sorted(echipe, key=lambda x: int(x["puncte"]), reverse=True)
    lider          = echipe_sortate[0]
    urmaritori     = echipe_sortate[1:]
    puncte_maxime  = curse_ramase * 43

    scenarii = []
    scenarii.append(f"Puncte maxime rămase: {puncte_maxime} pct.")

    cel_mai_apropiat = urmaritori[0]
    magie = (cel_mai_apropiat["puncte"] + puncte_maxime) - lider["puncte"] + 1

    if magie <= 0:
        scenarii.append(f"{lider['nume'].upper()} ESTE CAMPIOANĂ MATEMATIC!")
    else:
        medie = magie / curse_ramase
        scenarii.append(f"{lider['nume']} are nevoie de {magie} puncte total ({medie:.1f} pct/cursă).")

    for u in urmaritori:
        diferenta = lider["puncte"] - u["puncte"]
        if diferenta >= puncte_maxime:
            scenarii.append(f"{u['nume']} ({diferenta} pct în urmă) -> ELIMINATĂ.")
        else:
            scenarii.append(
                f"{u['nume']} ({diferenta} pct în urmă) -> are nevoie de "
                f"{diferenta/curse_ramase:.1f} pct/cursă."
            )

    text_matematic = "\n".join(scenarii)

    prompt = (
        f"Ești un analist de Formula 1 din România. "
        f"Iată calculele pentru titlul constructorilor cu {curse_ramase} curse rămase:\n\n"
        f"{text_matematic}\n\n"
        f"Scrie un scurt articol (2-3 propoziții) explicând situația. "
        f"Fii captivant, menționează dacă liderul ({lider['nume']}) e sub presiune "
        f"sau dacă are titlul asigurat."
    )

    # ── ROUTING LLM ─────────────────────────────────────────
    if llm_provider == 'gemini':
        return {
            "status":               "success",
            "rezumat_scenarii":     "",
            "prompt_pentru_gemini": prompt
        }

    # Ollama (comportament original)
    try:
        response = ollama.generate(model="llama3.1", prompt=prompt, options={'temperature': 0.6})
        rezultat = response['response'].strip()
    except Exception as e:
        print(f"❌ OLLAMA ERROR: {e}")
        rezultat = text_matematic

    return {
        "status":               "success",
        "rezumat_scenarii":     rezultat,
        "prompt_pentru_gemini": None
    }


# ─────────────────────────────────────────────────────────────
# 9. ENDPOINT 5 — DUEL PILOT VS PILOT (HEAD-TO-HEAD)
# Suporta llm_provider: "ollama" (implicit) sau "gemini"
# ─────────────────────────────────────────────────────────────

@app.post("/duel", tags=["Analiză Duel"])
def duel_pilots(data: dict):
    nume_tu      = data.get('pilot_tu', '')
    nume_rival   = data.get('pilot_rival', '')
    circuit_nume = data.get('circuit', '')
    llm_provider = data.get('llm_provider', 'ollama')

    abbr_tu, circuit_fastf1 = traduce_input(nume_tu, circuit_nume)
    abbr_rival, _           = traduce_input(nume_rival, circuit_nume)

    df_cir = df_toate[df_toate['CircuitName'] == circuit_fastf1].copy()

    curse_comune = set(df_cir[df_cir['Abbreviation'] == abbr_tu]['Round'].tolist()) & \
                   set(df_cir[df_cir['Abbreviation'] == abbr_rival]['Round'].tolist())

    if not curse_comune:
        return {
            "error": True,
            "message": f"Nu există date comune pentru {nume_tu} și {nume_rival} la {circuit_nume}."
        }

    ultima_runda = max(curse_comune)
    df_runda = df_cir[df_cir['Round'] == ultima_runda]

    row_tu    = df_runda[df_runda['Abbreviation'] == abbr_tu].iloc[0]
    row_rival = df_runda[df_runda['Abbreviation'] == abbr_rival].iloc[0]

    sectoare_tu    = ['Sector1Q_sec', 'Sector2Q_sec', 'Sector3Q_sec']
    sectoare_rival = ['Sector1Q_sec', 'Sector2Q_sec', 'Sector3Q_sec']

    for col in sectoare_tu:
        if pd.isna(row_tu[col]):
            return {
                "error": True,
                "message": f"{nume_tu} nu are date pe sectoare pentru {circuit_nume}."
            }

    for col in sectoare_rival:
        if pd.isna(row_rival[col]):
            return {
                "error": True,
                "message": f"{nume_rival} nu are date pe sectoare pentru {circuit_nume}."
            }

    def to_ms(val):
        return int(float(val) * 1000)

    s1_p1 = to_ms(row_tu['Sector1Q_sec'])
    s2_p1 = to_ms(row_tu['Sector2Q_sec'])
    s3_p1 = to_ms(row_tu['Sector3Q_sec'])
    s1_p2 = to_ms(row_rival['Sector1Q_sec'])
    s2_p2 = to_ms(row_rival['Sector2Q_sec'])
    s3_p2 = to_ms(row_rival['Sector3Q_sec'])

    lap_p1 = s1_p1 + s2_p1 + s3_p1
    lap_p2 = s1_p2 + s2_p2 + s3_p2

    time_diff = lap_p1 - lap_p2
    winner = "pilot1" if time_diff < 0 else ("pilot2" if time_diff > 0 else "tie")

    def s_comp(s_p1, s_p2):
        diff = s_p1 - s_p2
        win  = "pilot1" if diff < 0 else ("pilot2" if diff > 0 else "tie")
        return {"pilot1": s_p1, "pilot2": s_p2, "diff": diff, "winner": win}

    sec_comp = {
        "sector1": s_comp(s1_p1, s1_p2),
        "sector2": s_comp(s2_p1, s2_p2),
        "sector3": s_comp(s3_p1, s3_p2),
    }

    diffs = {
        "Sectorul 1": sec_comp["sector1"]["diff"],
        "Sectorul 2": sec_comp["sector2"]["diff"],
        "Sectorul 3": sec_comp["sector3"]["diff"],
    }

    if winner == "pilot1":
        sector_decisiv = min(diffs, key=diffs.get)
    elif winner == "pilot2":
        sector_decisiv = max(diffs, key=diffs.get)
    else:
        sector_decisiv = "niciun sector"

    nume_castigator = nume_tu if winner == "pilot1" else (
                      nume_rival if winner == "pilot2" else "Nimeni")

    an_cursa = int(df_runda['An'].iloc[0])

    prompt = (
        f"Ești un expert în telemetrie F1. Comentează acest duel virtual: "
        f"{nume_tu} vs {nume_rival} la {circuit_nume} (date din {an_cursa}). "
        f"{nume_castigator} a fost mai rapid cu {abs(time_diff)/1000.0:.3f} secunde. "
        f"Diferențele pe sectoare (negativ = {nume_tu} mai rapid): "
        f"S1: {sec_comp['sector1']['diff']/1000.0:+.3f}s, "
        f"S2: {sec_comp['sector2']['diff']/1000.0:+.3f}s, "
        f"S3: {sec_comp['sector3']['diff']/1000.0:+.3f}s. "
        f"Sectorul decisiv a fost {sector_decisiv}. "
        f"Scrie exact un paragraf (maxim 3 propoziții) în română, "
        f"foarte tehnic și entuziast, ca la TV. Nu repeta cerința."
    )

    t1 = {"lapTimeMs": lap_p1, "sector1Ms": s1_p1,
          "sector2Ms": s2_p1,  "sector3Ms": s3_p1}
    t2 = {"lapTimeMs": lap_p2, "sector1Ms": s1_p2,
          "sector2Ms": s2_p2,  "sector3Ms": s3_p2}

    if llm_provider == 'gemini':
        return {
            "error":                False,
            "winner":               winner,
            "timeDiff":             time_diff,
            "insights":             "",
            "myTime":               t1,
            "rivalTime":            t2,
            "sectorComparison":     sec_comp,
            "prompt_pentru_gemini": prompt
        }

    try:
        response = ollama.generate(model="llama3.1", prompt=prompt,
                                   options={'temperature': 0.6})
        insights = response['response'].strip()
    except Exception as e:
        insights = f"Analiza AI nu este disponibilă (Ollama offline): {e}"

    return {
        "error":                False,
        "winner":               winner,
        "timeDiff":             time_diff,
        "insights":             insights,
        "myTime":               t1,
        "rivalTime":            t2,
        "sectorComparison":     sec_comp,
        "prompt_pentru_gemini": None
    }

# ─────────────────────────────────────────────────────────────
# 10. ENDPOINT DE VERIFICARE (Adaugă aici)
# ─────────────────────────────────────────────────────────────

@app.get("/check_animation_ready")
def check_animation_ready():
    path = "race_animation.gif"
    
    if os.path.exists(path) and os.path.getsize(path) > 1024:
        return {"ready": True}
    return {"ready": False}
 

# ─────────────────────────────────────────────────────────────
# 10. ENDPOINT DE VERIFICARE
# ─────────────────────────────────────────────────────────────

@app.get("/health", tags=["Status"])
def health_check():
    return {
        "status":          "ok",
        "versiune":        "v2 — Grila 2025",
        "model_features":  model_v2.n_features_in_,
        "model_arbori":    model_v2.n_estimators,
        "excel_randuri":   len(df_toate),
        "piloti":          sorted(df_toate['Abbreviation'].unique().tolist()) if not df_toate.empty else [],
        "circuite":        sorted(df_toate['CircuitName'].unique().tolist())  if not df_toate.empty else [],
    }


if __name__ == "__main__": 
    uvicorn.run(app, host="0.0.0.0", port=8001)