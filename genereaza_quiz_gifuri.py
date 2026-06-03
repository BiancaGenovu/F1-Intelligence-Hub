"""
genereaza_quiz_gifuri.py
========================
Genereaza cate un GIF de 10 secunde pentru fiecare din cele 24 de circuite.
Circuitul se deseneaza treptat — 10% pe secunda — pentru jocul de quiz.

Rulare:
    python genereaza_quiz_gifuri.py

Output:
    Folder 'quiz_circuite/' cu 24 de fisiere .gif
    Acestea se pun in folderul static/ al serverului FastAPI.
"""

import json
import numpy as np
import os
import sys

import matplotlib
matplotlib.use('Agg')   # headless, fara fereastra
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation, PillowWriter
from scipy.interpolate import splprep, splev

# ─────────────────────────────────────────────────────────────
# CONFIGURARE
# ─────────────────────────────────────────────────────────────

JSON_PATH      = 'coordonate_circuite.json'   # fisierul tau cu coordonate GPS
OUTPUT_FOLDER  = 'quiz_circuite'              # unde se salveaza GIF-urile
FPS            = 15                           # cadre pe secunda (15 = fisiere mici, animatie fluida)
DURATA_SEC     = 10                           # durata totala reveal
TOTAL_FRAMES   = FPS * DURATA_SEC             # 150 de cadre total
SMOOTH_POINTS  = 400                          # puncte spline — mai multe = curbe mai fine
IMG_SIZE_INCH  = 5                            # dimensiunea figurii in inch (square)
DPI            = 80                           # 5 * 80 = 400x400 pixeli final

# Culori — identice cu tema dark din aplicatia Android
CULOARE_FUNDAL     = '#080810'
CULOARE_CIRCUIT    = '#FFFFFF'
CULOARE_GLOW       = '#E10600'
CULOARE_START_DOT  = '#E10600'

# ─────────────────────────────────────────────────────────────
# FUNCTII
# ─────────────────────────────────────────────────────────────

def incarca_si_netezeste(x_raw: list, y_raw: list, num_points: int = SMOOTH_POINTS):
    """
    Preia coordonatele brute GPS si le netezeste cu spline cubic.
    Returneaza doua array-uri numpy cu coordonate netede.
    """
    try:
        tck, u = splprep([x_raw, y_raw], s=50, per=True)
        u_new = np.linspace(0, 1, num_points)
        x_smooth, y_smooth = splev(u_new, tck)
        return x_smooth, y_smooth
    except Exception as e:
        print(f"   ⚠️  Spline esuat, folosesc punctele brute: {e}")
        return np.array(x_raw), np.array(y_raw)


def genereaza_gif_circuit(nume_circuit: str, x: np.ndarray, y: np.ndarray,
                           output_path: str) -> bool:
    """
    Genereaza un GIF de DURATA_SEC secunde in care circuitul se deseneaza
    treptat de la 0% la 100%.
    """
    fig, ax = plt.subplots(figsize=(IMG_SIZE_INCH, IMG_SIZE_INCH))
    fig.patch.set_facecolor(CULOARE_FUNDAL)
    ax.set_facecolor(CULOARE_FUNDAL)
    ax.set_aspect('equal')
    ax.axis('off')

    # Margini cu padding
    padding_x = (max(x) - min(x)) * 0.12
    padding_y = (max(y) - min(y)) * 0.12
    ax.set_xlim(min(x) - padding_x, max(x) + padding_x)
    ax.set_ylim(min(y) - padding_y, max(y) + padding_y)

    total_points = len(x)

    # ── LAYERE DE DESEN ──────────────────────────────────────
    # Glow (linie mai groasa, semi-transparenta sub linie)
    linie_glow, = ax.plot([], [], color=CULOARE_GLOW, linewidth=6,
                          alpha=0.15, solid_capstyle='round', zorder=1)

    # Linia principala a circuitului
    linie_circuit, = ax.plot([], [], color=CULOARE_CIRCUIT, linewidth=2.5,
                              solid_capstyle='round', zorder=2)

    # Punctul de start/finish (apare din primul frame)
    dot_start, = ax.plot(x[0], y[0], 'o', color=CULOARE_START_DOT,
                          markersize=7, zorder=3)

    # Capul animatiei — un punct luminos care merge pe circuit
    dot_cap, = ax.plot([], [], 'o', color='#FFFFFF', markersize=5, zorder=4)

    def update(frame):
        # Cate puncte afisam la frame-ul curent
        # Adaugam o usoara accelerare la inceput (easing)
        progres_lin = (frame + 1) / TOTAL_FRAMES  # 0.0 → 1.0 liniar
        progres_ease = progres_lin ** 0.85          # usor mai rapid la inceput
        num_pts = max(2, int(progres_ease * total_points))

        linie_glow.set_data(x[:num_pts], y[:num_pts])
        linie_circuit.set_data(x[:num_pts], y[:num_pts])

        # Capul animatiei urmareste varful
        dot_cap.set_data([x[num_pts - 1]], [y[num_pts - 1]])

        return linie_glow, linie_circuit, dot_cap, dot_start

    anim = FuncAnimation(
        fig, update,
        frames=TOTAL_FRAMES,
        interval=1000 // FPS,
        blit=True
    )

    try:
        writer = PillowWriter(fps=FPS)
        anim.save(output_path, writer=writer, dpi=DPI)
        plt.close(fig)
        return True
    except Exception as e:
        plt.close(fig)
        print(f"   ❌ Eroare la salvare: {e}")
        return False


# ─────────────────────────────────────────────────────────────
# SCRIPT PRINCIPAL
# ─────────────────────────────────────────────────────────────

def main():
    # 1. Incarcam coordonatele
    if not os.path.exists(JSON_PATH):
        print(f"❌ Fisierul '{JSON_PATH}' nu a fost gasit!")
        print("   Pune 'coordonate_circuite.json' in acelasi folder cu scriptul.")
        sys.exit(1)

    with open(JSON_PATH, 'r') as f:
        date_circuite = json.load(f)

    circuite = list(date_circuite.keys())
    print(f"📁 Am gasit {len(circuite)} circuite in JSON.")
    print(f"⚙️  Configuratie: {FPS}fps × {DURATA_SEC}s = {TOTAL_FRAMES} cadre | {IMG_SIZE_INCH*DPI}x{IMG_SIZE_INCH*DPI}px\n")

    # 2. Cream folderul de output
    os.makedirs(OUTPUT_FOLDER, exist_ok=True)
    print(f"📂 GIF-urile se vor salva in: '{OUTPUT_FOLDER}/'\n")
    print("─" * 50)

    # 3. Generam GIF-ul pentru fiecare circuit
    reusit   = 0
    esuat    = 0
    sarit    = 0

    for idx, nume in enumerate(circuite, 1):
        output_path = os.path.join(OUTPUT_FOLDER, f"{nume}.gif")

        # Skip daca exista deja (util daca scriptul e intrerupt si reluai)
        if os.path.exists(output_path):
            size_kb = os.path.getsize(output_path) // 1024
            print(f"[{idx:2}/{len(circuite)}] ⏭️  {nume} — deja exista ({size_kb} KB), sarit.")
            sarit += 1
            continue

        print(f"[{idx:2}/{len(circuite)}] ⏳ {nume}...", end=' ', flush=True)

        x_raw = date_circuite[nume]['x']
        y_raw = date_circuite[nume]['y']

        x_smooth, y_smooth = incarca_si_netezeste(x_raw, y_raw)

        ok = genereaza_gif_circuit(nume, x_smooth, y_smooth, output_path)

        if ok:
            size_kb = os.path.getsize(output_path) // 1024
            print(f"✅ salvat ({size_kb} KB)")
            reusit += 1
        else:
            print(f"❌ esuat")
            esuat += 1

    # 4. Sumar final
    print("\n" + "─" * 50)
    print(f"🏁 GATA!")
    print(f"   ✅ Generate cu succes : {reusit}")
    print(f"   ⏭️  Sarite (existau)   : {sarit}")
    print(f"   ❌ Esecuri             : {esuat}")
    print(f"\n📂 GIF-urile se afla in: '{os.path.abspath(OUTPUT_FOLDER)}/'")
    print(f"\n💡 Urmatorul pas:")
    print(f"   Copiaza folderul '{OUTPUT_FOLDER}/' langa 'main_v2.py'")
    print(f"   si adauga in main_v2.py:")
    print(f"   app.mount(\"/quiz\", StaticFiles(directory=\"quiz_circuite\"), name=\"quiz\")")
    print(f"   Accesibil la: http://10.0.2.2:8001/quiz/Monaco.gif")


if __name__ == "__main__":
    main()