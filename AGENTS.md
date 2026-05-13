<!-- AGENTS.md — SGBD Prognoză Meteo -->

## Prezentare Generală

Proiect universitar românesc (SGBD — Sisteme de Gestiune a Bazelor de Date): aplicație desktop de prognoză meteo care stochează prognoze zilnice per oraș (temperatură min/max, viteza vântului, pictogramă, indice UV, umiditate, avertizări). Include comentarii utilizatori, votare acuratețe prognoze, statistici comparative, detectare anomalii, clasamente orașe și predicții — toate calculate server-side prin proceduri stocate PostgreSQL și consumate de un client JavaFX.

**Arhitectura de ansamblu:**

```
┌─────────────────────────────────────────┐
│         JavaFX Desktop Client           │
│  ┌─────────┐ ┌─────────┐ ┌──────────┐  │
│  │Controllers│ │ Services │ │   DAOs   │  │
│  │  (Java)   │ │  (Java)  │ │  (JDBC)  │  │
│  └────┬────┘ └────┬────┘ └────┬─────┘  │
└───────┼───────────┼───────────┼────────┘
        │           │           │
        └───────────┴───────────┘
                    │ JDBC direct
        ┌───────────┴───────────┐
        │    PostgreSQL Server   │
        │  ┌──────┐  ┌────────┐ │
        │  │Tabele│  │Proceduri│ │
        │  │  7   │  │  ~17   │ │
        │  └──────┘  └────────┘ │
        └───────────────────────┘
```

**Nu există strat API REST.** Clientul JavaFX se conectează direct la PostgreSQL prin JDBC și doar afișează rezultatele. Logica de business (statistici, comparații, clasamente, anomalii, predicții, reputație) rulează exclusiv în proceduri stocate.

**Sursa de date externă:** Open-Meteo API (archive-api.open-meteo.com + api.open-meteo.com) pentru date istorice și prognoze. Aplicația importă datele în PostgreSQL și le procesează local.

**Documente suplimentare în proiect:**
- `ARCHITECTURE.md` — schema completă țintă a bazei de date (16 tabele, 64 proceduri, triggeri, view-uri). **Notă:** doar o parte este implementată efectiv.
- `PLAN.md` — plan pentru un motor probabilistic de predicție (Monte Carlo, HMM, k-means, Markov). **Status: doar planificare, nimic implementat.**

---

## Stivă Tehnologică

| Componentă | Tehnologie / Versiune |
|---|---|
| Limbaj | Java 21 |
| UI | JavaFX 23.0.2 (controls, fxml, charts integrate) |
| Build | Maven 3.x (pom.xml) |
| Plugin run | javafx-maven-plugin 0.0.8 |
| RDBMS | PostgreSQL (driver 42.7.1) |
| JSON | Jackson 2.16.1 |
| Teste | JUnit Jupiter 5.10.2 |
| HTTP Client | java.net.http.HttpClient (built-in) |

---

## Comenzi de Build și Run

```bash
# Compilează proiectul
mvn compile

# Rulează aplicația JavaFX
mvn javafx:run

# Clasele principale
Main class: com.sgbd.controller.MainApp
```

**Configurare bază de date (mediu):**
```bash
export DB_URL="jdbc:postgresql://localhost:5432/prognoza_meteo"
export DB_USER="postgres"
export DB_PASSWORD="postgres"
```

Dacă variabilele nu sunt setate, `DatabaseConnection` folosește valorile implicite de mai sus.

---

## Structura Proiectului

```
├── pom.xml                          # Configurare Maven
├── db/
│   ├── migrations/                  # Migrații SQL numerotate (001–008)
│   │   ├── 001_create_countries.sql
│   │   ├── 002_create_cities.sql
│   │   ├── 003_create_forecasts.sql
│   │   ├── 004_create_users.sql
│   │   ├── 005_create_votes.sql
│   │   ├── 006_create_comments.sql
│   │   ├── 007_create_forecast_log.sql
│   │   └── 008_add_data_source.sql
│   ├── procedures/                  # Proceduri stocate PostgreSQL (~17 fișiere)
│   │   ├── sp_daily_forecast_report.sql
│   │   ├── sp_predict_week.sql
│   │   ├── sp_detect_anomalies.sql
│   │   ├── sp_city_rankings.sql
│   │   ├── sp_classify_similar_cities.sql
│   │   ├── sp_comparison_same_day.sql
│   │   ├── sp_compare_monthly.sql
│   │   ├── sp_compare_annual.sql
│   │   ├── sp_generate_forecasts.sql
│   │   ├── sp_update_all_warnings.sql
│   │   ├── sp_generate_icon.sql
│   │   ├── sp_user_reputation.sql
│   │   ├── sp_forecast_score.sql
│   │   ├── sp_get_map_data.sql
│   │   ├── sp_city_weather_evolution.sql
│   │   ├── sp_identify_error_forecasts.sql
│   │   └── sp_auto_warn_users.sql
│   └── seeds/
│       └── seed_countries_cities.sql # 15 țări + 29 orașe europene
├── src/main/
│   ├── java/com/sgbd/
│   │   ├── controller/              # Controllere JavaFX (7 taburi)
│   │   │   ├── MainApp.java         # Punct de intrare + TabPane
│   │   │   ├── ForecastController.java
│   │   │   ├── MapController.java
│   │   │   ├── ComparisonController.java
│   │   │   ├── StatsController.java
│   │   │   ├── PredictionController.java
│   │   │   ├── RankingsController.java
│   │   │   └── CommentsController.java
│   │   ├── service/                 # Servicii JDBC + API extern
│   │   │   ├── ForecastService.java
│   │   │   ├── CityService.java
│   │   │   ├── UserService.java
│   │   │   ├── StatisticsService.java
│   │   │   ├── MapService.java
│   │   │   ├── WeatherApiService.java
│   │   │   └── WeatherImporterService.java
│   │   ├── model/                   # POJO-uri
│   │   │   ├── Forecast.java
│   │   │   ├── City.java
│   │   │   ├── Country.java
│   │   │   ├── User.java
│   │   │   ├── Vote.java
│   │   │   ├── Comment.java
│   │   │   ├── Anomaly.java
│   │   │   ├── CityRanking.java
│   │   │   └── ComparisonResult.java
│   │   └── util/
│   │       └── DatabaseConnection.java
│   └── resources/com/sgbd/css/
│       └── style.css                # Stilizare JavaFX
```

---

## Schema Bazei de Date (Implementată)

**Tabele existente:**

| Tabelă | Scop |
|---|---|
| `countries` | Țări (id, name, code) |
| `cities` | Orașe (id, name, country_id, latitude, longitude, is_important) |
| `forecasts` | Prognoze zilnice — tabela centrală. Coloane: city_id, date, temp_min, temp_max, wind_speed, icon_type, uv_index, humidity, warning_text, data_source, fetched_at, created_at, updated_at |
| `users` | Utilizatori (id, username, password_hash, reputation, created_at) |
| `votes` | Voturi acuratețe prognoză (user_id, forecast_id, is_accurate) |
| `comments` | Comentarii la prognoze cu suport reply (parent_comment_id) |
| `forecast_log` | Log modificări prognoze (JSONB old_values/new_values) |

**Constrângeri cheie:**
- `UNIQUE(city_id, date)` pe forecasts
- `UNIQUE(name, country_id)` pe cities
- `UNIQUE(user_id, forecast_id)` pe votes
- `CHECK(humidity BETWEEN 0 AND 100)`

**Indexi creați:**
- `idx_forecasts_city`, `idx_forecasts_date`, `idx_forecasts_city_date`, `idx_forecasts_source`, `idx_forecasts_fetched`
- `idx_cities_country`
- `idx_votes_forecast`, `idx_votes_user`
- `idx_comments_forecast`, `idx_comments_user`, `idx_comments_parent`
- `idx_forecast_log_forecast`

---

## Proceduri Stocate Implementate

| Procedură | Tip | Scop |
|---|---|---|
| `sp_daily_forecast_report(city_id, date)` | funcție | Raport complet zilnic cu voturi și comentarii |
| `sp_predict_week(city_id, start_date, days)` | funcție | Predicție pe baza mediei aceleiași zile din ani trecuți + fallback ultimele 7 zile |
| `sp_detect_anomalies(city_id, year)` | funcție | Detectare anomalii (regula 2-sigma față de media lunară) |
| `sp_city_rankings(criterion, days)` | funcție | Clasamente: hottest, coldest, windiest, most_humid, most_warnings, most_extreme |
| `sp_classify_similar_cities(city_id, days)` | funcție | Similaritate orașe pe baza distanței euclidiene (temp, umiditate, vânt) |
| `sp_comparison_same_day(city_id, date)` | funcție | Compară cu media aceleiași zile din alți ani + media sezonieră |
| `sp_compare_monthly(city_id, year, month)` | funcție | Compară lună curentă cu media lunară istorică (per zi) |
| `sp_compare_annual(city_id, year)` | funcție | Compară an selectat cu media tuturor anilor |
| `sp_generate_forecasts(year)` | procedură | Generează prognoze aleatorii realiste pentru toate orașele |
| `sp_update_all_warnings(year)` | procedură | Generează avertizări automate pe baza pragurilor (furtună, caniculă, ger, etc.) |
| `sp_generate_icon(temp, humidity, wind, uv)` | funcție | Selectează pictograma pe baza parametrilor meteo |
| `sp_user_reputation(user_id)` | funcție | Calculează reputația: (voturi corecte / total) × (1 + ln(1 + comentarii)) |
| `sp_forecast_score(forecast_id)` | funcție | Scor ponderat al prognozei pe baza voturilor și reputației votanților |
| `sp_get_map_data(country_id, date)` | funcție | Date pentru hartă (coordonate + temperaturi) |
| `sp_city_weather_evolution(city_id, from, to)` | funcție | Evoluție meteo pentru grafice |
| `sp_identify_error_forecasts(city_id, threshold)` | funcție | Prognoze cu >50% voturi negative |
| `sp_auto_warn_users(city_id, keywords)` | funcție | Caută avertizări active după cuvinte-cheie |

---

## Fluxul Aplicației

### Pornire (MainApp)
1. Creează `TabPane` cu 7 taburi: Prognoză, Hartă, Comparații, Statistici & Anomalii, Predicții, Clasamente, Comentarii & Voturi.
2. Verifică prospețimea datelor într-un thread separat (`checkDataFreshnessOnStartup`).
3. Dacă datele sunt mai vechi de 24 ore, afișează dialog de confirmare pentru reîmprospătare automată.

### Import date (WeatherImporterService)
1. `WeatherApiService` interoghează Open-Meteo (historical/forecast) și parsează JSON cu Jackson.
2. `WeatherImporterService` face upsert în tabela `forecasts` (ON CONFLICT DO UPDATE).
3. Derivează pictograma automat (`deriveIcon`) și generează avertizări (`generateWarningsForPeriod`).
4. Limitează apelurile API: max 50 per run, delay 250ms între apeluri.
5. Șterge prognozele vechi de peste 2 zile (`cleanupOldForecasts`).

### Afișare prognoze (ForecastController)
1. Selectare țară → oraș → dată.
2. Afișează prognoza pe 7 zile în `TableView`.
3. Suport import istoric per oraș sau pentru toate orașele.

### Predicții (PredictionController)
- **DB:** apelează `sp_predict_week` (medie istorică aceeași zi + tendință ultimele 7 zile).
- **API:** importă direct prognoza Open-Meteo și o afișează.

### Autentificare (CommentsController + UserService)
- Login/înregistrare cu username + parolă.
- Parola este hash-uită cu **SHA-256** (atât în Java cât și în proceduri stocate, unde e cazul).
- Votarea prognozelor și adăugarea comentariilor necesită autentificare.
- Reputația se recalculează automat la fiecare vot/comentariu.

---

## Convenții de Cod

### Java
- **Naming:** `camelCase` variabile/metode, `PascalCase` clase, `snake_case` doar la mapare SQL.
- **Limba:** cod și comentarii în **română** (convenție curs).
- **Package-uri:** `com.sgbd.controller`, `com.sgbd.service`, `com.sgbd.model`, `com.sgbd.util`.
- **Controller pattern:** fiecare controller expune `getView()` care returnează un `javafx.scene.Node`.
- **Servicii JDBC:** toate apelurile la DB folosesc `try-with-resources` pe `Connection`, `PreparedStatement`, `ResultSet`.
- **Threading:** operațiile lungi (import API) rulează în thread-uri noi; actualizările UI se fac prin `Platform.runLater()`.

### SQL / PostgreSQL
- **Naming:** `snake_case` pentru toate obiectele DB (tabele, coloane, proceduri).
- **Proceduri stocate:** prefix `sp_` (ex: `sp_predict_week`).
- **Funcții:** prefix `sp_` sau `fn_` (ex: `sp_generate_icon`).
- **Comentarii:** bloc header la începutul fiecărei proceduri cu descrierea parametrilor și scopului.
- **Migrații:** fișiere numerotate `001_`, `002_`, etc. **Nu modifica migrații aplicate; adaugă mereu unele noi.**
- **Format:** cuvinte cheie SQL în majuscule, o clauză per linie.

### CSS (JavaFX)
- Fișier unic: `style.css` în `src/main/resources/com/sgbd/css/`.
- Temă bazată pe albastru (`#2980b9`, `#2c3e50`).

---

## Testare

- JUnit Jupiter 5.10.2 este declarat în `pom.xml` (scope test).
- **Momentan nu există teste implementate** în `src/test/java`.
- Pentru testare manuală, rulează aplicația și verifică:
  1. Import date istorice pentru un oraș (ex: București).
  2. Detectare anomalii pentru acel oraș.
  3. Compară prognoza curentă cu media istorică.
  4. Votează o prognoză și verifică recalcularea reputației.

---

## Considerații de Securitate

1. **Credențiale DB:** citite din variabile de mediu (`DB_URL`, `DB_USER`, `DB_PASSWORD`), cu fallback la valori implicite (`postgres`/`postgres`). **În producție, setează variabilele de mediu și elimină fallback-ul.**
2. **Hash parole:** folosește SHA-256. **Notă:** pentru producție, ar trebui înlocuit cu bcrypt/Argon2 (documentat în ARCHITECTURE.md dar neimplementat).
3. **SQL Injection:** mitigată prin folosirea exhaustivă a `PreparedStatement`/`CallableStatement` cu parametri poziționali.
4. **Client direct la DB:** arhitectura curentă nu are strat de API REST; clientul JavaFX deține logica de conectare JDBC. Orice utilizator al aplicației poate extrage string-ul de conectare.
5. **API extern:** apeluri HTTP GET neautentificate către Open-Meteo (nu necesită cheie API).

---

## Dezvoltare Ulterioară (Planuri Documentate)

Proiectul conține două documente de planificare ambițioase care **nu sunt implementate**:

1. **`PLAN.md`** — motor de predicție probabilistică:
   - Vector meteo 25D + derivate temporale + detectoare fuzzy de fenomene (ceață, furtună, ciclon, caniculă, inversiune).
   - Clustering k-means în 16 regimuri meteo.
   - Lanț Markov de ordin 2 cu zerouri structurale.
   - Hidden Markov Model (8 stări ascunse, Baum-Welch).
   - Simulare Monte Carlo (5000 traiectorii × 10 zile).
   - Blendare exponențială cu prior sezonier.
   - Extragerile P10/P50/P90 și probabilități evenimente.

2. **`ARCHITECTURE.md`** — schemă extinsă a bazei de date cu tabele suplimentare (`icon_types`, `uv_levels`, `warnings`, `forecast_history`, `seasonal_statistics`, `anomaly_events`, `city_similarity_cache`, `user_notifications`, `user_reputation_history`, `city_climate_profiles`), triggeri, view-uri, și o arhitectură de proceduri stocate mult mai extinsă.

**Pentru agenți:** orice modificare trebuie să plece de la **starea actuală a codului** (ceea ce este implementat), nu de la documentele de planificare.

---

## Git Workflow

- Branch naming: `feature/<name>`, `fix/<name>`, `docs/<name>`
- Commit messages în engleză, imperativ (ex: `Add daily forecast procedure`)
- Commits mici și focusate
- Fără commit direct pe `master` — feature branches + merge
