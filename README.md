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
│  (8 tab-uri)│                │  (business logic│              │  + proceduri │
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
| 🌤️ Prognoză | Prognoza zilnică pe 10 zile cu date reale de la Open-Meteo |
| 🗺️ Hartă | Hartă 2.5D a României cu bare de extrudare pentru temperaturi |
| 📊 Comparații | Compară prognoze între ani, sezoane, luni sau aceeași zi |
| 📈 Statistici | Detectare anomalii, evoluție temperaturi, avertizări meteo |
| 🔮 Predicții | Monte Carlo probabilist: benzi P10-P90, probabilități evenimente |
| 🏆 Clasamente | Ranking orașe după temperatură, umiditate, similaritate climatică |
| 🎯 Acuratețe | Backtest predictii vs real, metrici MAE/RMSE, învățare din erori |
| 💬 Comentarii | Voturi și comentarii utilizatori, reputație |

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

**34 teste**, 0 eșecuri:
- `CityServiceTest` (3)
- `UserServiceTest` (5)
- `ForecastServiceTest` (3)
- `WeatherVectorServiceTest` (7)
- `RecipeDetectorServiceTest` (6)
- `AccuracyMetricsTest` (2)
- `AccuracyServiceTest` (3)
- `ReinforcementServiceTest` (2)
- `DatabaseInitializerTest` (3)

## 🛠️ Stack tehnic

- **Java 21** + **JavaFX 23.0.2**
- **Maven**
- **PostgreSQL 16** (Docker)
- **Jackson** (JSON)
- **Apache Commons Math3** (statistică)
- **JUnit 5** + **Mockito**
- **Open-Meteo API** (date istorice + prognoze)

## 🗄️ Baza de date

- **14 migrații** numerotate (`001_create_countries.sql` → `014_add_reinforcement_log.sql`)
- **39 proceduri stocate** (prefix `sp_` și `fn_`)
- Auto-initializare la pornirea aplicației via `DatabaseInitializer`
- Seed cu România + 25 de orașe

## ⌨️ Scurtături

| Combinație | Acțiune |
|------------|---------|
| `Ctrl+1` … `Ctrl+8` | Comută între tab-uri |

## 📁 Structură proiect

```
SGBD/
├── src/main/java/com/sgbd/
│   ├── controller/          # 8 controller-e JavaFX
│   ├── model/               # Entități (City, Forecast, User, Vote)
│   ├── service/             # Servicii business + import date
│   └── service/prediction/  # Motor ML: vectori, clustering, Markov, HMM, Monte Carlo, RL
├── src/test/java/           # 34 teste unitare
├── src/main/resources/com/sgbd/css/
│   └── style.css            # Temă dark glassmorphism
├── db/migrations/           # 14 migrații SQL
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
