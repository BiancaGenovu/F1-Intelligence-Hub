"""
==============================================================================
EXTRACTOR DATE F1 - Grila 2025
Extrage date din sezoanele 2020-2025, filtrate la:
  - Doar cei 20 de piloti din grila 2025
  - Doar circuitele din calendarul 2025

PLAN TRAIN / TEST:
  - TRAIN: 2020 + 2021 + 2022 + 2023 + 2024 (tot) + 2025 rundele 1-13
  - TEST:  2025 rundele 14-24

RETRY LOGIC:
  - 3 incercari per sesiune
  - 25 minute pauza intre incercari (elimina complet problema de rate limit)

RULARE:
  python extrage_f1_2025grid.py
==============================================================================
"""

import fastf1
import pandas as pd
import numpy as np
import os
import warnings
import time
from datetime import datetime

warnings.filterwarnings("ignore")

# ─────────────────────────────────────────────────────────────
# CONFIGURATIE
# ─────────────────────────────────────────────────────────────

CACHE_DIR        = "./cache"
OUTPUT_EXCEL     = "F1_2025Grid.xlsx"
SEZOANE          = [2020, 2021, 2022, 2023, 2024, 2025]
SPLIT_2025_ROUND = 13       # round <= 13 → Train | round >= 14 → Test

MAX_RETRIES        = 3
RETRY_DELAY_SEC    = 25 * 60   # 25 minute — elimina sigur rate limit-ul de 1h

# ─────────────────────────────────────────────────────────────
# PILOTII GRILEI 2025 (abrevieri FastF1)
# ─────────────────────────────────────────────────────────────
PILOTI_2025 = {
    "VER",  # Max Verstappen      — Red Bull
    "LAW",  # Liam Lawson         — Red Bull
    "LEC",  # Charles Leclerc     — Ferrari
    "HAM",  # Lewis Hamilton      — Ferrari
    "NOR",  # Lando Norris        — McLaren
    "PIA",  # Oscar Piastri       — McLaren
    "RUS",  # George Russell      — Mercedes
    "ANT",  # Kimi Antonelli      — Mercedes
    "ALO",  # Fernando Alonso     — Aston Martin
    "STR",  # Lance Stroll        — Aston Martin
    "GAS",  # Pierre Gasly        — Alpine
    "DOO",  # Jack Doohan         — Alpine
    "TSU",  # Yuki Tsunoda        — RB
    "HAD",  # Isack Hadjar        — RB
    "ALB",  # Alexander Albon     — Williams
    "SAI",  # Carlos Sainz        — Williams
    "HUL",  # Nico Hulkenberg     — Sauber/Audi
    "BOR",  # Gabriel Bortoleto   — Sauber/Audi
    "OCO",  # Esteban Ocon        — Haas
    "BEA",  # Oliver Bearman      — Haas
}

# ─────────────────────────────────────────────────────────────
# CIRCUITELE CALENDARULUI 2025
# FastF1 foloseste Location (orasul/locatia), nu numele oficial
# ─────────────────────────────────────────────────────────────
CIRCUITE_2025 = {
    "Sakhir",           # R1  Bahrain
    "Jeddah",           # R2  Arabia Saudita
    "Melbourne",        # R3  Australia
    "Suzuka",           # R4  Japonia
    "Shanghai",         # R5  China
    "Miami",            # R6  Miami
    "Imola",            # R7  Emilia Romagna
    "Monaco",           # R8  Monaco
    "Barcelona",        # R9  Spania
    "Montréal",         # R10 Canada
    "Spielberg",        # R11 Austria
    "Silverstone",      # R12 Marea Britanie
    "Spa-Francorchamps",# R13 Belgia
    "Budapest",         # R14 Ungaria
    "Zandvoort",        # R15 Olanda
    "Monza",            # R16 Italia
    "Baku",             # R17 Azerbaijan
    "Marina Bay",       # R18 Singapore
    "Austin",           # R19 SUA
    "Mexico City",      # R20 Mexic
    "São Paulo",        # R21 Brazilia
    "Las Vegas",        # R22 Las Vegas
    "Lusail",           # R23 Qatar
    "Yas Island",       # R24 Abu Dhabi
}

# ─────────────────────────────────────────────────────────────
# SETUP
# ─────────────────────────────────────────────────────────────

os.makedirs(CACHE_DIR, exist_ok=True)
fastf1.Cache.enable_cache(CACHE_DIR)

import logging
logging.getLogger("fastf1").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)

try:
    from tqdm import tqdm
    TQDM_OK = True
except ImportError:
    TQDM_OK = False

COMPOUND_MAP = {
    "SOFT": 3, "MEDIUM": 2, "HARD": 1,
    "INTERMEDIATE": 4, "WET": 5,
    "HYPERSOFT": 3, "SUPERSOFT": 3, "ULTRASOFT": 3,
    "SUPERHARD": 1, "UNKNOWN": 0, "NONE": 0, "": 0
}


# ─────────────────────────────────────────────────────────────
# UTILITARE
# ─────────────────────────────────────────────────────────────

def td_to_sec(td):
    try:
        if pd.isna(td):
            return np.nan
        if isinstance(td, pd.Timedelta):
            return td.total_seconds()
        return float(td)
    except Exception:
        return np.nan


def safe_load_session(an, round_num, session_type, **kwargs):
    """
    Incarca o sesiune FastF1 cu retry logic robust.
    La rate limit asteapta 25 minute inainte de retry — elimina complet problema.
    """
    # Verificam daca sesiunea a avut loc deja
    try:
        event = fastf1.get_event(an, round_num)
        date_map = {"R": "Session5Date", "Q": "Session4Date",
                    "S": "Session3Date", "SQ": "Session2Date"}
        date_key = date_map.get(session_type, "EventDate")
        session_date = pd.to_datetime(event.get(date_key, event["EventDate"]))
        if pd.Timestamp.now() < session_date:
            return None  # cursa viitoare
    except Exception:
        pass

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            session = fastf1.get_session(an, round_num, session_type)
            session.load(**kwargs)
            return session
        except Exception as e:
            err_str = str(e).lower()

            # Sesiune inexistenta (ex: nu e sprint weekend) — nu reincercam
            if any(x in err_str for x in ["not found", "no session", "404", "invalid"]):
                return None

            is_rate_limit = "500 calls" in str(e) or "ratelimit" in err_str

            if attempt < MAX_RETRIES:
                if is_rate_limit:
                    wait_min = RETRY_DELAY_SEC // 60
                    print(f"\n      ⏳ RATE LIMIT — astept {wait_min} minute "
                          f"(tentativa {attempt}/{MAX_RETRIES})...")
                    # Afisam countdown la fiecare minut
                    for minute_left in range(wait_min, 0, -1):
                        print(f"         {minute_left} minute ramase...", end="\r")
                        time.sleep(60)
                    print(f"         Reiau descarcarea...              ")
                else:
                    print(f"      ⚠️  Tentativa {attempt}/{MAX_RETRIES}: {e} — reiau in 30s...")
                    time.sleep(30)
            else:
                label = "❌ Rate limit epuizat" if is_rate_limit else "❌ Indisponibil"
                print(f"      {label} dupa {MAX_RETRIES} tentative: {session_type} {an} R{round_num}")
                return None

    return None


# ─────────────────────────────────────────────────────────────
# EXTRACTIE CALIFICARI
# ─────────────────────────────────────────────────────────────

def extrage_calificari(session_quali):
    rezultate = {}
    if session_quali is None:
        return rezultate
    try:
        results = session_quali.results
        if results is None or results.empty:
            return rezultate

        for _, row in results.iterrows():
            driver = str(row.get("DriverNumber", "")).strip()
            abbr   = str(row.get("Abbreviation", "")).strip().upper()
            if not driver or driver == "nan":
                continue
            if abbr not in PILOTI_2025:
                continue  # Filtram pilotii care nu sunt in grila 2025

            q1 = td_to_sec(row.get("Q1"))
            q2 = td_to_sec(row.get("Q2"))
            q3 = td_to_sec(row.get("Q3"))
            timpi = [t for t in [q3, q2, q1] if not np.isnan(t)]
            best_q = timpi[0] if timpi else np.nan

            rezultate[driver] = {
                "Q1_sec": q1, "Q2_sec": q2, "Q3_sec": q3,
                "BestQ_sec": best_q,
            }

        # Gap fata de pole
        pole_time = min(
            (v["BestQ_sec"] for v in rezultate.values() if not np.isnan(v["BestQ_sec"])),
            default=np.nan
        )
        for d in rezultate:
            best = rezultate[d]["BestQ_sec"]
            if not np.isnan(best) and not np.isnan(pole_time) and pole_time > 0:
                rezultate[d]["GapToPole_sec"] = round(best - pole_time, 4)
                rezultate[d]["GapToPole_pct"] = round((best - pole_time) / pole_time * 100, 4)
            else:
                rezultate[d]["GapToPole_sec"] = np.nan
                rezultate[d]["GapToPole_pct"] = np.nan

        # Timpi sectoare
        try:
            laps = session_quali.laps
            if laps is not None and not laps.empty:
                best_laps = (
                    laps.pick_quicklaps()
                    .groupby("DriverNumber", group_keys=False)
                    .apply(lambda x: x.sort_values("LapTime").head(1))
                )
                for _, lap in best_laps.iterrows():
                    drv = str(lap["DriverNumber"])
                    if drv in rezultate:
                        rezultate[drv]["Sector1_sec"] = td_to_sec(lap.get("Sector1Time"))
                        rezultate[drv]["Sector2_sec"] = td_to_sec(lap.get("Sector2Time"))
                        rezultate[drv]["Sector3_sec"] = td_to_sec(lap.get("Sector3Time"))
        except Exception:
            pass

    except Exception as e:
        print(f"      ⚠️  Eroare calificari: {e}")

    return rezultate


# ─────────────────────────────────────────────────────────────
# EXTRACTIE CURSA
# ─────────────────────────────────────────────────────────────

def calc_pit_stops(driver_laps):
    """Calculeaza pit stop-uri corect: PitInTime(N) → PitOutTime(N+1)"""
    durations = []
    try:
        df = driver_laps.sort_values("LapNumber").reset_index(drop=True)
        for i in range(len(df) - 1):
            pit_in  = td_to_sec(df.at[i,   "PitInTime"])
            pit_out = td_to_sec(df.at[i+1, "PitOutTime"])
            if np.isnan(pit_in) or np.isnan(pit_out):
                continue
            dur = pit_out - pit_in
            if 15 < dur < 60:
                durations.append(round(dur, 3))
    except Exception:
        pass
    n   = len(durations)
    avg = round(np.mean(durations), 3) if durations else np.nan
    mn  = round(min(durations), 3)     if durations else np.nan
    return n, avg, mn


def get_lap_times_clean(driver_laps):
    """Extrage timpii de tura valizi, fara SC si outlieri IQR."""
    try:
        has_deleted = "Deleted" in driver_laps.columns
        mask = driver_laps["LapTime"].notna()
        if has_deleted:
            mask &= (driver_laps["Deleted"].fillna(False) == False)
        if "TrackStatus" in driver_laps.columns:
            mask &= ~driver_laps["TrackStatus"].str.contains("5|6|7", na=False, regex=True)

        valid = driver_laps[mask]
        if valid.empty:
            return np.nan, np.nan, np.nan

        times = valid["LapTime"].apply(td_to_sec).dropna()
        if times.empty:
            return np.nan, np.nan, np.nan

        if len(times) > 4:
            q75, q25 = times.quantile(0.75), times.quantile(0.25)
            times = times[times <= q75 + 1.5 * (q75 - q25)]

        if times.empty:
            return np.nan, np.nan, np.nan

        return round(times.median(), 4), round(times.min(), 4), \
               (round(times.std(), 4) if len(times) > 2 else np.nan)
    except Exception:
        return np.nan, np.nan, np.nan


def extrage_cursa(session_race, is_sprint=False):
    rezultate = {}
    if session_race is None:
        return rezultate
    try:
        results = session_race.results
        laps    = session_race.laps
        if results is None or results.empty:
            return rezultate

        # Meteo
        air_temp = track_temp = humidity = wind = np.nan
        rainfall = 0
        try:
            w = session_race.weather_data
            if w is not None and not w.empty:
                air_temp   = round(float(w["AirTemp"].mean()), 1)
                track_temp = round(float(w["TrackTemp"].mean()), 1)
                rainfall   = int(w["Rainfall"].any())
                humidity   = round(float(w["Humidity"].mean()), 1)
                if "WindSpeed" in w.columns:
                    wind = round(float(w["WindSpeed"].mean()), 1)
        except Exception:
            pass

        for _, row in results.iterrows():
            driver = str(row.get("DriverNumber", "")).strip()
            abbr   = str(row.get("Abbreviation", "")).strip().upper()
            if not driver or driver == "nan":
                continue
            if abbr not in PILOTI_2025:
                continue  # Filtram pilotii care nu sunt in grila 2025

            # DNF
            status  = str(row.get("Status", "")).lower()
            dnf_kws = ["retired", "accident", "collision", "mechanical",
                       "disqualified", "did not", "power unit", "gearbox",
                       "hydraulics", "brakes", "suspension", "engine",
                       "electrical", "overheating", "dnf"]
            dnf = 1 if any(k in status for k in dnf_kws) else 0

            # Pozitii
            try:
                final_pos = int(row.get("Position") or 20)
            except (ValueError, TypeError):
                final_pos = 20

            try:
                grid_pos = int(row.get("GridPosition") or final_pos)
                if grid_pos == 0:
                    grid_pos = 20
            except (ValueError, TypeError):
                grid_pos = final_pos

            try:
                points = float(row.get("Points") or 0)
            except (ValueError, TypeError):
                points = 0.0

            # Ture
            median_lap = fastest_lap = consistency = np.nan
            num_pits = 0
            avg_pit = min_pit = np.nan
            start_compound = 0
            sc_laps = 0

            if laps is not None and not laps.empty:
                try:
                    dlaps = laps.pick_drivers(driver)
                    if not dlaps.empty:
                        median_lap, fastest_lap, consistency = get_lap_times_clean(dlaps)
                        if not is_sprint:
                            num_pits, avg_pit, min_pit = calc_pit_stops(dlaps)
                        lap1 = dlaps[dlaps["LapNumber"] == 1]
                        if not lap1.empty:
                            start_compound = COMPOUND_MAP.get(
                                str(lap1.iloc[0].get("Compound", "")).upper(), 0)
                        if "TrackStatus" in dlaps.columns:
                            sc_laps = int(dlaps["TrackStatus"]
                                         .str.contains("5|6", na=False, regex=True).sum())
                except Exception:
                    pass

            rezultate[driver] = {
                "FinalPosition": final_pos, "GridPosition": grid_pos,
                "Points": points, "DNF": dnf,
                "Status": str(row.get("Status", "")),
                "Abbreviation": abbr,
                "TeamName": str(row.get("TeamName", "")),
                "IsSprintResult": 1 if is_sprint else 0,
                "NumPitStops": num_pits, "AvgPitTime_sec": avg_pit,
                "MinPitTime_sec": min_pit, "StartCompound": start_compound,
                "NumSafetyCarLaps": sc_laps,
                "MedianLapTime_sec": median_lap, "FastestLap_sec": fastest_lap,
                "LapConsistency_std": consistency,
                "AirTemp": air_temp, "TrackTemp": track_temp,
                "Rainfall": rainfall, "Humidity": humidity, "WindSpeed": wind,
            }

    except Exception as e:
        print(f"      ⚠️  Eroare cursa: {e}")

    return rezultate


# ─────────────────────────────────────────────────────────────
# EXTRACTIE SEZON
# ─────────────────────────────────────────────────────────────

def extrage_sezon(an, rows):
    print(f"\n{'='*60}")
    print(f"  SEZON {an}")
    print(f"{'='*60}")

    try:
        schedule = fastf1.get_event_schedule(an, include_testing=False)
    except Exception as e:
        print(f"  ❌ Calendar indisponibil: {e}")
        return

    race_events = schedule[schedule["RoundNumber"] > 0].copy()

    iterator = (tqdm(race_events.iterrows(), total=len(race_events),
                     desc=f"  {an}", ncols=90)
                if TQDM_OK else race_events.iterrows())

    for _, event in iterator:
        round_num    = int(event["RoundNumber"])
        event_name   = event["EventName"]
        circuit_name = str(event.get("Location", event_name))
        event_format = str(event.get("EventFormat", "conventional")).lower()
        is_sprint    = "sprint" in event_format

        try:
            event_date = pd.to_datetime(event["EventDate"]).strftime("%Y-%m-%d")
        except Exception:
            event_date = "N/A"

        # Filtram circuitele care nu sunt in calendarul 2025
        # (pentru sezoanele 2020-2024 pastram doar cursele de pe circuitele din 2025)
        if circuit_name not in CIRCUITE_2025:
            if not TQDM_OK:
                print(f"  [{round_num:02d}] {circuit_name} — ⏭  nu e in calendarul 2025, skip")
            continue

        if not TQDM_OK:
            print(f"\n  [{round_num:02d}] {circuit_name} ({event_date})")

        # Calificari
        session_quali = safe_load_session(
            an, round_num, "Q",
            laps=True, telemetry=False, weather=False, messages=False)
        date_quali = extrage_calificari(session_quali)

        # Sprint (daca exista)
        date_sprint = {}
        if is_sprint:
            session_sprint = safe_load_session(
                an, round_num, "S",
                laps=True, telemetry=False, weather=True, messages=False)
            if session_sprint:
                date_sprint = extrage_cursa(session_sprint, is_sprint=True)

        # Cursa principala
        session_race = safe_load_session(
            an, round_num, "R",
            laps=True, telemetry=False, weather=True, messages=False)
        if session_race is None:
            if not TQDM_OK:
                print("      ⏭  Cursa indisponibila — skip")
            continue

        date_cursa = extrage_cursa(session_race)
        if not date_cursa:
            continue

        for drv_num, race_data in date_cursa.items():
            q   = date_quali.get(drv_num, {})
            spr = date_sprint.get(drv_num, {})

            rows.append({
                "An": an, "Round": round_num,
                "CircuitName": circuit_name,
                "RaceName": event_name, "RaceDate": event_date,
                "IsSprintWeekend": 1 if is_sprint else 0,
                "DriverNumber": drv_num,
                "Abbreviation": race_data["Abbreviation"],
                "TeamName": race_data["TeamName"],
                "IsTrainSet": 1 if (an < 2025 or round_num <= SPLIT_2025_ROUND) else 0,

                # Target
                "FinalPosition": race_data["FinalPosition"],
                "Points": race_data["Points"],
                "DNF": race_data["DNF"],
                "Status": race_data["Status"],

                # Calificari
                "GridPosition": race_data["GridPosition"],
                "Q1_sec": q.get("Q1_sec", np.nan),
                "Q2_sec": q.get("Q2_sec", np.nan),
                "Q3_sec": q.get("Q3_sec", np.nan),
                "BestQ_sec": q.get("BestQ_sec", np.nan),
                "GapToPole_sec": q.get("GapToPole_sec", np.nan),
                "GapToPole_pct": q.get("GapToPole_pct", np.nan),
                "Sector1Q_sec": q.get("Sector1_sec", np.nan),
                "Sector2Q_sec": q.get("Sector2_sec", np.nan),
                "Sector3Q_sec": q.get("Sector3_sec", np.nan),

                # Sprint
                "SprintPosition": spr.get("FinalPosition", np.nan),
                "SprintPoints": spr.get("Points", 0.0),
                "SprintDNF": spr.get("DNF", 0),

                # Cursa — pace
                "MedianLapTime_sec": race_data["MedianLapTime_sec"],
                "FastestLap_sec": race_data["FastestLap_sec"],
                "LapConsistency_std": race_data["LapConsistency_std"],

                # Pit stop-uri
                "NumPitStops": race_data["NumPitStops"],
                "AvgPitTime_sec": race_data["AvgPitTime_sec"],
                "MinPitTime_sec": race_data["MinPitTime_sec"],
                "StartCompound": race_data["StartCompound"],
                "NumSafetyCarLaps": race_data["NumSafetyCarLaps"],

                # Meteo
                "AirTemp": race_data["AirTemp"],
                "TrackTemp": race_data["TrackTemp"],
                "Rainfall": race_data["Rainfall"],
                "Humidity": race_data["Humidity"],
                "WindSpeed": race_data["WindSpeed"],
            })

        if not TQDM_OK:
            n = len([x for x in date_cursa if
                     date_cursa[x]["Abbreviation"] in PILOTI_2025])
            print(f"      ✅ {n} piloti 2025 extraciti")


# ─────────────────────────────────────────────────────────────
# FEATURE ENGINEERING
# ─────────────────────────────────────────────────────────────

def adauga_features(df):
    print("\n  Calculeaza features derivate...")
    df = df.sort_values(["An", "Round", "FinalPosition"]).reset_index(drop=True)

    # 1. Statistici cumulative sezon (shift)
    print("    [1/9] Statistici sezon...")
    for col in ["SeasonPoints_before", "SeasonPodiums_before", "SeasonWins_before",
                "SeasonDNF_before", "SeasonRaces_before"]:
        df[col] = 0.0

    for (an, drv), group in df.groupby(["An", "Abbreviation"]):
        idxs = group.index.tolist()
        pts, pods, wins, dnfs, races = 0.0, 0, 0, 0, 0
        for idx in idxs:
            df.at[idx, "SeasonPoints_before"]  = pts
            df.at[idx, "SeasonPodiums_before"] = pods
            df.at[idx, "SeasonWins_before"]    = wins
            df.at[idx, "SeasonDNF_before"]     = dnfs
            df.at[idx, "SeasonRaces_before"]   = races
            pts  += float(df.at[idx, "Points"])
            pods += 1 if df.at[idx, "FinalPosition"] <= 3 else 0
            wins += 1 if df.at[idx, "FinalPosition"] == 1 else 0
            dnfs += int(df.at[idx, "DNF"])
            races += 1

    df["SeasonDNF_rate"] = (
        df["SeasonDNF_before"] / df["SeasonRaces_before"].replace(0, 1)
    ).round(4)

    # 2. Rolling avg pozitie (N=5, N=10)
    print("    [2/9] Rolling avg pozitie...")
    df["RollingAvgPos_5"]  = np.nan
    df["RollingAvgPos_10"] = np.nan
    for drv, group in df.groupby("Abbreviation"):
        idxs = group.sort_values(["An", "Round"]).index.tolist()
        hist = []
        for idx in idxs:
            if hist:
                df.at[idx, "RollingAvgPos_5"]  = round(np.mean(hist[-5:]),  3)
                df.at[idx, "RollingAvgPos_10"] = round(np.mean(hist[-10:]), 3)
            hist.append(df.at[idx, "FinalPosition"])

    # 3. Pozitie campionat WDC/WCC (shift)
    print("    [3/9] Pozitie campionat WDC/WCC...")
    df["WDC_Standing_before"] = np.nan
    df["WCC_Standing_before"] = np.nan
    for an, season_df in df.groupby("An"):
        for round_num in sorted(season_df["Round"].unique()):
            past = season_df[season_df["Round"] < round_num]
            cur_idx = df[(df["An"] == an) & (df["Round"] == round_num)].index
            if past.empty:
                df.loc[cur_idx, "WDC_Standing_before"] = 0.0
                df.loc[cur_idx, "WCC_Standing_before"] = 0.0
                continue
            wdc = (past.groupby("Abbreviation")["Points"].sum()
                   .sort_values(ascending=False).reset_index())
            wdc["Rank"] = range(1, len(wdc)+1)
            wcc = (past.groupby("TeamName")["Points"].sum()
                   .sort_values(ascending=False).reset_index())
            wcc["Rank"] = range(1, len(wcc)+1)
            for idx in cur_idx:
                abbr = df.at[idx, "Abbreviation"]
                team = df.at[idx, "TeamName"]
                row_wdc = wdc[wdc["Abbreviation"] == abbr]
                row_wcc = wcc[wcc["TeamName"] == team]
                df.at[idx, "WDC_Standing_before"] = (
                    float(row_wdc["Rank"].iloc[0]) if not row_wdc.empty else np.nan)
                df.at[idx, "WCC_Standing_before"] = (
                    float(row_wcc["Rank"].iloc[0]) if not row_wcc.empty else np.nan)

    # 4. Statistici echipa (shift)
    print("    [4/9] Statistici echipa...")
    df["TeamSeasonPoints_before"]  = 0.0
    df["TeamSeasonPodiums_before"] = 0
    for (an, team), group in df.groupby(["An", "TeamName"]):
        idxs = group.index.tolist()
        pts, pods = 0.0, 0
        for idx in idxs:
            df.at[idx, "TeamSeasonPoints_before"]  = pts
            df.at[idx, "TeamSeasonPodiums_before"] = pods
            pts  += float(df.at[idx, "Points"])
            pods += 1 if df.at[idx, "FinalPosition"] <= 3 else 0

    # 5. Gap calificari vs coechipier
    print("    [5/9] Gap calificari vs coechipier...")
    df["QualiGapToTeammate_sec"] = np.nan
    for (an, rnd, team), group in df.groupby(["An", "Round", "TeamName"]):
        times = group["BestQ_sec"].dropna()
        if times.empty or len(group) < 2:
            continue
        best = times.min()
        for idx in group.index:
            t = df.at[idx, "BestQ_sec"]
            if not np.isnan(t):
                df.at[idx, "QualiGapToTeammate_sec"] = round(t - best, 4)

    # 6. Gap calificari vs pole
    print("    [6/9] Gap calificari vs pole...")
    df["QualiGapToFastest_sec"] = np.nan
    df["BestQ_normalized"]      = np.nan
    for (an, rnd), group in df.groupby(["An", "Round"]):
        times = group["BestQ_sec"].dropna()
        if times.empty:
            continue
        fastest = times.min()
        for idx in group.index:
            t = df.at[idx, "BestQ_sec"]
            if not np.isnan(t):
                df.at[idx, "QualiGapToFastest_sec"] = round(t - fastest, 4)
                if fastest > 0:
                    df.at[idx, "BestQ_normalized"] = round(t / fastest, 6)

    # 7. Ritm cursa relativ
    print("    [7/9] Ritm cursa relativ...")
    df["MedianLapGapToFastest_sec"] = np.nan
    df["MedianLap_normalized"]      = np.nan
    for (an, rnd), group in df.groupby(["An", "Round"]):
        medians = group["MedianLapTime_sec"].dropna()
        if medians.empty:
            continue
        fastest = medians.min()
        for idx in group.index:
            m = df.at[idx, "MedianLapTime_sec"]
            if not np.isnan(m):
                df.at[idx, "MedianLapGapToFastest_sec"] = round(m - fastest, 4)
                if fastest > 0:
                    df.at[idx, "MedianLap_normalized"] = round(m / fastest, 6)

    # 8. Pozitii castigate + istoric circuit
    df["PositionsGained"] = df["GridPosition"] - df["FinalPosition"]

    print("    [8/9] Istoric pilot pe circuit...")
    df["DriverCircuitRaces_before"]   = 0
    df["DriverCircuitAvgPos_before"]  = np.nan
    df["DriverCircuitBestPos_before"] = np.nan
    for (drv, circuit), group in df.groupby(["Abbreviation", "CircuitName"]):
        idxs = group.sort_values("An").index.tolist()
        hist = []
        for idx in idxs:
            df.at[idx, "DriverCircuitRaces_before"] = len(hist)
            if hist:
                df.at[idx, "DriverCircuitAvgPos_before"]  = round(np.mean(hist), 3)
                df.at[idx, "DriverCircuitBestPos_before"] = min(hist)
            hist.append(df.at[idx, "FinalPosition"])

    # 9. Cariera totala
    print("    [9/9] Statistici cariera...")
    df["CareerRaces_before"]   = 0
    df["CareerWins_before"]    = 0
    df["CareerPodiums_before"] = 0
    for drv, group in df.groupby("Abbreviation"):
        idxs = group.sort_values(["An", "Round"]).index.tolist()
        races, wins, pods = 0, 0, 0
        for idx in idxs:
            df.at[idx, "CareerRaces_before"]   = races
            df.at[idx, "CareerWins_before"]    = wins
            df.at[idx, "CareerPodiums_before"] = pods
            races += 1
            wins  += 1 if df.at[idx, "FinalPosition"] == 1 else 0
            pods  += 1 if df.at[idx, "FinalPosition"] <= 3 else 0

    print("    ✅ Features derivate calculate.")
    return df


# ─────────────────────────────────────────────────────────────
# EXPORT EXCEL
# ─────────────────────────────────────────────────────────────

COLOANE_FINALE = [
    "An", "Round", "CircuitName", "RaceName", "RaceDate",
    "IsSprintWeekend", "DriverNumber", "Abbreviation", "TeamName", "IsTrainSet",
    # Target
    "FinalPosition", "Points", "DNF", "Status",
    # Calificari
    "GridPosition", "Q1_sec", "Q2_sec", "Q3_sec",
    "BestQ_sec", "BestQ_normalized",
    "GapToPole_sec", "GapToPole_pct",
    "QualiGapToTeammate_sec", "QualiGapToFastest_sec",
    "Sector1Q_sec", "Sector2Q_sec", "Sector3Q_sec",
    # Sprint
    "SprintPosition", "SprintPoints", "SprintDNF",
    # Pace
    "MedianLapTime_sec", "FastestLap_sec", "LapConsistency_std",
    "MedianLapGapToFastest_sec", "MedianLap_normalized",
    # Pit stop-uri
    "NumPitStops", "AvgPitTime_sec", "MinPitTime_sec",
    "StartCompound", "NumSafetyCarLaps",
    # Pozitii
    "PositionsGained",
    # Meteo
    "AirTemp", "TrackTemp", "Rainfall", "Humidity", "WindSpeed",
    # Sezon (shift)
    "SeasonPoints_before", "SeasonPodiums_before", "SeasonWins_before",
    "SeasonDNF_before", "SeasonRaces_before", "SeasonDNF_rate",
    # Campionat (shift)
    "WDC_Standing_before", "WCC_Standing_before",
    # Form recent
    "RollingAvgPos_5", "RollingAvgPos_10",
    # Echipa (shift)
    "TeamSeasonPoints_before", "TeamSeasonPodiums_before",
    # Circuit history
    "DriverCircuitRaces_before", "DriverCircuitAvgPos_before",
    "DriverCircuitBestPos_before",
    # Cariera
    "CareerRaces_before", "CareerWins_before", "CareerPodiums_before",
]


def salveaza_excel(df, path):
    print(f"\n  Salveaza {path}...")
    cols = [c for c in COLOANE_FINALE if c in df.columns]
    df_out   = df[cols].copy()
    df_train = df_out[df_out["IsTrainSet"] == 1]
    df_test  = df_out[df_out["IsTrainSet"] == 0]

    stats = pd.DataFrame({
        "Metric": ["Total randuri", "Train", "Test", "Sezoane",
                   "Circuite", "Piloti", "DNF %", "Ploaie %", "Generat la"],
        "Valoare": [
            len(df_out), len(df_train), len(df_test),
            f"{df_out['An'].min()}-{df_out['An'].max()}",
            df_out["CircuitName"].nunique(),
            df_out["Abbreviation"].nunique(),
            f"{df_out['DNF'].mean()*100:.1f}%",
            f"{df_out['Rainfall'].mean()*100:.1f}%",
            datetime.now().strftime("%Y-%m-%d %H:%M"),
        ]
    })

    with pd.ExcelWriter(path, engine="openpyxl") as writer:
        df_out.to_excel(writer,   sheet_name="Date_Complete", index=False)
        df_train.to_excel(writer, sheet_name="Train_Set",     index=False)
        df_test.to_excel(writer,  sheet_name="Test_Set",      index=False)
        stats.to_excel(writer,    sheet_name="Statistici",    index=False)

    mb = os.path.getsize(path) / (1024*1024)
    print(f"\n  ✅ Salvat: {path} ({mb:.1f} MB)")
    print(f"     Total: {len(df_out)} | Train: {len(df_train)} | Test: {len(df_test)}")
    print(f"     Circuite: {df_out['CircuitName'].nunique()} | "
          f"Piloti: {df_out['Abbreviation'].nunique()}")


# ─────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("=" * 60)
    print("  EXTRACTOR F1 — Grila 2025")
    print(f"  FastF1 {fastf1.__version__}")
    print("=" * 60)
    print(f"\n  Cache:    {os.path.abspath(CACHE_DIR)}")
    print(f"  Output:   {OUTPUT_EXCEL}")
    print(f"  Sezoane:  {SEZOANE}")
    print(f"  Piloti:   {len(PILOTI_2025)} (grila 2025)")
    print(f"  Circuite: {len(CIRCUITE_2025)} (calendar 2025)")
    print(f"  Retry:    {MAX_RETRIES}x cu {RETRY_DELAY_SEC//60} minute pauza")
    print(f"\n  La rate limit scriptul asteapta {RETRY_DELAY_SEC//60} min automat.")
    print(f"  Recomandat: porneste seara, lasa peste noapte.\n")

    rows = []
    start = time.time()

    for an in SEZOANE:
        extrage_sezon(an, rows)

    if not rows:
        print("\n❌ Nu s-au extras date. Verifica conexiunea si cache-ul.")
        exit(1)

    print(f"\n{'='*60}")
    print(f"  Randuri brute extrase: {len(rows)}")
    print("  Calculeaza features derivate...")
    print(f"{'='*60}")

    df = pd.DataFrame(rows)
    df = adauga_features(df)
    salveaza_excel(df, OUTPUT_EXCEL)

    elapsed = (time.time() - start) / 60
    print(f"\n{'='*60}")
    print(f"  ✅ GATA in {elapsed:.0f} minute!")
    print(f"  Fisier: {os.path.abspath(OUTPUT_EXCEL)}")
    print(f"  Urmatorul pas: F1_Model_Training.ipynb cu F1_2025Grid.xlsx")
    print(f"{'='*60}")
