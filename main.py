import pandas as pd
import xgboost as xgb
import ollama
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles # <- ADĂUGAT pentru GIF
import uvicorn
from typing import List
import os # <- ADĂUGAT

from race_animator import genereaza_animatie_cursa, CULORI_ECHIPE # <- ADĂUGAT

app = FastAPI(
    title="Simulator F1 API - Ultra Realist",
    description="Backend care extrage datele direct din sheet-urile Excel-ului Licenta.xlsx"
)

# Expunem folderul curent pentru a putea descărca animația din Android
app.mount("/static", StaticFiles(directory="."), name="static")

# 1. Încărcăm Modelul XGBoost
model_suprem = xgb.XGBRegressor()
model_suprem.load_model("model_f1.json")

# 2. Încărcăm Baza de Date din Excel (din sheet-uri diferite)
try:
    fisier_excel = "Licenta.xlsx"
    df_piloti = pd.read_excel(fisier_excel, sheet_name="Piloti")
    df_echipe = pd.read_excel(fisier_excel, sheet_name="Echipe")
    df_circuit = pd.read_excel(fisier_excel, sheet_name="Circuit")
    print("✅ Fișierul Excel a fost încărcat cu succes!")
except Exception as e:
    print(f"⚠️ Eroare critică la citirea Excel-ului: {e}. Asigură-te că ai rulat 'pip install openpyxl'.")

COLANE_ASTEPTATE = [
    'CircuitID', 'PilotID', 'EchipaID', 'An_Cursa', 'Pozitie_Calificare', 
    'Starting_Grid', 'Vreme_Cursa_1_ploaie', 'Lungime_Circuit', 'Numar_Laps', 
    'Numar_Viraje', 'Scor_Depasire', 'Tip_Circuit_Viteza', 'Tip_Circuit_Viraje', 
    'Puncte_Acumulate_Constructor', 'Media_Clasare_Echipa_sezon', 
    'Rata_Abandonuri_Echipa_Sezon', 'Medie_Timp_Boxa_Secunde', 
    'Total_Podiumuri_Sezon', 'Total_Curse_Cariere', 'Istoric_Victorii_Cariera', 
    'Total_Podiumuri_Cariera', 'Media_Pozitiei_Terminare_Sezon', 
    'Rata_Abandonuri_Sezon', 'Puncte_Acumulate_Sezon', 'Index_Blocaj_Grila', 
    'Avantaj_Experienta', 'Ritm_Quali_Sec_Km', 'Ritm_Cursa_Sec_Km'
]

def pregateste_date_ml(df_input: pd.DataFrame) -> pd.DataFrame:
    """Funcție helper pentru a nu repeta codul de MERGE în ambele endpoint-uri"""
    df = df_input.copy()
    df['An_Cursa'] = 2025
    df['Pozitie_Calificare'] = df['Starting_Grid']
    
    # MERGE Piloti
    df = pd.merge(df, df_piloti, on="Nume_Pilot", how="left")
    
    # MERGE Echipe
    if 'EchipaID' in df.columns:
        df = pd.merge(df, df_echipe, on="EchipaID", how="left")
        
    # MERGE Circuit
    if 'Nume_Circuit' in df.columns:
        df = pd.merge(df, df_circuit, on="Nume_Circuit", how="left")

    # Feature Engineering
    df['Scor_Depasire'] = df.get('Scor_Depasire', pd.Series([1.0] * len(df))).fillna(1.0).replace(0, 1.0)
    df['Index_Blocaj_Grila'] = df['Starting_Grid'] / df['Scor_Depasire']
    
    df['Total_Podiumuri_Cariera'] = df.get('Total_Podiumuri_Cariera', pd.Series([0] * len(df))).fillna(0)
    df['Avantaj_Experienta'] = df['Total_Podiumuri_Cariera'] * df['Scor_Depasire']

    # Completare cu 0 pentru coloanele lipsă/NaN
    for col in COLANE_ASTEPTATE:
        if col not in df.columns:
            df[col] = 0
        else:
            df[col] = df[col].fillna(0)

    return df

# --- ENDPOINT 1: ANALIZĂ PILOT SINGUR ---
@app.post("/predict_and_analyze", tags=["Simulare Individuala"])
def predict_and_analyze(data: dict):
    nume_pilot = data.get('Nume_Pilot', 'Pilotul')
    este_ploaie = "ploaie" if data.get('Vreme_Cursa_1_ploaie', 0) == 1 else "vreme uscată"

    # Transformăm într-un DataFrame cu un singur rând și îmbogățim datele
    df_baza = pd.DataFrame([data])
    df_complet = pregateste_date_ml(df_baza)
    
    # Extragem datele reale aduse din Excel pentru a le trimite la Ollama
    podiumuri_reale = int(df_complet.iloc[0].get('Total_Podiumuri_Cariera', 0))
    puncte_masina = float(df_complet.iloc[0].get('Puncte_Acumulate_Constructor', 0))
    scor_depasire = float(df_complet.iloc[0].get('Scor_Depasire', 1.0))
    
    # Preluăm fix coloanele necesare modelului
    df_final_ml = df_complet[COLANE_ASTEPTATE]
    
    # Predicție
    prediction_raw = float(model_suprem.predict(df_final_ml)[0])
    loc_final = max(1, round(prediction_raw))
    
    # Analiză AI cu date reale
    prompt = (
        f"Ești un jurnalist sportiv român, expert în Formula 1. Analizează predicția pentru {nume_pilot}, "
        f"care pleacă de pe {data.get('Starting_Grid', 0)}. Podiumuri carieră: {podiumuri_reale}, "
        f"Puncte mașină: {puncte_masina}, Vreme: {este_ploaie}. Predicție finală: locul {loc_final}. "
        f"CERINȚĂ: Scrie o analiză scurtă, într-o limbă română impecabilă, "
        f"naturală și fluentă. Folosește vocabular specific motorsportului românesc. "
        f"Sunt strict interzise traducerile literale din engleză sau expresiile nenaturale."
    )
    
    response = ollama.generate(
        model="llama3.1", 
        prompt=prompt,
        options={'temperature': 0.5} # Am crescut temperatura
    )

    return {
        "pozitie_estimata": loc_final,
        "scor_brut": round(prediction_raw, 3),
        "analiza_ai": response['response']
    }

# --- ENDPOINT 2: SIMULARE GRILĂ COMPLETĂ ---
@app.post("/simulate_race", tags=["Simulare Cursa Completa"])
def simulate_race(drivers_data: List[dict]):
    if not drivers_data:
        return {"status": "error", "message": "Lista goală"}

    df_baza = pd.DataFrame(drivers_data)
    df_complet = pregateste_date_ml(df_baza)
    
    df_final_ml = df_complet[COLANE_ASTEPTATE]
    
    # Predicție XGBoost pentru toți
    scoruri_brute = model_suprem.predict(df_final_ml)
    df_complet['scor_brut'] = scoruri_brute
    
    # Clasament
    df_sortat = df_complet.sort_values(by='scor_brut').reset_index(drop=True)
    df_sortat['loc_final'] = df_sortat.index + 1
    df_sortat['pozitii_castigate'] = df_sortat['Starting_Grid'] - df_sortat['loc_final']
    
    # === GENERĂM ANIMAȚIA GIF ===
    import math
    
    date_animatie = []
    for _, rand in df_sortat.iterrows():
        nume_pilot = str(rand.get('Nume_Pilot', 'UNK'))
        nume_scurt = nume_pilot[:3].upper() # Luăm primele 3 litere
        
        id_brut = rand.get('EchipaID')
        
        # Dacă id_brut este gol/NaN (lipsă potrivire nume), punem echipa 9 (Alb) ca rezervă
        # Deoarece pd este deja importat global, folosim funcția isna()
        if pd.isna(id_brut):
            id_final = "9"
        else:
            id_final = str(int(id_brut)) # Transformă "1.0" în "1"
            
        culoare = CULORI_ECHIPE.get(id_final, "#FFFFFF")
        
        date_animatie.append({
            'nume': nume_scurt,
            'start': int(rand['Starting_Grid']),
            'end': int(rand['loc_final']),
            'culoare': culoare
        })
    
    # Extragem numele circuitului din prima mașină (toate rulează pe același circuit)
    nume_circuit_curent = str(df_sortat.iloc[0].get('Nume_Circuit', 'Monza'))
    
    # Trimitem noul parametru către funcția noastră actualizată
    genereaza_animatie_cursa(date_animatie, nume_circuit_curent, "race_animation.gif")
    # ============================

    # LLM
    circuit_name = df_sortat.iloc[0].get("Nume_Circuit", "Circuit")
    podium_nume = [
        df_sortat.iloc[0].get('Nume_Pilot', 'P1'), 
        df_sortat.iloc[1].get('Nume_Pilot', 'P2'), 
        df_sortat.iloc[2].get('Nume_Pilot', 'P3')
    ]
    pilot_zilei_idx = df_sortat['pozitii_castigate'].idxmax()
    pilot_zilei = df_sortat.iloc[pilot_zilei_idx]
    
    prompt = (
        f"Ești un comentator sportiv român pasionat de Formula 1. S-a încheiat cursa de la {circuit_name}. "
        f"Podiumul este: 1. {podium_nume[0]}, 2. {podium_nume[1]}, 3. {podium_nume[2]}. "
        f"Atracția cursei a fost {pilot_zilei.get('Nume_Pilot', 'un pilot')} care a urcat pe locul {pilot_zilei.get('loc_final', 0)} "
        f"(câștigând {pilot_zilei.get('pozitii_castigate', 0)} poziții). "
        f"CERINȚĂ: Scrie un rezumat entuziast. Folosește o limbă română absolut naturală, "
        f"cursivă și corectă gramatical. Exprimă-te ca un comentator TV real din România. "
        f"Fără fraze ciudate sau traduceri robotice."
    )
    
    response = ollama.generate(
        model="llama3.1", 
        prompt=prompt,
        options={'temperature': 0.6} # Am crescut temperatura pentru entuziasm
    )
    
    import time
    timestamp = int(time.time())
    
    coloane_raspuns = ['Nume_Pilot', 'Starting_Grid', 'loc_final', 'pozitii_castigate', 'scor_brut']
    coloane_existente = [col for col in coloane_raspuns if col in df_sortat.columns]
    clasament_json = df_sortat[coloane_existente].to_dict('records')
    
    return {
        "status": "success",
        "rezumat_cursa": response['response'],
        "clasament": clasament_json,
        # Adăugăm ?v=timestamp ca să spargem memoria cache a browserului!
        "animatie_url": f"/static/race_animation.gif?v={timestamp}" 
    }

# --- ENDPOINT 3: SCENARII CAMPIONAT MONDIAL (Multi-Pilot & Multi-Cursă) ---
@app.post("/championship_scenarios", tags=["Campionat"])
def championship_scenarios(data: dict):
    curse_ramase = int(data.get("curse_ramase", 1))
    piloti = data.get("piloti", [])
    
    if len(piloti) < 2:
        return {"status": "error", "message": "Ai nevoie de cel puțin 2 piloți."}
        
    piloti_sortati = sorted(piloti, key=lambda x: int(x["puncte"]), reverse=True)
    lider = piloti_sortati[0]
    urmaritori = piloti_sortati[1:]
    
    puncte_maxime_posibile = curse_ramase * 25
    puncte_f1 = {1: 25, 2: 18, 3: 15, 4: 12, 5: 10, 6: 8, 7: 6, 8: 4, 9: 2, 10: 1, "DNF": 0}
    
    scenarii_generate = []
    
    if curse_ramase == 1:
        # LOGICA PENTRU ULTIMA CURSĂ (Permutări de locuri)
        scenarii_generate.append(f"🏁 SCENARII PENTRU ULTIMA CURSĂ:\n")
        locuri_sigure_lider = []
        
        for loc_lider, pct_lider in puncte_f1.items():
            total_lider = lider["puncte"] + pct_lider
            conditii_urmaritori = []
            
            for u in urmaritori:
                puncte_necesare = total_lider - u["puncte"]
                if puncte_necesare < 25: 
                    loc_necesar = "Imposibil"
                    for loc_urm, pct_urm in puncte_f1.items():
                        if loc_urm != "DNF" and pct_urm > puncte_necesare: 
                            loc_necesar = str(loc_urm)
                    
                    if loc_necesar != "Imposibil":
                        conditii_urmaritori.append(f"{u['nume']} trebuie să termine minim pe locul {loc_necesar}")
            
            if not conditii_urmaritori:
                locuri_sigure_lider.append(str(loc_lider))
            else:
                nume_loc = f"locul {loc_lider}" if loc_lider != "DNF" else "Abandon (DNF)"
                scenarii_generate.append(f"Dacă {lider['nume']} termină pe {nume_loc} -> " + " SAU ".join(conditii_urmaritori) + ".")
        
        if locuri_sigure_lider:
            locuri_str = ", ".join(locuri_sigure_lider)
            scenarii_generate.insert(1, f"✅ {lider['nume']} este campion garantat dacă termină pe locurile: {locuri_str}.\n")
            
    else:
        # LOGICA PENTRU MAI MULTE CURSE (Numărul Magic)
        scenarii_generate.append(f"📊 ANALIZĂ PENTRU ULTIMELE {curse_ramase} CURSE:")
        scenarii_generate.append(f"Puncte maxime rămase în joc: {puncte_maxime_posibile} pct.\n")
        
        cel_mai_apropiat = urmaritori[0]
        # Presupunem că urmăritorul câștigă tot (25 pct * curse)
        puncte_tinta_urmaritor = cel_mai_apropiat["puncte"] + puncte_maxime_posibile
        
        # De câte puncte are nevoie liderul ca să îl bată chiar și în acel scenariu extrem
        magie = puncte_tinta_urmaritor - lider["puncte"]
        
        if magie <= 0:
            scenarii_generate.append(f"🏆 {lider['nume']} ESTE DEJA CAMPION MONDIAL MATEMATIC!")
        else:
            medie_necesara = magie / curse_ramase
            scenarii_generate.append(f"🎯 Pentru a garanta titlul, {lider['nume']} mai are nevoie de {magie} puncte în total.")
            scenarii_generate.append(f"Asta înseamnă o medie de minim {medie_necesara:.1f} puncte pe cursă, indiferent de ce fac ceilalți.\n")
            
        scenarii_generate.append("SITUAȚIA URMĂRITORILOR:")
        for u in urmaritori:
            diferenta = lider["puncte"] - u["puncte"]
            if diferenta >= puncte_maxime_posibile:
                scenarii_generate.append(f"❌ {u['nume']} (deficiență: {diferenta} pct) -> ELIMINAT matematic din lupta pentru titlu.")
            else:
                scenarii_generate.append(f"⚠️ {u['nume']} (deficiență: {diferenta} pct) -> Trebuie să recupereze în medie {(diferenta/curse_ramase):.1f} pct/cursă față de lider.")

    return {
        "status": "success",
        "rezumat_scenarii": "\n".join(scenarii_generate)
    }

# --- ENDPOINT 4: SCENARII CAMPIONATUL CONSTRUCTORILOR (Echipe) ---
@app.post("/constructors_championship", tags=["Campionat"])
def constructors_championship(data: dict):
    curse_ramase = int(data.get("curse_ramase", 1))
    echipe = data.get("echipe", [])
    
    if len(echipe) < 2:
        return {"status": "error", "message": "Ai nevoie de cel puțin 2 echipe."}
        
    echipe_sortate = sorted(echipe, key=lambda x: int(x["puncte"]), reverse=True)
    lider = echipe_sortate[0]
    urmaritori = echipe_sortate[1:]
    
    # O echipă poate lua maxim 43 puncte pe cursă (Locul 1 = 25, Locul 2 = 18)
    puncte_maxime_cursa = 43
    puncte_maxime_posibile = curse_ramase * puncte_maxime_cursa
    
    scenarii_generate = []
    scenarii_generate.append(f"🏎️ ANALIZĂ CAMPIONATUL CONSTRUCTORILOR ({curse_ramase} curse rămase):")
    scenarii_generate.append(f"Puncte maxime rămase în joc: {puncte_maxime_posibile} pct (câte {puncte_maxime_cursa} pct/cursă pt. o clasare 1-2).\n")
    
    cel_mai_apropiat = urmaritori[0]
    puncte_tinta_urmaritor = cel_mai_apropiat["puncte"] + puncte_maxime_posibile
    magie = puncte_tinta_urmaritor - lider["puncte"] + 1 # +1 pentru a fi sigur ca il bate
    
    if magie <= 0:
        scenarii_generate.append(f"🏆 ECHIPA {lider['nume'].upper()} ESTE CAMPIOANĂ MONDIALĂ MATEMATIC!")
    else:
        if curse_ramase == 1:
            scenarii_generate.append(f"🎯 Pentru a garanta titlul, ambele mașini {lider['nume']} trebuie să adune combinat minim {magie} puncte în această ultimă cursă.")
            scenarii_generate.append(f"(Indiferent de ce fac piloții de la {cel_mai_apropiat['nume']}).\n")
        else:
            medie_necesara = magie / curse_ramase
            scenarii_generate.append(f"🎯 Pentru a garanta titlul, {lider['nume']} mai are nevoie de {magie} puncte în total.")
            scenarii_generate.append(f"Asta înseamnă o medie de minim {medie_necesara:.1f} puncte pe cursă (ambele mașini combinat).\n")
            
    scenarii_generate.append("SITUAȚIA URMĂRITORILOR:")
    for u in urmaritori:
        diferenta = lider["puncte"] - u["puncte"]
        if diferenta >= puncte_maxime_posibile:
            scenarii_generate.append(f"❌ {u['nume']} (deficiență: {diferenta} pct) -> ELIMINATĂ matematic din lupta pentru titlu.")
        else:
            scenarii_generate.append(f"⚠️ {u['nume']} (deficiență: {diferenta} pct) -> Trebuie să recupereze în medie {(diferenta/curse_ramase):.1f} pct/cursă față de {lider['nume']}.")

    return {
        "status": "success",
        "rezumat_scenarii": "\n".join(scenarii_generate)
    }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)