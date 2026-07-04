# F1 INTELLIGENCE HUB - Aplicație Android pentru Predicții și Analiză în Formula 1 bazată pe Învățare Automată 

**Autor:** Genovu Bianca-Maria


**https://github.com/BiancaGenovu/F1-Intelligence-Hub**

### Structura proiectului

Repository-ul este organizat în două componente principale:

- **`main/`** — conține tot ce ține de partea de server (backend):
  - `main_v2.py` — server FastAPI (rulează pe portul 8001)
  - `extrage_f1_2025grid.py` — script de extragere a datelor istorice (2020–2025) folosind FastF1, pentru grila 2025 (20 de piloți, 24 de circuite)
  - `F1_2025Grid.xlsx` - fișierul care conține istoricul tuturor piloților pe fiecare circuit în parte
  - `model_f1_nou.json` — modelul XGBoost antrenat
  - `race_animator.py`, `genereaza_quiz_gifuri.py`, `download_circuite.py`, `vezi_circuite.py` — scripturi auxiliare pentru generarea conținutului vizual și a datelor despre circuite
  - `coordonate_circuite.json` — date de coordonate pentru circuite

- **`android/`** — conține aplicația client, scrisă în Android Studio

## 2. Pași de compilare, instalare și lansare

### 2.1 Server (folderul `main/`)

Necesită Python instalat, cu pachetele folosite de proiect (FastAPI, XGBoost, Ollama etc.) deja instalate.

Pornire server:
```bash
cd main
python main_v2.py
```

Serverul pornește pe `http://localhost:8001`.

### 2.2 Aplicație Android (folderul `android/`)

1. Deschide folderul `android/` în Android Studio
2. Așteaptă sincronizarea Gradle
3. Apasă `Run ▶` pentru a instala și lansa aplicația pe emulator sau pe un dispozitiv conectat

> Serverul (pasul 2.1) trebuie să fie pornit înainte de a folosi aplicația Android, pentru ca predicțiile să funcționeze.

