# Prognoză Meteo — România

Aplicație desktop JavaFX pentru prognoze meteo probabilistice, statistici și clasamente pentru orașele din România. Proiect universitar pentru cursul de Sisteme de Gestiune a Bazelor de Date (SGBD).

## 🚀 Pornire rapidă

```bash
# 1. Pornește PostgreSQL
./start-db.sh

# 2. Verifică setup-ul
./verify-setup.sh

# 3. Pornește aplicația
./run-app.sh
```

Sau manual:
```bash
docker-compose up -d
DB_URL=jdbc:postgresql://localhost:5433/prognoza_meteo mvn javafx:run
```

## 🏗️ Arhitectură

```
┌─────────────┐     JDBC      ┌─────────────────┐     SQL      ┌─────────────┐
│  JavaFX UI  │ ◄────────────► │  Java Services  │ ◄──────────► │ PostgreSQL  │
│  (3 tab-uri)│                │  (business logic│              │  + proceduri │
└─────────────┘                │   thin layer)   │              │  stocate     │
                               └─────────────────┘              └─────────────┘
                                        │
                                        ▼
                               ┌─────────────────┐
                               │   Open-Meteo    │
                               │   API (gratuit) │
                               └─────────────────┘
```

## 📊 Funcționalități

| Tab | Funcționalitate |
|-----|----------------|
| 🌤️ Acum | Dashboard unificat: prognoză zilnică, evoluție temperatură, predicții probabilistice P10-P90, comparații istorice, probabilități meteo, clasamente orașe |
| 🗺️ Hartă | Hartă 2.5D a României cu bare de extrudare, animații meteo (ploaie, ninsoare, soare, furtună), timelapse -7…+7 zile |
| ⋮ Mai multe | Setări (unități, sincronizare), despre aplicație |

## 🧠 Motor de predicție

1. **Vectori meteo 25D** — extragere din date istorice (temp, umiditate, vânt, presiune, radiație)
2. **6 detectoare de fenomene** — ceață, furtună, caniculă, ger, inversiune, strat marin
3. **K-Means clustering** — 16 regimuri climatice per zonă
4. **Lanț Markov de ordin 2** — tensor de tranziții condiționat de sezon
5. **HMM (8 stări ascunse)** — Baum-Welch pentru pattern-uri sinoptice
6. **Monte Carlo** — 5000 traiectorii, benzi de încredere P10/P50/P90
7. **Reinforcement Learning** — ajustare automată a ponderilor pe baza erorilor

## 🧪 Teste

```bash
mvn test
```

**38 teste**, 0 eșecuri:
- `CityServiceTest` (3)
- `UserServiceTest` (5)
- `ForecastServiceTest` (3)
- `WeatherVectorServiceTest` (7)
- `RecipeDetectorServiceTest` (6)
- `AccuracyMetricsTest` (2)
- `AccuracyServiceTest` (3)
- `ReinforcementServiceTest` (2)
- `DatabaseInitializerTest` (3)
- `BackgroundSyncServiceTest` (2)
- `AppStateTest` (2)

## 🛠️ Stack tehnic

- **Java 21** + **JavaFX 23.0.2**
- **Maven**
- **PostgreSQL 16** (Docker)
- **Jackson** (JSON)
- **Apache Commons Math3** (statistică)
- **JUnit 5** + **Mockito**
- **Open-Meteo API** (date istorice + prognoze)

## 🗄️ Baza de date

- **15 migrații** numerotate (`001_create_countries.sql` → `015_add_triggers.sql`)
- **39 proceduri stocate** (prefix `sp_` și `fn_`)
- **2 trigger-e** pentru automatizare (`trg_forecasts_updated_at`, `trg_update_reputation_on_vote`)
- Auto-initializare la pornirea aplicației via `DatabaseInitializer`
- Seed cu România + 25 de orașe

## ⌨️ Scurtături

| Combinație | Acțiune |
|------------|---------|
| `Ctrl+1` … `Ctrl+3` | Comută între tab-uri |

## 📁 Structură proiect

```
SGBD/
├── src/main/java/com/sgbd/
│   ├── controller/          # 3 controller-e active JavaFX (UnifiedDashboard, Map, More)
│   ├── model/               # Entități (City, Forecast, User, Vote)
│   ├── service/             # Servicii business + import date
│   └── service/prediction/  # Motor ML: vectori, clustering, Markov, HMM, Monte Carlo, RL
├── src/test/java/           # 38 teste unitare
├── src/main/resources/com/sgbd/css/
│   └── style.css            # Temă dark glassmorphism
├── db/migrations/           # 15 migrații SQL
├── db/seeds/                # Seed-uri (țări, orașe)
├── db/procedures/           # 39 proceduri stocate
├── docker-compose.yml
├── start-db.sh
├── run-app.sh
└── verify-setup.sh
```

## 📝 Licență

Proiect universitar — SGBD, Facultatea de Automatică și Calculatoare.
# Markov-Weather
