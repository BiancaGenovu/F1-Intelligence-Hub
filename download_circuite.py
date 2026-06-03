import fastf1
import json
import os
import logging

# Pentru versiunile noi de fastf1: Așa ascundem mesajele inutile din consolă
logging.getLogger('fastf1').setLevel(logging.ERROR)

# Setăm folderul de cache (obligatoriu)
if not os.path.exists('cache'):
    os.makedirs('cache')
fastf1.Cache.enable_cache('cache')

# Aici am legat EXACT numele din Excel-ul tău cu serverele oficiale F1
circuite_de_descarcat = {
    "Australia": {"year": 2023, "event": "Australia"},
    "China": {"year": 2024, "event": "China"}, 
    "Japan": {"year": 2023, "event": "Japan"},
    "Bahrain": {"year": 2023, "event": "Bahrain"},
    "Saudi Arabia": {"year": 2023, "event": "Saudi Arabia"},
    "Miami": {"year": 2023, "event": "Miami"},
    "Emilia-Romagna": {"year": 2024, "event": "Emilia Romagna"}, 
    "Monaco": {"year": 2023, "event": "Monaco"},
    "Spain": {"year": 2023, "event": "Spain"},
    "Canada": {"year": 2023, "event": "Canada"},
    "Austria": {"year": 2023, "event": "Austria"},
    "Great Britain": {"year": 2023, "event": "Great Britain"},
    "Belgium": {"year": 2023, "event": "Belgium"},
    "Hungary": {"year": 2023, "event": "Hungary"},
    "Netherlands": {"year": 2023, "event": "Netherlands"},
    "Italy": {"year": 2023, "event": "Italy"}, 
    "Azerbaijan": {"year": 2023, "event": "Azerbaijan"},
    "Singapore": {"year": 2023, "event": "Singapore"},
    "United States": {"year": 2023, "event": "United States Grand Prix"}, # Corectat
    "Mexico": {"year": 2023, "event": "Mexico"},
    "Brazil": {"year": 2023, "event": "Brazil"},
    "Las Vegas": {"year": 2023, "event": "Las Vegas"},
    "Qatar": {"year": 2023, "event": "Qatar Grand Prix"}, # Corectat
    "Abu Dhabi": {"year": 2023, "event": "Abu Dhabi"}
}

track_data = {}

print("🏎️ Începem descărcarea coordonatelor pentru TOATE cele 24 de circuite...")
print("⏳ Aceasta poate dura 5-10 minute, ia-ți o cafea între timp!")

for nume_circuit, info in circuite_de_descarcat.items():
    print(f"\n-> Descarc: {nume_circuit} ({info['year']})...")
    try:
        session = fastf1.get_session(info['year'], info['event'], 'Q')
        session.load(telemetry=True, weather=False, messages=False)
        fastest_lap = session.laps.pick_fastest()
        telemetry = fastest_lap.get_telemetry()
        
        x = telemetry['X'].tolist()
        y = telemetry['Y'].tolist()
        
        # Luăm doar 1 punct din 10 ca să facem fișierul foarte ușor și rapid de citit
        x_redus = x[::10]
        y_redus = y[::10]
        
        # Închidem bucla circuitului
        x_redus.append(x_redus[0])
        y_redus.append(y_redus[0])
        
        track_data[nume_circuit] = {
            "x": x_redus,
            "y": y_redus
        }
        print(f"✅ {nume_circuit} a fost salvat cu succes!")
        
    except Exception as e:
        print(f"❌ Eroare la {nume_circuit}: {e}")

# Salvăm totul într-un fișier local
with open('coordonate_circuite.json', 'w') as f:
    json.dump(track_data, f)

print("\n🎉 GATA! Baza ta de date cu circuite GPS este acum completă 100%!")