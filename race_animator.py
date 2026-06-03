import numpy as np
import matplotlib             # <- ADĂUGAT
matplotlib.use('Agg')         # <- ADĂUGAT (Modul invizibil / Headless)
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter
import os
import json
from scipy.interpolate import splprep, splev

# ... restul codului rămâne exact la fel ...

# Culorile oficiale ale echipelor F1
CULORI_ECHIPE = {
    "1": "#FF8000", # McLaren
    "2": "#0600EF", # Red Bull
    "3": "#00D2BE", # Mercedes
    "4": "#DC0000", # Ferrari
    "5": "#005AFF", # Williams
    "6": "#6692FF", # RB
    "7": "#52E252", # Kick Sauber
    "8": "#006F62", # Aston Martin
    "9": "#FFFFFF", # Haas
    "10": "#FF87BC" # Alpine
}

def get_track_coordinates(nume_circuit):
    # 1. Încărcăm fișierul local cu coordonate
    cale_json = os.path.join(os.getcwd(), 'coordonate_circuite.json')
    try:
        with open(cale_json, 'r') as f:
            date_circuite = json.load(f)
    except Exception as e:
        print(f"❌ Eroare la citirea JSON-ului: {e}")
        return [], []
        
    # 2. Verificăm dacă circuitul cerut există în fișier (fallback la Monza)
    if nume_circuit not in date_circuite:
        print(f"⚠️ Circuitul '{nume_circuit}' nu e în JSON. Folosim Monza ca default.")
        nume_circuit = "Monza"
        
    x_raw = date_circuite[nume_circuit]['x']
    y_raw = date_circuite[nume_circuit]['y']
    
    # 3. Netezim curbele perfect folosind matematica Spline
    tck, u = splprep([x_raw, y_raw], s=50, per=True) 
    u_new = np.linspace(0, 1, 1500)
    x_smooth, y_smooth = splev(u_new, tck)
    
    return x_smooth, y_smooth

def genereaza_animatie_cursa(piloti_date, nume_circuit="Monza", nume_fisier="race_animation.gif"):
    print(f"🎨 Generăm animația pentru circuitul real: {nume_circuit}...")
    
    # Setăm proporții tipice de ecran de telefon (ex: 9 pe lățime, 18 pe înălțime)
    fig, ax = plt.subplots(figsize=(9, 18)) 
    
    # Sincronizăm EXACT culoarea de fundal cu aplicația de Android ca să devină invizibilă marginea
    fig.patch.set_facecolor('#080810')
    ax.set_facecolor('#080810')

    ax.axis('equal') # Păstrează scara reală 1:1 a circuitului GPS
    ax.axis('off')

    x_track, y_track = get_track_coordinates(nume_circuit)
    if len(x_track) == 0:
        return
    
    # Desenăm asfaltul
    ax.plot(x_track, y_track, color='#333333', linewidth=20, zorder=1)
    ax.plot(x_track, y_track, color='#FFFFFF', linewidth=1, linestyle='--', zorder=2)
    
    # Un punct roșu mare care reprezintă Linia de Start/Finish
    ax.plot(x_track[0], y_track[0], 'ro', markersize=12, zorder=3)

    # Fizica mașinilor pe circuit real
    dx = np.diff(x_track)
    dy = np.diff(y_track)
    distante = np.sqrt(dx**2 + dy**2)
    distanta_cumulata = np.concatenate(([0], np.cumsum(distante)))
    lungime_totala = distanta_cumulata[-1]
    
    nx = -dy / distante
    ny = dx / distante
    nx = np.append(nx, nx[-1])
    ny = np.append(ny, ny[-1])

    puncte_masini = []
    texte_nume = []
    for p in piloti_date:
        punct, = ax.plot([], [], 'o', color=p['culoare'], markersize=8, zorder=4)
        text = ax.text(0, 0, p['nume'], color='white', fontsize=7, fontweight='bold', zorder=5)
        puncte_masini.append(punct)
        texte_nume.append(text)

    total_frames = 1000
    
    def update(frame):
        progres = frame / total_frames
        for i, p in enumerate(piloti_date):
            pozitie_finala = p['end']
            
            distanta_tinta = (lungime_totala * 5) - (pozitie_finala * (lungime_totala * 0.015))
            distanta_curenta = progres * distanta_tinta
            pozitie_pe_tur = distanta_curenta % lungime_totala
            
            x_baza = np.interp(pozitie_pe_tur, distanta_cumulata, x_track)
            y_baza = np.interp(pozitie_pe_tur, distanta_cumulata, y_track)
            
            # Deoarece coordonatele GPS F1 sunt în metri reali (mii de unități)
            # offset-ul trebuie să fie mai mare, la fel și ridicarea textului
            offset = 150 if pozitie_finala % 2 == 0 else -150
            nx_curent = np.interp(pozitie_pe_tur, distanta_cumulata, nx)
            ny_curent = np.interp(pozitie_pe_tur, distanta_cumulata, ny)
            
            x = x_baza + nx_curent * offset
            y = y_baza + ny_curent * offset
            
            puncte_masini[i].set_data([x], [y])
            texte_nume[i].set_position((x, y + 250)) # Ridicăm textul mai sus de punct
            
        return puncte_masini + texte_nume

    anim = FuncAnimation(fig, update, frames=total_frames, interval=30, blit=True)
    cale_salvare = os.path.join(os.getcwd(), nume_fisier)
    anim.save(cale_salvare, writer=PillowWriter(fps=25))
    plt.close()
    print(f"✅ Animația a fost salvată!")

# TESTARE DIRECTĂ
if __name__ == "__main__":
    piloti_test = [
        {'nume': 'NOR', 'start': 4, 'end': 1, 'culoare': CULORI_ECHIPE['1']},
        {'nume': 'VER', 'start': 1, 'end': 2, 'culoare': CULORI_ECHIPE['2']}
    ]
    # Schimbă aici să testezi cum arată 'Spa-Francorchamps', 'Monaco', etc.
    genereaza_animatie_cursa(piloti_test, nume_circuit="Monaco", nume_fisier="test_circuit.gif")