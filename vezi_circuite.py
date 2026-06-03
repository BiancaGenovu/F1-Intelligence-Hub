import json
import matplotlib.pyplot as plt
import os

# 1. Creăm un folder automat unde să salvăm pozele
folder_poze = "poze_circuite"
if not os.path.exists(folder_poze):
    os.makedirs(folder_poze)

# 2. Citim datele strict de la tine de pe laptop (local)
try:
    with open('coordonate_circuite.json', 'r') as f:
        date_circuite = json.load(f)
except Exception as e:
    print(f"Eroare la citirea fișierului: {e}")
    exit()

# Afișăm în consolă ce am găsit în fișier
circuite_gasite = list(date_circuite.keys())
print(f"📊 Am găsit {len(circuite_gasite)} circuite în fișierul tău JSON!")
print(f"Lista acestora: {', '.join(circuite_gasite)}\n")
print("📸 Începem generarea pozelor...")

# 3. Generăm câte o poză pentru fiecare circuit
for nume_circuit, coordonate in date_circuite.items():
    # Setăm pânza albă
    plt.figure(figsize=(6, 6))
    
    # Desenăm circuitul (linia neagră)
    plt.plot(coordonate['x'], coordonate['y'], color='black', linewidth=4)
    
    # Punem un punct roșu la linia de Start/Finish
    plt.plot(coordonate['x'][0], coordonate['y'][0], 'ro', markersize=10)
    
    # Setăm axele egale ca să nu se deformeze formele!
    plt.axis('equal')
    plt.axis('off') # Ascundem grila și numerele de pe fundal
    
    # Punem numele circuitului sus
    plt.title(nume_circuit, fontsize=16, fontweight='bold')
    
    # Salvăm poza
    cale_salvare = os.path.join(folder_poze, f"{nume_circuit}.png")
    plt.savefig(cale_salvare, bbox_inches='tight', dpi=150)
    plt.close() # Închidem poza din memorie ca să trecem la următoarea

print(f"✅ GATA! Intră în folderul '{folder_poze}' din proiectul tău ca să vezi cele {len(circuite_gasite)} de imagini!")