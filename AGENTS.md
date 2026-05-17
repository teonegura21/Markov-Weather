# AGENTS.md

Acest fișier oferă ghidaje pentru agenții AI care lucrează în acest repository. Citește-l integral înainte de a face modificări.

---

## 1. Prezentare generală

**Prognoza Meteo — România** este o aplicație desktop JavaFX pentru prognoze meteo probabilistice, statistici și clasamente pentru orașele din România. Proiect universitar pentru cursul de Sisteme de Gestiune a Bazelor de Date (SGBD).

**Arhitectura de ansamblu:**
```
┌─────────────┐     JDBC      ┌─────────────────┐     SQL      ┌─────────────┐
│  JavaFX UI  │ ◄────────────► │  Java Services  │ ◄──────────► │ PostgreSQL  │
│  (cod Java) │                │  (business logic│              │  + proceduri │
└─────────────┘                │   thin layer)   │              │  stocate     │
                               └─────────────────┘              └─────────────┘
                                        │
                                        ▼
                               ┌─────────────────┐
                               │   Open-Meteo    │
                               │   API (gratuit) │
                               └─────────────────┘
```

**Nu există strat REST.** Clientul desktop se conectează direct la PostgreSQL prin JDBC (HikariCP). Logica de business (statistici, comparații, clasamente, anomalii, predicții, reputație) rulează în mare parte în proceduri stocate pe serverul PostgreSQL.

**Stack:**
- **Java 21** + **JavaFX 23.0.2**
- **Maven 3** (build și packaging)
- **PostgreSQL 16** (container Docker, port host 5433)
- **HikariCP 5.1.0** (pool de conexiuni JDBC)
- **Jackson 2.16.1** (procesare JSON)
- **Apache Commons Math3 3.6.1** (statistică)
- **Apache Commons CSV 1.10.0**
- **JUnit Jupiter 5.10.2** + **Mockito 5.11.0** (testare)
- **JaCoCo 0.8.11** (raportare acoperire)
- **Checkstyle 3.3.1** (linting)
- **Maven Shade Plugin** (fat JAR)
- **Open-Meteo API** (date istorice + prognoze)

**Clasa principală:** `com.sgbd.controller.MainApp`

---

## 2. Comenzi de build, test și rulare

```bash
# Compilare
mvn compile

# Rulare toate testele
mvn test

# Rulare o singură clasă de test
mvn test -Dtest=CityServiceTest

# Rulare o singură metodă de test
mvn test -Dtest=CityServiceTest#testGetAllCountries

# Rulare aplicație (necesită display sau xvfb)
mvn javafx:run

# Packaging fat JAR (sare peste teste)
mvn clean package -DskipTests
# Output: target/prognoza-meteo-1.0.0.jar

# Generare raport de acoperire (JaCoCo)
mvn test
# Raport: target/site/jacoco/index.html

# Verificare stil (Checkstyle)
mvn checkstyle:check
# Reguli în checkstyle.xml (linie max 120 caractere, fără import cu *, etc.)
```

Compilatorul rulează cu flag-ul `-Xlint:unchecked`.

**Scripturi utilitare:**
- `./start-db.sh` — pornește containerul PostgreSQL în Docker și așteaptă readiness.
- `./run-app.sh` — setează variabilele de mediu DB_* și pornește aplicația via `mvn javafx:run` (folosește `xvfb-run` automat în mediu headless).
- `./verify-setup.sh` — verifică Docker, compilează proiectul și rulează testele.
- `./backup-db.sh` / `./restore-db.sh` — backup și restore PostgreSQL.

---

## 3. Setup bază de date

```bash
# Pornire PostgreSQL în Docker (port host 5433)
./start-db.sh
```

La prima pornire, `DatabaseInitializer` rulează automat:
1. **Migrațiile** din `db/migrations/` (fișiere numerotate `001_` … `018_`).
2. **Seed-urile** din `db/seeds/` (țări + orașe).
3. **Procedurile stocate** din `db/procedures/` (39 de fișiere SQL).

Inițializarea este **idempotentă**: se verifică tabela `_schema_version` și un hash SHA-256 al migrațiilor; dacă schema este la zi, se sare peste reaplicare.

**Prioritate configurare:**
1. Variabile de mediu (`DB_URL`, `DB_USER`, `DB_PASSWORD`)
2. Fișier `.env` din rădăcina proiectului
3. `src/main/resources/application.properties`
4. Valori implicite

**Valori implicite:**
- URL: `jdbc:postgresql://localhost:5433/prognoza_meteo`
- User: `postgres`
- Parolă: `postgres`

**Parametri HikariCP** (configurabili prin aceleași surse):
- `db.pool.maxSize=10`
- `db.pool.minIdle=2`
- `db.pool.connectionTimeout=5000`
- `db.pool.idleTimeout=600000`
- `db.pool.maxLifetime=1800000`

---

## 4. Organizarea codului

### 4.1 Pachete și clase principale

```
src/main/java/com/sgbd/
├── controller/          # 4 clase — controllerele tab-urilor JavaFX
│   ├── MainApp.java                    # Entry point; gestionează navigația, cache view-uri, inițializare BD
│   ├── UnifiedDashboardController.java # Tab "Acum": dashboard cu prognoză, grafic, clasamente, predicții
│   ├── MapController.java              # Tab "Hartă": hartă 2.5D a României cu bare de extrudare
│   └── MoreController.java             # Tab "Mai multe": setări, despre, export
├── model/               # 10 clase — entități și DTO-uri simple (POJO)
│   ├── City.java, Country.java, Forecast.java, User.java, Vote.java
│   ├── Comment.java, Anomaly.java, CityRanking.java, ComparisonResult.java, HourlyForecast.java
├── service/             # 11 clase — logică business și import date
│   ├── CityService.java, ForecastService.java, UserService.java, StatisticsService.java
│   ├── MapService.java, WeatherApiService.java, WeatherImporterService.java
│   ├── DataPopulationService.java, ExportService.java, BackgroundSyncService.java
│   └── prediction/      # 12 clase — pipeline ML probabilistic
│       ├── PredictionEngineService.java   # Orchestrator pipeline
│       ├── WeatherVectorService.java      # Vectori meteo 25D
│       ├── RecipeDetectorService.java     # Detectoare de fenomene (ceață, furtună, caniculă, ger, inversiune, strat marin)
│       ├── ClusteringService.java         # K-Means clustering — 16 regimuri climatice
│       ├── MarkovModelService.java        # Lanț Markov de ordin 2
│       ├── HmmTrainingService.java        # HMM cu 8 stări ascunse (Baum-Welch)
│       ├── MonteCarloEngine.java          # 5000 de traiectorii, benzi P10/P50/P90
│       ├── AccuracyService.java, AccuracyMetrics.java, CityAccuracyRanking.java
│       ├── ReinforcementService.java, ReinforcementLog.java
│       └── StartupOrchestratorService.java
└── util/                # 12 clase — infrastructură și utilitare
    ├── DatabaseConnection.java      # Fațadă pentru HikariCP (folosită de TOATE serviciile)
    ├── DatabaseConnectionPool.java  # Wrapper HikariCP cu inițializare lazy
    ├── DatabaseInitializer.java     # Rulează migrații, seed-uri, proceduri stocate la startup
    ├── ConfigLoader.java            # Încărcare config cu prioritate: env > .env > properties > default
    ├── LoggerUtil.java              # Factory pentru java.util.logging
    ├── SessionManager.java          # Singleton — stare globală (oraș selectat, dată, utilizator logat)
    ├── AppState.java                # Singleton — preferințe UI persistate (dimensiune fereastră, auto-sync, unități)
    ├── BaseController.java          # Clasă abstractă pentru controllere; oferă loading/error states și listeneri SessionManager
    ├── ValidationUtil.java          # Validări comune
    ├── AnimationUtil.java           # Utilitare animații JavaFX
    ├── ColorUtil.java, WeatherGradient.java, RomaniaMapData.java
```

### 4.2 Resurse

```
src/main/resources/
├── application.properties              # Configurații implicite
└── com/sgbd/
    ├── css/style.css                   # Temă unică dark glassmorphism (albastru accent #2980b9, #2c3e50)
    └── romania_border.bin              # Date binare pentru conturul hărții României
```

**Notă importantă:** UI-ul este construit **programatic în cod Java**, nu prin fișiere FXML. Deși `javafx-fxml` este în dependențe, nu există fișiere `.fxml` în proiect. Fiecare controller creează nodurile JavaFX direct.

### 4.3 Bază de date (fișiere SQL)

```
db/
├── migrations/          # 18 fișiere numerotate (001_create_countries.sql … 018_add_forecast_log_trigger.sql)
├── seeds/               # Seed-uri (țări, orașe)
│   └── seed_countries_cities.sql
└── procedures/          # 39 de fișiere SQL (prefix sp_ pentru proceduri, fn_ pentru funcții)
    # Exemple: sp_generate_forecasts.sql, sp_city_rankings.sql, sp_detect_anomalies.sql,
    # sp_predict_week.sql, sp_run_monte_carlo.sql, sp_user_reputation.sql,
    # fn_get_season.sql, fn_dew_point.sql, fn_sigmoid.sql
```

Schema bazei de date conține **16 tabele**, **14 views**, **6 trigger-e**, **18 indecși** și **64 de proceduri/funcții** (vezi `ARCHITECTURE.md` pentru documentația completă).

---

## 5. Convenții de cod

### 5.1 Java

- **Limbaj:** cod și comentarii în **română** (convenție de curs).
- **Naming:**
  - `camelCase` pentru variabile și metode.
  - `PascalCase` pentru clase.
  - `snake_case` doar pentru maparea coloanelor SQL.
- **Pachete:** `com.sgbd.controller`, `com.sgbd.service`, `com.sgbd.service.prediction`, `com.sgbd.model`, `com.sgbd.util`.
- **Ordine importuri:**
  1. `com.sgbd.*`
  2. `java.*`
  3. `javafx.*`
  4. Importuri statice (la final)
- **JDBC:** întotdeauna `try-with-resources` pentru `Connection`, `PreparedStatement`, `ResultSet`.
- **Threading UI:** operațiile lungi (import API, ML) rulează în thread-uri noi; actualizările UI se fac exclusiv prin `Platform.runLater()`.
- **Logging:** se folosește `java.util.logging` prin `LoggerUtil.getLogger(Clasa.class)`.
- **Error handling:**
  - În servicii: se propagă `SQLException` către calleri.
  - În controllere: se prind excepțiile și se afișează `Alert`.
- **Null safety:** se evită `null` brut unde e posibil; se folosesc colecții goale ca valori implicite.
- **Lungime linie:** maxim 120 caractere (impus de Checkstyle).

### 5.2 SQL / PostgreSQL

- **Naming:** `snake_case` pentru toate obiectele DB (tabele, coloane, proceduri).
- **Proceduri:** prefix `sp_` pentru proceduri business, `fn_` pentru funcții utilitare.
- **Migrații:** fișiere numerotate `001_`, `002_`, etc. **Niciodată nu modifica migrații deja aplicate; adaugă mereu unele noi.**
- **Formatare:** cuvinte-cheie SQL în MAJUSCULE, o clauză per linie.

### 5.3 CSS (JavaFX)

- Singurul fișier CSS este `src/main/resources/com/sgbd/css/style.css`.
- Temă dark glassmorphism cu accent albastru (`#2980b9`, `#2c3e50`).

---

## 6. Arhitectură aplicație

### 6.1 Controllere

- Fiecare controller de tab moștenește `BaseController`.
- `BaseController` oferă:
  - `getView()` — construiește și returnează un `StackPane` cu conținut, indicator de încărcare și label de eroare.
  - `buildContent(VBox)` — abstract, implementat în subclase.
  - `onCityChanged(City)` / `onDateChanged(LocalDate)` — hook-uri apelate automat când se schimbă orașul/data globală prin `SessionManager`.
  - `showLoading(boolean)`, `showError(String)`, `hideError()`.
- `MainApp` creează și cache-uiește view-urile; navigarea se face prin `switchView(key)`.
- Navigare programatică din controllere: `MainApp.navigateHome()`.

### 6.2 Servicii

- Serviciile sunt **stateless** și se instanțiază direct în controllere (fără framework DI).
- Fiecare serviciu care accesează BD folosește `DatabaseConnection.getConnection()` (pool HikariCP).
- `SessionManager` este singleton și ține starea globală (oraș selectat, dată curentă, utilizator autentificat).
- `AppState` este singleton și persistă preferințele locale (dimensiune fereastră, auto-sync, unități de măsură).

### 6.3 Pipeline de predicție (ML)

1. **Vectori meteo 25D** — extragere din date istorice (temperatură, umiditate, vânt, presiune, radiație).
2. **6 detectoare de fenomene** — ceață, furtună, caniculă, ger, inversiune, strat marin.
3. **K-Means clustering** — 16 regimuri climatice per zonă.
4. **Lanț Markov de ordin 2** — tensor de tranziții condiționat de sezon.
5. **HMM (8 stări ascunse)** — Baum-Welch pentru pattern-uri sinoptice.
6. **Monte Carlo** — 5000 de traiectorii, benzi de încredere P10/P50/P90.
7. **Reinforcement Learning** — ajustare automată a ponderilor pe baza erorilor.

### 6.4 Proceduri stocate

Logica complexă (statistici, comparații, clasamente, anomalii, predicții, reputație) rulează în PostgreSQL. Java apelează procedurile prin `CallableStatement` sau `PreparedStatement` cu `SELECT procedura(?)`.

---

## 7. Testare

### 7.1 Framework

- **JUnit Jupiter 5.10.2** + **Mockito 5.11.0**.
- **JaCoCo** generează raportul de acoperire la `mvn test`.

### 7.2 Strategie

- **Testele unitare** mocks-ează `DatabaseConnection` cu `MockedStatic` pentru a evita apeluri reale la BD.
- **Testele de integrare** (ex: `DatabaseInitializerTest`, `DatabaseConnectionPoolTest`) necesită o instanță PostgreSQL activă.
- Clasele și metodele de test sunt **package-private** (fără `public` inutil).

### 7.3 Inventar teste (38 de teste în 11 clase)

| Clasă de test | Nr. teste | Tip |
|---------------|-----------|-----|
| `CityServiceTest` | 3 | Unit (mocked DB) |
| `ForecastServiceTest` | 3 | Unit (mocked DB) |
| `UserServiceTest` | 5 | Unit (mocked DB) |
| `SqlInjectionTest` | 1 | Securitate |
| `WeatherVectorServiceTest` | 7 | Unit |
| `RecipeDetectorServiceTest` | 6 | Unit |
| `AccuracyMetricsTest` | 2 | Unit |
| `AccuracyServiceTest` | 3 | Unit |
| `ReinforcementServiceTest` | 2 | Unit |
| `DatabaseConnectionPoolTest` | 3 | Integrare (necesită PostgreSQL) |
| `DatabaseInitializerTest` | 3 | Integrare (necesită PostgreSQL) |

---

## 8. Securitate

- **SQL Injection:** toate interogările folosesc `PreparedStatement` cu parametri poziționali (`?`). Niciodată concatenare de string-uri în SQL. Există `SqlInjectionTest` care verifică rezistența la injecție.
- **Credențiale:** aplicația desktop deține credențialele JDBC direct (nu există server middle-tier). Acesta este un compromis acceptabil pentru o aplicație desktop de proiect universitar.
- **Hash parole:** parolele utilizatorilor sunt stocate cu bcrypt (gestionat în proceduri stocate).
- **Validare input:** `ValidationUtil` oferă validări comune (email, coordonate, intervale numerice).

---

## 9. Deployment

### 9.1 Docker

- `docker-compose.yml` definește două servicii:
  - `postgres`: `postgres:16-alpine`, port host `5433`, volum persistent `pgdata`.
  - `app`: build din `Dockerfile`, depinde de healthcheck-ul PostgreSQL, montează socket-ul X11 pentru GUI.
- `Dockerfile` multi-etapă:
  - Etapa 1: `maven:3.9-eclipse-temurin-21` — compilează și package cu `mvn clean package -DskipTests`.
  - Etapa 2: `eclipse-temurin:21-jre` — runtime minimal cu librării necesare pentru JavaFX headless/GUI.

### 9.2 Fat JAR

- `mvn clean package -DskipTests` produce `target/prognoza-meteo-1.0.0.jar` (shade plugin include toate dependențele).
- Rulare directă: `java -jar target/prognoza-meteo-1.0.0.jar`.

---

## 10. Flux Git

- **Branch-uri:** `feature/<nume>`, `fix/<nume>`, `docs/<nume>`.
- **Commit messages:** în engleză, modul imperativ (ex: `Add weekly forecast procedure`).
- **Commits:** mici și focalizate; fără commit direct pe `master`.

---

## 11. Scurtături și UX

| Combinație | Acțiune |
|------------|---------|
| `Ctrl+1` | Comută la tab-ul "Acum" |
| `Ctrl+2` | Comută la tab-ul "Hartă" |
| `Ctrl+3` | Comută la tab-ul "Mai multe" |

---

## 12. Fișiere de referință

- `README.md` — prezentare generală pentru oameni (în română).
- `ARCHITECTURE.md` — documentație exhaustivă a schemei bazei de date, procedurilor stocate, views, trigger-e, indecși și mapării DAO (în română).
- `RAPORT.md` — raport academic al proiectului.
- `NORMALIZARE.md` — detalii despre normalizarea bazei de date.
- `PLAN.md` — planul de dezvoltare inițial.
