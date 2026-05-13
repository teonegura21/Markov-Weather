# PLAN — Motor de Predicție Meteorologică Probabilistică

> Acest document descrie arhitectura completă a motorului de predicție meteo, de la
> vectorul de caracteristici 25-dimensional până la Monte Carlo cu Hidden Markov Model.
> **Status: Planificare. Nimic din acest document nu este încă implementat.**

---

## Cuprins

1. [Starea Actuală vs. Starea Țintă](#1-starea-actuală-vs-starea-țintă)
2. [Vectorul Meteo 25D](#2-vectorul-meteo-25d)
3. [Detectoare de Fenomene (Recipe Detectors)](#3-detectoare-de-fenomene-recipe-detectors)
4. [Clustering în Regimuri Meteo (k-means)](#4-clustering-în-regimuri-meteo-k-means)
5. [Markov de Ordin 2 cu Zerouri Structurale](#5-markov-de-ordin-2-cu-zerouri-structurale)
6. [Hidden Markov Model (3 Straturi)](#6-hidden-markov-model-3-straturi)
7. [Simulare Monte Carlo](#7-simulare-monte-carlo)
8. [Blendare cu Priorul Sezonier](#8-blendare-cu-priorul-sezonier)
9. [Extragerea Probabilităților din Ensemble](#9-extragerea-probabilităților-din-ensemble)
10. [Schema Bazei de Date — Tabele Noi](#10-schema-bazei-de-date--tabele-noi)
11. [Proceduri Stocate Noi](#11-proceduri-stocate-noi)
12. [Plan de Implementare (Faze)](#12-plan-de-implementare-faze)
13. [Verificare Corectitudine](#13-verificare-corectitudine)

---

## 1. Starea Actuală vs. Starea Țintă

### Ce există ACUM (implementat)

| Componentă | Status |
|---|---|
| Tabele: countries, cities, forecasts, users, votes, comments, forecast_log | ✅ Implementat |
| Coloane: data_source, fetched_at | ✅ Implementat |
| API Open-Meteo (istoric + forecast) | ✅ Implementat |
| Import date în PostgreSQL din API | ✅ Implementat |
| Auto-refresh la startup (verificare prospețime) | ✅ Implementat |
| Predicție DB: `sp_predict_week` (medie aceeași zi din ani trecuți + fallback) | ✅ Implementat |
| Predicție API: Open-Meteo direct | ✅ Implementat |
| UI JavaFX: 7 taburi funcționale | ✅ Implementat |

### Ce trebuie CONSTRUIT (acest plan)

| Componentă | Prioritate | Complexitate |
|---|---|---|
| Vector meteo 25D + calcul derivativ temporal | 🔴 Critical | ★★☆ |
| Detectoare fuzzy de fenomene (fog, storm, heatwave, etc.) | 🔴 Critical | ★★☆ |
| Clustering k-means în 40D → regimuri meteo | 🔴 Critical | ★★★ |
| Matrice Markov ordin 2, condiționată sezonier, cu zerouri structurale | 🔴 Critical | ★★★ |
| Hidden Markov Model (Baum-Welch, H=8 stări ascunse) | 🟡 High | ★★★★★ |
| Motor Monte Carlo (N=5000 traiectorii × 10 zile) | 🔴 Critical | ★★★ |
| Blendare exponențială cu priorul sezonier | 🟡 High | ★☆☆ |
| Extragere P10/P50/P90 + probabilități evenimente | 🔴 Critical | ★☆☆ |
| Proceduri stocate: k-means, Markov, HMM, Monte Carlo | 🔴 Critical | ★★★★ |
| UI: grafice cu intervale de încredere, probabilități | 🟢 Medium | ★★☆ |

---

## 2. Vectorul Meteo 25D

Fiecare zi pentru fiecare oraș este reprezentată de un vector de 25 de caracteristici
numerice. La acestea se adaugă derivate temporale (Δ pe 1, 2, 3 zile) și scorurile
detectoarelor de fenomene, rezultând un vector final de **~40 dimensiuni**.

### LAYER A: TERMIC (5 dimensiuni)

| # | Nume | Formulă | Unitate | Explicație |
|---|---|---|---|---|
| A1 | `temp_min` | min(temperature) | °C | Minimul zilnic (de obicei în zori) |
| A2 | `temp_max` | max(temperature) | °C | Maximul zilnic (de obicei după-amiaza) |
| A3 | `temp_avg` | (min+max)/2 | °C | Media zilnică |
| A4 | `temp_amplitude` | max − min | °C | Amplitudinea diurnă — mică în ceață (2-3°C), mare în deșert (15-20°C) |
| A5 | `temp_trend` | avg(t) − avg(t−1) | °C | Semnal de încălzire/răcire |

### LAYER B: UMIDITATE (5 dimensiuni)

| # | Nume | Formulă | Unitate | Explicație |
|---|---|---|---|---|
| B1 | `humidity_min` | min(relative_humidity) | % | Cel mai uscat moment al zilei (după-amiaza) |
| B2 | `humidity_max` | max(relative_humidity) | % | Cel mai umed moment (zori) |
| B3 | `humidity_avg` | mean(relative_humidity) | % | Umiditatea relativă medie |
| B4 | `dew_point_min` | calculat din temp_min și humidity_max | °C | Punctul de rouă minim — umiditatea absolută |
| B5 | `dew_point_spread` | temp_min − dew_point_min | °C | Când < 2°C: ceața este iminentă |

Formula punct de rouă (aproximare Magnus):
```
γ = ln(humidity/100) + (17.27 × temp) / (237.3 + temp)
dew_point = (237.3 × γ) / (17.27 − γ)
```

### LAYER C: VÂNT (4 dimensiuni)

| # | Nume | Formulă | Unitate | Explicație |
|---|---|---|---|---|
| C1 | `wind_speed_avg` | mean(wind_speed) | km/h | Viteza medie a vântului |
| C2 | `wind_speed_max` | max(wind_speed) | km/h | Rafala maximă |
| C3 | `gust_factor` | max/avg | adimensional | >3 = rafale convective, <1.5 = vânt constant |
| C4 | `wind_persistence` | ore cu vânt > 20 km/h | ore (0-24) | Cât de persistent este vântul |

### LAYER D: RADIAȚIE ȘI NORI (4 dimensiuni)

| # | Nume | Formulă | Unitate | Explicație |
|---|---|---|---|---|
| D1 | `sunshine_hours` | ore efective de soare | ore (0-16) | Depinde de sezon |
| D2 | `sunshine_fraction` | ore_efective / ore_maxime_posibile | 0-1 | 0.0 = complet acoperit, 1.0 = senin toată ziua |
| D3 | `uv_index_max` | max(uv_index) | 0-11+ | Vârful UV al zilei |
| D4 | `cloud_cover_proxy` | 1.0 − sunshine_fraction | 0-1 | Acoperire nori estimată |

### LAYER E: PRECIPITAȚII (4 dimensiuni)

| # | Nume | Formulă | Unitate | Explicație |
|---|---|---|---|---|
| E1 | `precipitation_sum` | total precipitații | mm | Cantitatea totală în 24h |
| E2 | `precip_intensity` | precip_sum / ore_cu_precipitații | mm/h | <1 = burniță, 1-5 = moderat, >5 = torențial |
| E3 | `precipitation_hours` | ore cu precipitații | ore (0-24) | Durata evenimentului |
| E4 | `snow_depth` | strat de zăpadă | cm | 0 în sezonul cald |

### LAYER F: PRESIUNE (3 dimensiuni)

| # | Nume | Formulă | Unitate | Explicație |
|---|---|---|---|---|
| F1 | `pressure_mean` | mean(sea_level_pressure) | hPa | Presiunea medie la nivelul mării |
| F2 | `pressure_trend` | mean(t) − mean(t−1) | hPa | Negativ = vremea se deteriorează, pozitiv = se ameliorează |
| F3 | `pressure_range` | max(pressure) − min(pressure) | hPa | Oscilația diurnă: 2-3 hPa normal, >5 hPa = sistem puternic |

### DERIVATE TEMPORALE (8 dimensiuni adiționale)

Pentru fiecare dintre variabilele cheie (temp_avg, humidity_avg, pressure_mean,
wind_speed_avg), se calculează:

```
Δ₁ = v(t) − v(t−1)     (diferența față de ieri)
Δ₂ = v(t) − v(t−2)     (diferența față de alaltăieri — accelerare)
```

Acestea captează **viteza de schimbare** a sistemului, nu doar starea curentă.

---

## 3. Detectoare de Fenomene (Recipe Detectors)

Fiecare detector este o funcție fuzzy care returnează un scor **între 0 și 1**,
reprezentând cât de puternic se manifestă fenomenul într-o zi dată. Aceste scoruri
devin **dimensiuni suplimentare** în vectorul de clustering.

### 3.1 Detector de Ceață (Fog)

```
fog_score = min(
    μ_sigmoid(humidity_avg,    center=88%,  width=5%,   direction=up),    // umiditate mare
    μ_rev_sigmoid(wind_speed_avg, center=8,  width=3,      direction=down),  // vânt slab
    μ_rev_sigmoid(dew_point_spread, center=2.5, width=0.8, direction=down),  // aproape de saturație
    μ_rev_sigmoid(sunshine_fraction, center=0.3, width=0.15, direction=down), // acoperit
    seasonal_weight(winter=1.0, autumn=0.9, spring=0.5, summer=0.2)
)
```

**Fizica**: Ceața de radiație apare în nopți senine cu vânt 0-8 km/h, când solul se
răcește sub punctul de rouă. Ceața de advecție apare când aer cald și umed trece
peste o suprafață rece. Ambele necesită vânt slab și umiditate aproape de saturație.

**Persistența contează**: O zi cu ceață în a 4-a zi consecutivă are comportament
diferit față de prima zi. Ceața persistentă (>3 zile) formează un strat de inversiune
care se auto-întreține. Acest lucru este capturat de Markov-ul de ordin 2.

### 3.2 Detector de Furtună Convectivă (Afternoon Thunderstorm)

```
thunderstorm_score = min(
    μ_sigmoid(temp_max,       center=28°C, width=3°C,   direction=up),    // zi caniculară
    μ_sigmoid(temp_amplitude, center=13°C, width=3°C,   direction=up),    // amplitudine mare
    μ_sigmoid(humidity_min,   center=45%,  width=8%,    direction=up),    // suficientă umezeală
    μ_sigmoid(gust_factor,    center=2.5,  width=0.5,   direction=up),    // rafale
    μ_sigmoid(precip_intensity, center=3,  width=1.5,   direction=up),    // ploaie torențială
    seasonal_weight(summer=1.0, spring=0.5, autumn=0.4, winter=0.05)
)
```

**Fizica**: Încălzirea solară a solului → termice → cumulus congestus → furtună.
Necesită: (1) diferență mare de temperatură sol-aer, (2) umiditate în straturile
joase și medii, (3) trigger (front rece, convergență de vânt, orografie).

### 3.3 Detector de Ciclon Extratropical (Mid-Latitude Cyclone)

```
cyclone_score = min(
    μ_rev_sigmoid(pressure_mean, center=1005, width=8, direction=down),    // presiune joasă
    μ_rev_sigmoid(pressure_trend, center=-2, width=1.5, direction=down),   // presiune în scădere
    μ_sigmoid(wind_speed_avg, center=25, width=8, direction=up),           // vânt
    μ_sigmoid(humidity_avg, center=75%, width=10%, direction=up),          // umiditate
    // Dacă precipitațiile sunt constante (nu torențiale) → ciclon, nu convectiv
    μ_sigmoid(precipitation_hours, center=6, width=3, direction=up),
    μ_rev_sigmoid(precip_intensity, center=5, width=2, direction=down)
)
```

### 3.4 Detector de Anticiclon Persistent (Clear/Dry Regime)

```
anticyclone_score = min(
    μ_sigmoid(pressure_mean, center=1025, width=5, direction=up),
    μ_sigmoid(pressure_trend, center=0, width=1.5, direction=around_zero), // presiune stabilă
    μ_rev_sigmoid(wind_speed_avg, center=10, width=5, direction=down),
    μ_rev_sigmoid(humidity_avg, center=60%, width=10%, direction=down),
    μ_sigmoid(sunshine_fraction, center=0.7, width=0.2, direction=up)
)
```

### 3.5 Detector de Val de Căldură (Heatwave)

```
heatwave_score = min(
    μ_sigmoid(temp_max, center=35°C, width=2°C, direction=up),
    μ_sigmoid(temp_trend, center=0, width=1, direction=around_zero),       // persistă, nu scade
    μ_sigmoid(sunshine_fraction, center=0.8, width=0.1, direction=up),
    μ_sigmoid(temp_amplitude, center=14°C, width=2°C, direction=up),
    seasonal_weight(summer=1.0, spring=0.2, autumn=0.3, winter=0.0)
)
```

### 3.6 Detector de Inversiune Termică (Winter Stratus)

```
inversion_score = min(
    μ_rev_sigmoid(temp_amplitude, center=4°C, width=1.5°C, direction=down), // amplitudine foarte mică
    μ_rev_sigmoid(wind_speed_avg, center=5, width=3, direction=down),
    μ_rev_sigmoid(sunshine_fraction, center=0.15, width=0.1, direction=down), // fără soare
    μ_sigmoid(humidity_avg, center=82%, width=8%, direction=up),
    seasonal_weight(winter=1.0, autumn=0.8, spring=0.3, summer=0.05)
)
```

### Funcțiile de apartenență fuzzy

```
μ_sigmoid(x, center, width, direction=up):
    if direction == up:   return 1 / (1 + exp(-(x - center) / width))
    if direction == down: return 1 / (1 + exp(+(x - center) / width))
    if direction == around_zero: return exp(-x² / (2 × width²))

μ_rev_sigmoid = complementul: 1 − μ_sigmoid
```

---

## 4. Clustering în Regimuri Meteo (k-means)

### Vectorul de intrare: ~40D

```
v_raw = [A1..A5, B1..B5, C1..C4, D1..D4, E1..E4, F1..F3]   // 25D vectorul de bază
v_temp = [Δ₁ temp_avg, Δ₂ temp_avg, Δ₁ humidity, Δ₂ humidity,
          Δ₁ pressure, Δ₂ pressure, Δ₁ wind, Δ₂ wind]         // 8D derivate temporale
v_recipes = [fog_score, thunderstorm_score, cyclone_score,
             anticyclone_score, heatwave_score, inversion_score]  // 6D detectoare

v_final = [v_raw | v_temp | v_recipes]                        // 39D
```

### Algoritmul

```
1. Se colectează toate zilele din toți anii (minim 3 ani) pentru o ZONĂ CLIMATICĂ
   (nu global — regimurile diferă între București și Oslo).

2. Se normalizează fiecare dimensiune: z = (x − μ) / σ
   (standardizare pentru ca distanța euclidiană să nu fie dominată de scale diferite)

3. Se rulează k-means cu K=16 (număr ales empiric; se validează cu metoda elbow
   și silhouette score).

4. Fiecare zi primește o etichetă de regim: r ∈ {1, 2, ..., 16}

5. Pentru fiecare regim r se calculează:
   - Centroidul μᵣ (vector 40D)
   - Matricea de covarianță Σᵣ (40×40)
   - Frecvența πᵣ (proporția de zile din istoric)
   - Distribuția sezonieră P(season | r) — cât de des apare regimul în fiecare sezon

6. Regimurile sunt ETICHETATE MANUAL după inspecția centroidului:
   Exemplu:
     Regim 3: μ = [−2, 0, ..., 0.88, 0.05, 0.02, 0.90, 0.03, 0.01]
              → "Anticiclon de iarnă cu ceață și inversiune"
     Regim 8: μ = [15, 33, ..., 0.05, 0.82, 0.10, 0.02, 0.75, 0.01]
              → "Caniculă de vară cu furtuni convective"
```

### Regimuri așteptate (ipotetic, pe baza climatologiei Europei Centrale)

| ID | Regim | Sezon dominant | Caracteristici |
|---|---|---|---|
| 1 | Anticiclon uscat de vară | Vară | Cald, uscat, senin, vânt slab |
| 2 | Caniculă convectivă | Vară | Foarte cald, furtuni după-amiaza |
| 3 | Front rece de vară | Vară | Răcorire bruscă, ploaie, apoi senin |
| 4 | Anticiclon de toamnă | Toamnă | Răcoros dimineața, blând ziua, ceață |
| 5 | Depresiune atlantică | Toamnă/Iarnă | Ploaie continuă, vânt, presiune joasă |
| 6 | Anticiclon de iarnă cu ceață | Iarnă | Foarte rece, ceață persistentă, fără soare |
| 7 | Front cald de iarnă | Iarnă | Încălzire, ninsoare sau lapoviță |
| 8 | Viscol / Ciclon de iarnă | Iarnă | Zăpadă, vânt puternic, viscol |
| 9 | Primăvară instabilă | Primăvară | Alternanță soare-ploaie, amplitudine mare |
| 10 | Anticiclon blând de primăvară | Primăvară | Cald, uscat, înverzire |
| ... | ... | ... | ... |

---

## 5. Markov de Ordin 2 cu Zerouri Structurale

### 5.1 De ce ordin 2?

Un Markov de ordin 1 spune: P(mâine = ploaie | azi = înnorat).

Dar secvența contează:
- P(ploaie | ieri=senin, azi=înnorat) = 0.40  (se formează sistemul)
- P(ploaie | ieri=ploaie, azi=înnorat) = 0.15  (sistemul se retrage)

Acestea sunt două probabilități fundamental diferite, pe care ordinul 1
le confundă (ambele ar fi "azi=înnorat → mâine=ploaie").

### 5.2 Structura tensorului de tranziție

```
T[season][rₜ₋₁][rₜ][rₜ₊₁] = P(rₜ₊₁ | rₜ₋₁, rₜ, season)

Dimensiuni: 4 sezoane × 16 regimuri × 16 regimuri × 16 regimuri
           = 4 × 4096 = 16384 probabilități

Sparsitate efectivă: ~60-70% din tranziții sunt structural zero sau near-zero
                     → ~5000-6000 probabilități nenule de estimat
```

### 5.3 Construcția tensorului

```sql
-- Pentru fiecare sezon, numărăm tripleții de regimuri consecutive
INSERT INTO markov_counts (season, r_prev, r_curr, r_next, count)
SELECT
    fn_get_season(d.date) AS season,
    r_prev.regime_id,
    r_curr.regime_id,
    r_next.regime_id,
    COUNT(*)
FROM daily_regimes r_prev
JOIN daily_regimes r_curr ON r_curr.city_id = r_prev.city_id
    AND r_curr.date = r_prev.date + INTERVAL '1 day'
JOIN daily_regimes r_next ON r_next.city_id = r_curr.city_id
    AND r_next.date = r_curr.date + INTERVAL '1 day'
GROUP BY season, r_prev.regime_id, r_curr.regime_id, r_next.regime_id;

-- Normalizare în probabilități
SELECT
    season, r_prev, r_curr, r_next,
    count::DOUBLE PRECISION / SUM(count) OVER (
        PARTITION BY season, r_prev, r_curr
    ) AS probability
FROM markov_counts;
```

### 5.4 Zerouri Structurale (Constrângeri Fizice)

Anumite tranziții sunt **imposibile fizic**, nu doar improbabile. Acestea primesc
probabilitate **exact 0** și sunt **resample-ate** în Monte Carlo:

| Regim Sursă | Regim Destinație | Motiv Fizic |
|---|---|---|
| Ciclon (precip > 10mm, hum > 85%) | Anticiclon uscat (hum < 40%) | Solul saturat → evaporare → umiditate minimă 60-70% |
| Caniculă (temp > 35°C) | Viscol (zăpadă) | Imposibil termodinamic în 24h |
| Ceață persistentă (vânt < 5 km/h) | Furtună (vânt > 50 km/h) | Tranziția necesită trecerea unui front (12-24h) |
| Inversiune termică | Convecție | Inversiunea SUPRIMĂ convecția prin definiție |

Regulă generală: dacă două regimuri au centroid-euclidian > 3σ distanță
în dimensiunile de umiditate și vânt, tranziția directă este structural zero
(multiplul de 3σ este calibrat pe date).

### 5.5 Condiționare Sezonieră

Tensorul este separat pentru fiecare sezon:

```
T_winter[r₋₁][r₀][r₁] — tranziții caracteristice iernii
T_spring[r₋₁][r₀][r₁] — tranziții de primăvară
T_summer[r₋₁][r₀][r₁] — tranziții de vară
T_autumn[r₋₁][r₀][r₁] — tranziții de toamnă
```

Aceasta rezolvă problema fundamentală: "ploaie → senin" are probabilitate 0.30
în vară (după o furtună convectivă trece repede) dar doar 0.08 în iarnă
(ploaia de iarnă provine din sisteme frontale largi care persistă).

---

## 6. Hidden Markov Model (3 Straturi)

### 6.1 Arhitectura pe 3 Niveluri

```
NIVEL 3 — Sinoptic / Planetar (scara 1000-3000km, persistență 5-21 zile)
├── Ω₁: Dorsala Azorelor peste Europa Centrală
├── Ω₂: Blocaj Scandinav (high-pressure blocking)
├── Ω₃: Flux zonal atlantic (tren de depresiuni)
├── Ω₄: Cut-off low mediteranean
└── Ω₅: Blocaj Omega (high-low-high)

NIVEL 2 — Regional / Mezoscale (100-500km, 1-5 zile) ← HIDDEN STATES
├── h₁: Dom anticiclonic (subsidență, senin, inversiune)
├── h₂: Pre-frontal (sistem în apropiere, presiune în scădere)
├── h₃: Pasaj frontal (schimbare de vânt, bandă de precipitații)
├── h₄: Post-frontal (advecție rece, cumulus, averse)
├── h₅: Construcție convectivă (încălzire solară, instabilitate)
├── h₆: Descărcare convectivă (furtuni, schimbări rapide de presiune)
├── h₇: Strat marin / Ceață de radiație (vânt 0, inversiune)
└── h₈: Regim de ceață persistentă (auto-întreținut)

NIVEL 1 — Observabil (local, per oraș, zilnic)
└── Vectorul 40D + regimul k-means observat r ∈ {1..16}
```

### 6.2 Antrenare Baum-Welch

```
Input: secvența de regimuri pentru un oraș pe 5+ ani
       r₁, r₂, r₃, ..., r_T  (fiecare rₜ ∈ {1..16})

Parametri de învățat:
  A[hᵢ][hⱼ]  — matricea de tranziție a stărilor ascunse (8×8)
  B[hᵢ][r]   — matricea de emisie (8×16): P(observăm regimul r | starea ascunsă hᵢ)
  π[hᵢ]      — distribuția inițială a stărilor ascunse

Algoritm:
1. Inițializare aleatoare a matricelor A, B, π
2. Repetă până la convergență:
   a. Forward:  αₜ(i) = P(r₁..rₜ, hₜ=i | A, B, π)
   b. Backward: βₜ(i) = P(rₜ₊₁..r_T | hₜ=i, A, B)
   c. E-step:   γₜ(i) = P(hₜ=i | r₁..r_T) = αₜ(i)βₜ(i) / Σⱼ αₜ(j)βₜ(j)
                ξₜ(i,j) = P(hₜ=i, hₜ₊₁=j | r₁..r_T)
   d. M-step:   A[i][j] = Σₜ ξₜ(i,j) / Σₜ γₜ(i)
                B[i][r] = Σₜ γₜ(i) × I(rₜ=r) / Σₜ γₜ(i)
                π[i] = γ₁(i)
```

### 6.3 Interpretarea stărilor ascunse după antrenare

După antrenare, inspectăm matricea de emisie B pentru a eticheta stările ascunse:

```
Exemplu:
  h₂ emite: {regim_3:0.45, regim_7:0.30, regim_9:0.15} → "activitate convectivă"
  h₅ emite: {regim_1:0.70, regim_2:0.20}                → "anticiclon stabil"
  h₇ emite: {regim_11:0.50, regim_12:0.25, regim_13:0.15} → "depresiune frontală"
```

### 6.4 Inferență Online (predicție cu HMM)

```
Având: istoricul de regimuri până azi (inclusiv): r₁, r₂, ..., r_today
       și starea ascunsă cea mai probabilă la ziua curentă: ĥ_today = argmax γ_today(i)

Predicție pentru mâine:
1. Starea ascunsă mâine:  P(h_tomorrow = j) = A[ĥ_today][j]
2. Regimul mâine:          P(r_tomorrow = k) = Σⱼ P(h_tomorrow=j) × B[j][k]
3. Vectorul meteo:         E[v_tomorrow] = Σₖ P(r_tomorrow=k) × μₖ
```

### 6.5 Semi-Markov: Durata Contează

Un HMM standard presupune că durata într-o stare ascunsă este geometrică:
P(durata = d) = (1−a) × aᵈ⁻¹. Dar fenomenele meteo au durate caracteristice
diferite:

| Fenomen | Durată tipică | Distribuție |
|---|---|---|
| Ceață de radiație | 0.5-2 zile | Exponențială scurtă |
| Ceață persistentă de iarnă | 3-10+ zile | Log-normală |
| Caniculă | 2-5 zile | Normală îngustă |
| Ciclon extratropical | 3-5 zile | Gamma |
| Anticiclon de vară | 5-14 zile | Normală largă |

**Soluție**: Semi-Markov Model (HSMM) — în loc de A[i][j] constant, folosim:

```
A[i][j][d] = P(hₜ₊₁=j | hₜ=i, durata_în_i = d)
```

Aceasta înseamnă că probabilitatea de a părăsi o stare depinde de cât timp
ai stat deja în ea. Pentru ceață: după 3 zile de ceață consecutivă,
probabilitatea de a persista CREȘTE (se formează inversiunea). Pentru
caniculă: după 4 zile, probabilitatea de schimbare crește (front rece).

În practică, implementăm acest lucru ca un **Markov de ordin variabil**:
după N zile consecutive în același regim, folosim o tranziție diferită.

---

## 7. Simulare Monte Carlo

### 7.1 Parametri

```
N = 5000            numărul de traiectorii simulate
T = 10              zile în viitor (maxim)
τ = 5               constanta de decay pentru blendare (zile)
```

### 7.2 Pseudocod

```
funcție predict(city_id, date_start, date_yesterday):
    r_today     = regimul observat la date_start
    r_yesterday = regimul observat la date_yesterday
    h_today     = argmax γ(date_start)  // din HMM
    season      = get_season(date_start)
    d           = durata_consecutivă în starea h_today
    T_tensor    = T[season]             // tensorul Markov condiționat sezonier

    results = array[T][N]  // stochează vectorii 25D pentru fiecare zi și traiectorie
    regime_trace = array[N][T+1]

    pentru k = 1..N:
        h_prev2, h_prev1 = h_today (sau nil la pasul 1)
        r_prev2 = r_yesterday
        r_prev1 = r_today
        durata_în_stare = d

        pentru t = 1..T:
            // PAS 1: Tranziția stării ascunse (Semi-Markov)
            h_next = sample_hidden_transition(h_prev1, durata_în_stare, A_semi_markov)

            dacă h_next == h_prev1:
                durata_în_stare += 1
            altfel:
                durata_în_stare = 1

            // PAS 2: Emisia regimului observabil
            r_next = sample_regime(h_next, r_prev2, r_prev1, T_tensor)

            // PAS 3: Verificare zerouri structurale
            dacă este_transition_interzisa(r_prev1, r_next):
                r_next = resample_regime(h_next, r_prev2, r_prev1, T_tensor,
                                         exclude_transitii_interzise=true)

            // PAS 4: Emisia vectorului meteo
            v_next = sample_multivariate_normal(μ[r_next], Σ[r_next])

            // PAS 5: Blendare cu priorul sezonier
            α = exp(-t / τ)
            v_seasonal = sample_from_seasonal_distribution(date_start + t, r_next)
            v_blend = α × v_next + (1 − α) × v_seasonal

            // PAS 6: Stocare
            results[t][k] = v_blend
            regime_trace[k][t] = r_next

            // PAS 7: Shift
            r_prev2 = r_prev1
            r_prev1 = r_next
            h_prev2 = h_prev1
            h_prev1 = h_next

    returnează results, regime_trace
```

### 7.3 Funcții Auxiliare

```
sample_hidden_transition(h_current, duration, A_semi):
    // A_semi[h_current][h_next][duration_bucket]
    bucket = discretize_duration(duration)  // [1, 2, 3-5, 6+]
    probs = A_semi[h_current][:][bucket]
    returnează categorical_sample(probs)

sample_regime(h_next, r_prev2, r_prev1, T_tensor):
    probs = T_tensor[r_prev2][r_prev1][:]
    // Amestecă cu emisia HMM: pondere 0.6 Markov, 0.4 HMM
    probs_hmm = B[h_next][:]
    probs_final = 0.6 × probs + 0.4 × probs_hmm
    returnează categorical_sample(probs_final)

este_transition_interzisa(r_from, r_to):
    // Verifică tabela structural_zeros
    returnează EXISTS(SELECT 1 FROM structural_zeros
           WHERE regime_from = r_from AND regime_to = r_to)

sample_multivariate_normal(μ, Σ):
    // Descompunere Cholesky: Σ = L × Lᵀ
    L = cholesky_decomposition(Σ)
    z = vector_gaussian_iid(40D)  // 40 de sample-uri N(0,1) independente
    returnează μ + L × z

sample_from_seasonal_distribution(date, regime):
    // Media și varianța istorică pentru acea zi calendaristică (±3 zile)
    // în același regim
    returnează random_sample(N(μ_seasonal[date][regime], σ²_seasonal[date][regime]))
```

---

## 8. Blendare cu Priorul Sezonier

### 8.1 De ce este necesară

Un lanț Markov pur "uită" starea inițială după ~5-7 pași (converge la distribuția
staționară). Dar noi știm că indiferent de vremea de azi, în 10 zile prognoza
trebuie să semene cu **climatologia acelei perioade calendaristice**.

### 8.2 Formula de blendare

```
v_blend(t) = α(t) × v_transition(t) + (1 − α(t)) × v_seasonal(t)

unde:
  α(t) = exp(−t / τ),  τ = 5 zile
  v_transition(t) = eșantionul din lanțul Markov + HMM
  v_seasonal(t)   = eșantionul din distribuția istorică a aceleiași săptămâni calendaristice
```

### 8.3 Efectul în timp

```
Ziua 1:  α = exp(−1/5) = 0.819  → 82% Markov, 18% sezonier
Ziua 3:  α = exp(−3/5) = 0.549  → 55% Markov, 45% sezonier
Ziua 5:  α = exp(−5/5) = 0.368  → 37% Markov, 63% sezonier
Ziua 7:  α = exp(−7/5) = 0.247  → 25% Markov, 75% sezonier
Ziua 10: α = exp(−10/5) = 0.135 → 13% Markov, 87% sezonier
```

Pe măsură ce ne îndepărtăm de prezent, predicția tinde către **climatologia
perioadei** — ceea ce este corect: nu poți prezice vremea peste 10 zile pe baza
vremii de azi, dar poți spune cum este vremea în mod normal în acea perioadă.

---

## 9. Extragerea Probabilităților din Ensemble

După cele N=5000 de simulări Monte Carlo, avem pentru fiecare zi viitoare `t`
o distribuție de 5000 de vectori meteo. Din aceasta extragem:

### 9.1 Distribuții pentru variabile continue

```
temp_max_samples = sort([results[t][1].temp_max, ..., results[t][5000].temp_max])

P10_temp_max(t) = temp_max_samples[500]    // percentila 10%
P50_temp_max(t) = temp_max_samples[2500]   // mediana (cea mai probabilă)
P90_temp_max(t) = temp_max_samples[4500]   // percentila 90%

// Interval de încredere 80%: [P10, P90]
```

Aceleași percentile pentru: temp_min, wind_speed, humidity, pressure, precip_sum.

### 9.2 Probabilități pentru evenimente discrete

```
P(ploaie_mâine)            = count(v[1].precip_sum > 1mm) / 5000
P(ploaie_torențială_mâine) = count(v[1].precip_intensity > 5) / 5000
P(furtună_în_următoarele_3_zile) = count(orice t∈1..3 are thunderstorm_score > 0.7) / 5000
P(caniculă_săptămâna_asta)       = count(orice t∈1..7 are heatwave_score > 0.5) / 5000
P(ceață_mâine)                   = count(v[1].fog_score > 0.5) / 5000
P(schimbare_regim_în_3_zile)     = count(trajectory[k][3] ≠ trajectory[k][0]) / 5000
```

### 9.3 Statistici de ansamblu

```
// Consensul ensemble-ului
consensus_temp_max(t) = median(results[t][:].temp_max)

// Incertitudinea (spread-ul)
spread_temp_max(t) = P90_temp_max(t) − P10_temp_max(t)
// Spread mare = predicție incertă (vreme instabilă)
// Spread mic   = predicție sigură (regim stabil)

// Probabilitatea de extrem
P(temp_max > 35°C în ziua t) = count(v.temp_max > 35) / 5000
P(vânt > 50 km/h în ziua t)  = count(v.wind_speed > 50) / 5000
```

---

## 10. Schema Bazei de Date — Tabele Noi

### 10.1 `weather_regimes` — Centroizii regimurilor

```sql
CREATE TABLE weather_regimes (
    id              SERIAL PRIMARY KEY,
    climate_zone    VARCHAR(50) NOT NULL,      -- ex: continental_eastern_europe
    regime_id       INT NOT NULL,              -- 1..16 în interiorul zonei
    centroid        DOUBLE PRECISION[] NOT NULL, -- vectorul μ (40D)
    covariance_flat DOUBLE PRECISION[] NOT NULL, -- Σ aplatizată pe rânduri (1600 elemente)
    frequency       DOUBLE PRECISION NOT NULL, -- π (proporția de zile)
    label_ro        VARCHAR(200),              -- etichetă în română
    description_ro  TEXT,
    UNIQUE (climate_zone, regime_id)
);
```

### 10.2 `daily_regimes` — Regimul fiecărei zile

```sql
CREATE TABLE daily_regimes (
    id          BIGSERIAL PRIMARY KEY,
    city_id     INT NOT NULL REFERENCES cities(id),
    date        DATE NOT NULL,
    regime_id   INT NOT NULL,
    climate_zone VARCHAR(50) NOT NULL,
    fog_score   DOUBLE PRECISION,
    thunderstorm_score DOUBLE PRECISION,
    cyclone_score DOUBLE PRECISION,
    anticyclone_score DOUBLE PRECISION,
    heatwave_score DOUBLE PRECISION,
    inversion_score DOUBLE PRECISION,
    computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (city_id, date)
);

CREATE INDEX idx_daily_regimes_city_date ON daily_regimes(city_id, date);
CREATE INDEX idx_daily_regimes_regime ON daily_regimes(regime_id);
```

### 10.3 `markov_transitions` — Tensorul de tranziție

```sql
CREATE TABLE markov_transitions (
    id          BIGSERIAL PRIMARY KEY,
    climate_zone VARCHAR(50) NOT NULL,
    season      VARCHAR(10) NOT NULL,    -- winter, spring, summer, autumn
    r_prev      INT NOT NULL,            -- regimul ieri (t-1)
    r_curr      INT NOT NULL,            -- regimul azi (t)
    r_next      INT NOT NULL,            -- regimul mâine (t+1)
    count       INT NOT NULL,            -- numărul brut de apariții
    probability DOUBLE PRECISION NOT NULL, -- probabilitatea normalizată
    UNIQUE (climate_zone, season, r_prev, r_curr, r_next)
);

CREATE INDEX idx_markov_zone_season ON markov_transitions(climate_zone, season);
CREATE INDEX idx_markov_lookup ON markov_transitions(climate_zone, season, r_prev, r_curr);
```

### 10.4 `structural_zeros` — Tranziții interzise fizic

```sql
CREATE TABLE structural_zeros (
    id          SERIAL PRIMARY KEY,
    regime_from INT NOT NULL,
    regime_to   INT NOT NULL,
    reason      TEXT NOT NULL,            -- explicația fizică
    CONSTRAINT uq_structural_zero UNIQUE (regime_from, regime_to)
);
```

### 10.5 `hidden_states` — Parametrii HMM

```sql
CREATE TABLE hidden_states (
    id              SERIAL PRIMARY KEY,
    city_id         INT NOT NULL REFERENCES cities(id),
    state_id        INT NOT NULL,         -- 1..8
    label_ro        VARCHAR(200),         -- etichetă umană după inspecție
    emission_probs  DOUBLE PRECISION[] NOT NULL, -- B[state_id][:] (16 elemente)
    UNIQUE (city_id, state_id)
);

CREATE TABLE hidden_transitions (
    id              SERIAL PRIMARY KEY,
    city_id         INT NOT NULL REFERENCES cities(id),
    state_from      INT NOT NULL,
    state_to        INT NOT NULL,
    duration_bucket INT NOT NULL,         -- 0=1 zi, 1=2 zile, 2=3-5 zile, 3=6+ zile
    probability     DOUBLE PRECISION NOT NULL,
    UNIQUE (city_id, state_from, state_to, duration_bucket)
);
```

### 10.6 `monte_carlo_predictions` — Cache pentru predicții

```sql
CREATE TABLE monte_carlo_predictions (
    id              BIGSERIAL PRIMARY KEY,
    city_id         INT NOT NULL REFERENCES cities(id),
    generated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    forecast_date   DATE NOT NULL,
    horizon_day     INT NOT NULL,         -- 1..10 (câte zile în viitor)
    temp_min_p50    DOUBLE PRECISION,
    temp_min_p10    DOUBLE PRECISION,
    temp_min_p90    DOUBLE PRECISION,
    temp_max_p50    DOUBLE PRECISION,
    temp_max_p10    DOUBLE PRECISION,
    temp_max_p90    DOUBLE PRECISION,
    wind_speed_p50  DOUBLE PRECISION,
    humidity_p50    DOUBLE PRECISION,
    precip_sum_p50  DOUBLE PRECISION,
    precip_prob     DOUBLE PRECISION,     -- P(precip > 1mm)
    storm_prob      DOUBLE PRECISION,     -- P(furtună)
    fog_prob        DOUBLE PRECISION,     -- P(ceață)
    heatwave_prob   DOUBLE PRECISION,     -- P(caniculă)
    ensemble_spread DOUBLE PRECISION,     -- P90 − P10 temp_max (incertitudine)
    UNIQUE (city_id, generated_at, forecast_date)
);

CREATE INDEX idx_mc_pred_latest ON monte_carlo_predictions(city_id, generated_at DESC);
```

### 10.7 `seasonal_climatology` — Medii sezoniere per zi calendaristică

```sql
CREATE TABLE seasonal_climatology (
    id              SERIAL PRIMARY KEY,
    city_id         INT NOT NULL REFERENCES cities(id),
    day_of_year     INT NOT NULL,         -- 1..366
    regime_id       INT,                  -- NULL = toate regimurile
    temp_min_mean   DOUBLE PRECISION,
    temp_min_std    DOUBLE PRECISION,
    temp_max_mean   DOUBLE PRECISION,
    temp_max_std    DOUBLE PRECISION,
    wind_speed_mean DOUBLE PRECISION,
    humidity_mean   DOUBLE PRECISION,
    precip_sum_mean DOUBLE PRECISION,
    sample_count    INT NOT NULL,         -- câte zile au contribuit
    UNIQUE (city_id, day_of_year, regime_id)
);
```

---

## 11. Proceduri Stocate Noi

### 11.1 Construirea Vectorilor și a Regimurilor

| Procedură | Descriere |
|---|---|
| `sp_build_weather_vector(p_city_id)` | Construiește vectorul 25D + derivate + detectoare pentru toate zilele unui oraș |
| `sp_compute_climate_zone(p_city_id)` | Determină zona climatică pe baza coordonatelor și a statisticilor sezoniere |
| `sp_run_kmeans(p_climate_zone, p_k)` | Rulează k-means pe toate orașele dintr-o zonă climatică; populează `weather_regimes` și `daily_regimes` |
| `sp_label_regimes()` | Etichetează automat regimurile pe baza centroidului și a ponderii detectoarelor |

### 11.2 Construirea Modelului Markov și HMM

| Procedură | Descriere |
|---|---|
| `sp_build_markov_tensor(p_climate_zone)` | Construiește tensorul de tranziție ordin 2 populând `markov_transitions` |
| `sp_add_structural_zeros()` | Populează `structural_zeros` pe baza distanței dintre centroizi și a constrângerilor fizice |
| `sp_train_hmm(p_city_id, p_hidden_states)` | Antrenează HMM-ul prin Baum-Welch; populează `hidden_states` și `hidden_transitions` |
| `sp_compute_seasonal_climatology(p_city_id)` | Populează `seasonal_climatology` cu medii și deviații standard per zi calendaristică |

### 11.3 Predicție

| Procedură | Descriere |
|---|---|
| `sp_run_monte_carlo(p_city_id, p_start_date, p_days, p_trajectories)` | Rulează simularea Monte Carlo completă și populează `monte_carlo_predictions` |
| `sp_get_probabilistic_forecast(p_city_id, p_date)` | Returnează predicția probabilistică dintr-un cache recent (fără a re-rula Monte Carlo) |
| `sp_get_storm_probability_this_week(p_city_id)` | Probabilitatea de furtună în următoarele 7 zile |
| `sp_get_extreme_heat_risk(p_city_id, p_threshold)` | Probabilitatea de a depăși un prag de temperatură în următoarele 10 zile |
| `sp_get_weather_regime_forecast(p_city_id, p_days)` | Evoluția cea mai probabilă a regimurilor în următoarele N zile |

---

## 12. Plan de Implementare (Faze)

### FAZA 1: Vectorul și Detectoarele (Săptămâna 1-2)

**Obiectiv**: Fiecare zi istorică primește un vector 39D complet.

- [ ] Migrație: extinde `forecasts` cu coloanele din Layer C-F (presiune, precipitații, radiație)
  - Sau: creează tabelă separată `weather_vectors` cu toate cele 25 de dimensiuni
- [ ] Procedura `sp_build_weather_vector`: calculează vectorul 25D + derivate temporale
- [ ] Procedura `sp_compute_recipe_scores`: calculează cele 6 scoruri fuzzy
- [ ] Funcție `fn_dew_point`: implementează formula Magnus pentru punctul de rouă
- [ ] Funcții auxiliare: `fn_sigmoid`, `fn_rev_sigmoid`, `fn_seasonal_weight`
- [ ] Populare date din Open-Meteo: adaugă parametrii lipsă (presiune, precipitații, radiație) la import
- [ ] Testare: vectorii pentru un oraș (București) pe 3 ani trebuie să fie computați corect

### FAZA 2: Clustering și Regimuri (Săptămâna 2-3)

**Obiectiv**: 16 regimuri meteo etichetate per zonă climatică.

- [ ] Tabela `weather_regimes`
- [ ] Tabela `daily_regimes`
- [ ] Tabela `seasonal_climatology`
- [ ] Procedura `sp_run_kmeans`: implementare k-means în PL/pgSQL sau Java
  - **Decizie**: k-means este iterativ și costisitor. Mai bine în Java (biblioteca Apache Commons Math)
    și apoi rezultatele încărcate în PostgreSQL.
- [ ] Serviciu Java: `ClusteringService` — execută k-means, salvează centroizii
- [ ] Etichetare manuală asistată a regimurilor (inspecția centroidului + ponderile detectoarelor)
- [ ] Testare: silhouette score > 0.3 pentru clustering

### FAZA 3: Markov și HMM (Săptămâna 3-4)

**Obiectiv**: Tensorul de tranziție și stările ascunse.

- [ ] Tabela `markov_transitions`
- [ ] Tabela `structural_zeros`
- [ ] Tabelele `hidden_states`, `hidden_transitions`
- [ ] Procedura `sp_build_markov_tensor`: numără tripleții și normalizează
- [ ] Procedura `sp_add_structural_zeros`: identifică tranzițiile fizic imposibile
- [ ] Serviciu Java: `HmmTrainingService` — Baum-Welch cu 8 stări ascunse
  - **Decizie**: HMM training necesită iterații și operații matriciale. Implementare în Java.
- [ ] Procedura `sp_compute_seasonal_climatology`: medii per zi calendaristică
- [ ] Testare: verficare manuală a tranzițiilor ("ploaie → ploaie" mai probabil decât "ploaie → senin")

### FAZA 4: Motorul Monte Carlo (Săptămâna 4-5)

**Obiectiv**: Simulare completă și extragere probabilități.

- [ ] Tabela `monte_carlo_predictions`
- [ ] Serviciu Java: `MonteCarloEngine` — 5000 traiectorii × 10 zile
  - Componente:
    - `TransitionSampler`: eșantionează regimul următor din tensorul Markov
    - `StructuralZeroFilter`: verifică și resample-ază tranzițiile interzise
    - `WeatherVectorSampler`: eșantionează din multinormală (Cholesky)
    - `SeasonalBlender`: blendare exponențială cu priorul sezonier
    - `ProbabilityExtractor`: calculează P10/P50/P90 și probabilități evenimente
- [ ] Procedura `sp_get_probabilistic_forecast`: returnează din cache
- [ ] Integrare cu UI: `PredictionController` extins cu intervale de încredere
- [ ] Testare: backtesting — compară predicțiile cu datele reale din ultimul an

### FAZA 5: UI și Rafinare (Săptămâna 5-6)

**Obiectiv**: Interfață completă cu grafice probabilistice.

- [ ] Grafice cu benzi de incertitudine (P10-P90) în JavaFX
- [ ] Panou de probabilități: "Probabilitate ploaie: 35%", "Risc furtună: 8%"
- [ ] Hartă de risc: colorare orașe după probabilitatea de evenimente extreme
- [ ] Notificări automate când P(eveniment extrem) > prag (ex: 30%)
- [ ] Testare integrare: flux complet de la API → vectori → regimuri → Markov → HMM → Monte Carlo → UI
- [ ] Optimizare: caching agresiv, Monte Carlo doar la refresh explicit sau schimbare de regim

---

## 13. Verificare Corectitudine

### 13.1 Backtesting

Pentru fiecare zi din ultimul an (366 de zile):

```
1. Rulează motorul de predicție folosind DOAR datele dinaintea acelei zile
   (simulând ce ar fi prezis sistemul în acea dimineață)
2. Compară cu ce S-A ÎNTÂMPLAT de fapt (din datele istorice)
3. Calculează metrici:
   - MAE (Mean Absolute Error) pentru temp_max, temp_min
   - Brier Score pentru probabilități (ploaie, furtună)
   - CRPS (Continuous Ranked Probability Score) pentru distribuții
   - Coverage: cât la sută din observații cad în [P10, P90]
```

Un model bun:
- Coverage ≈ 80% (intervalul [P10, P90] acoperă 80% din observații)
- Brier Score < 0.15 (mai bun decât climatologia pură)
- MAE temp_max < 2.5°C pentru ziua 1, < 5°C pentru ziua 7

### 13.2 Verificări de Consistență Fizică

- [ ] După o zi cu `precip_sum > 20mm`, umiditatea zilei următoare NU este niciodată sub 50%
  (verificat pe toate traiectoriile Monte Carlo)
- [ ] Corelația temp_max ↔ humidity în vectorii emiși este negativă (verificată pe Σ)
- [ ] Probabilitatea de "ceață + vânt > 30 km/h" este < 0.001 (structural zero)

### 13.3 Comparație cu Baseline

| Metodă | MAE temp_max (ziua 1) | MAE temp_max (ziua 7) |
|---|---|---|
| Climatologie pură (media calendaristică) | ~4.5°C | ~4.5°C |
| Persistență (mâine = azi) | ~2.8°C | ~5.5°C |
| Open-Meteo API (model numeric) | ~1.5°C | ~3.5°C |
| **Motorul nostru (Markov + HMM + MC)** | **~2.0°C (țintă)** | **~4.0°C (țintă)** |
| Media ponderată (ce avem acum) | ~3.2°C | ~4.8°C |

Ținta este să depășim semnificativ baseline-ul simplu, apropiindu-ne de
modelele numerice operaționale (Open-Meteo folosește GFS/ECMWF).

---

## ANEXE

### A. Dependențe Java Noi

```xml
<!-- Descompunere matricială (Cholesky, SVD) -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-math3</artifactId>
    <version>3.6.1</version>
</dependency>
```

### B. Configurare în `.env`

```properties
# Parametri Monte Carlo
MC_TRAJECTORIES=5000
MC_HORIZON_DAYS=10
MC_DECAY_TAU=5

# Parametri Clustering
KMEANS_K=16
KMEANS_MAX_ITERATIONS=100

# Parametri HMM
HMM_HIDDEN_STATES=8
HMM_MAX_ITERATIONS=50
HMM_CONVERGENCE_THRESHOLD=0.001
```

### C. Glosar

| Termen | Definiție |
|---|---|
| **Regim meteo** | Cluster de zile cu vectori meteo similari (ex: "anticiclon uscat de vară") |
| **Stare ascunsă** | Nivelul intermediar HMM care guvernează secvențele de regimuri |
| **Tensor Markov** | Matricea 3D de probabilități P(rₜ₊₁ | rₜ₋₁, rₜ, sezon) |
| **Zero structural** | Tranziție imposibilă fizic (nu doar improbabilă) |
| **Semi-Markov** | Markov unde probabilitatea de tranziție depinde de durata petrecută în starea curentă |
| **Ensemble** | Cele N=5000 de traiectorii Monte Carlo |
| **Prior sezonier** | Distribuția istorică pentru o anumită săptămână calendaristică |
| **Blendare** | Amestecarea predicției Markov cu priorul sezonier, ponderată exponențial |
| **P10/P50/P90** | Percentilele 10%, 50% (mediana), 90% din distribuția de ensemble |
| **Baum-Welch** | Algoritm EM pentru antrenarea parametrilor HMM |
| **Cholesky** | Descompunere a matricei de covarianță pentru eșantionare multinormală |
