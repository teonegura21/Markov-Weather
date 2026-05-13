# Arhitectură Aplicație — Prognoză Meteo (Proiect SGBD)

## Cuprins

1. [Prezentare Generală](#1-prezentare-generală)
2. [Schema Bazei de Date](#2-schema-bazei-de-date)
   - [2.1 Diagrama Entitate-Relație (textuală)](#21-diagrama-entitate-relație-textuală)
   - [2.2 Tabele — Definiții Complete](#22-tabele--definiții-complete)
   - [2.3 Indecși](#23-indecși)
   - [2.4 Constrângeri și Triggers](#24-constrângeri-și-triggers)
   - [2.5 Funcții Ajutătoare](#25-funcții-ajutătoare)
3. [Proceduri Stocate](#3-proceduri-stocate)
   - [3.1 Gestiune Utilizatori și Autentificare](#31-gestiune-utilizatori-și-autentificare)
   - [3.2 Generare Date (Populare Bază de Date)](#32-generare-date-populare-bază-de-date)
   - [3.3 Interogare Prognoze](#33-interogare-prognoze)
   - [3.4 Avertizări Meteo](#34-avertizări-meteo)
   - [3.5 Statistici și Comparații](#35-statistici-și-comparații)
   - [3.6 Detectare Anomalii](#36-detectare-anomalii)
   - [3.7 Clasamente și Similarități](#37-clasamente-și-similarități)
   - [3.8 Predicții Meteo](#38-predicții-meteo)
   - [3.9 Voturi și Comentarii](#39-voturi-și-comentarii)
   - [3.10 Reputație Utilizatori](#310-reputație-utilizatori)
   - [3.11 Rapoarte](#311-rapoarte)
   - [3.12 Mentenanță](#312-mentenanță)
4. [Vizualizări (Views)](#4-vizualizări-views)
5. [Strategia de Generare a Datelor](#5-strategia-de-generare-a-datelor)
6. [Arhitectura Aplicației JavaFX](#6-arhitectura-aplicației-javafx)
   - [6.1 Structură Pachete și Clase](#61-structură-pachete-și-clase)
   - [6.2 Maparea DAO — Proceduri Stocate](#62-maparea-dao--proceduri-stocate)
   - [6.3 Fluxuri Principale în UI](#63-fluxuri-principale-în-ui)
7. [Structura Directoarelor](#7-structura-directoarelor)

---

## 1. Prezentare Generală

```
┌──────────────────────────────────────────────────────────────┐
│                    JavaFX Desktop Client                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────┐ │
│  │   Views   │  │Controllers│  │ Services │  │     DAOs     │ │
│  │  (FXML)   │──│  (Java)   │──│  (Java)  │──│   (JDBC)     │─┼───┐
│  └──────────┘  └──────────┘  └──────────┘  └──────────────┘ │   │
└──────────────────────────────────────────────────────────────┘   │
                                                                   │
                            JDBC (CallableStatement)               │
                                                                   │
┌──────────────────────────────────────────────────────────────────┼─┐
│                    PostgreSQL Server                             │ │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────┐  │ │
│  │   Tables    │  │   Views    │  │ Procedures  │  │Functions │◄─┘ │
│  │ (16 tabele)│  │ (14 views) │  │(64 proceduri)│  │ (6 func) │    │
│  └────────────┘  └────────────┘  └────────────┘  └──────────┘    │
│  ┌────────────┐  ┌────────────┐                                   │
│  │  Triggers   │  │   Indexes  │                                   │
│  │ (6 triggeri)│  │ (18 indecși)│                                  │
│  └────────────┘  └────────────┘                                   │
└───────────────────────────────────────────────────────────────────┘
```

Toată logica de business (statistici, comparații, clasamente, anomalii, predicții, reputație)
rulează pe serverul PostgreSQL prin proceduri stocate. Clientul JavaFX se conectează
direct prin JDBC și doar afișează rezultatele. **Nu există strat de API REST.**

---

## 2. Schema Bazei de Date

### 2.1 Diagrama Entitate-Relație (textuală)

```
countries ──< cities ──< forecasts >── icon_types
                │            │  │
                │            │  └── uv_levels
                │            │  │
                │            │  └── warnings
                │            │  │
                │            │  └── anomaly_events
                │            │  │
                │            ├── votes ──< users
                │            │
                │            ├── comments ──< users
                │            │    │
                │            │    └── comments (self-referencing)
                │            │
                │            ├── forecast_history
                │            ├── seasonal_statistics
                │            └── city_similarity_cache
                │
                └── city_climate_profiles

users ──< user_notifications
users ──< user_reputation_history
```

### 2.2 Tabele — Definiții Complete

---

#### Tabela `countries`

| Coloană     | Tip            | Constrângeri                        | Observații                          |
|-------------|----------------|-------------------------------------|-------------------------------------|
| id          | SERIAL         | PRIMARY KEY                         |                                     |
| name        | VARCHAR(100)   | NOT NULL, UNIQUE                    | Numele țării în română              |
| code        | VARCHAR(3)     | NOT NULL, UNIQUE                    | Cod ISO 3166-1 alpha-3 (ex: ROU)   |
| continent   | VARCHAR(30)    | NOT NULL                            | Ex: Europa, Asia, America de Nord   |
| created_at  | TIMESTAMP      | NOT NULL DEFAULT CURRENT_TIMESTAMP  |                                     |

---

#### Tabela `cities`

| Coloană     | Tip            | Constrângeri                        | Observații                          |
|-------------|----------------|-------------------------------------|-------------------------------------|
| id          | SERIAL         | PRIMARY KEY                         |                                     |
| name        | VARCHAR(200)   | NOT NULL                            | Numele orașului                     |
| country_id  | INT            | NOT NULL, FK → countries(id)        |                                     |
| latitude    | DECIMAL(8,5)   | NOT NULL                            | Între -90 și 90                     |
| longitude   | DECIMAL(8,5)   | NOT NULL                            | Între -180 și 180                   |
| population  | INT            | NULL                                | Populație (pt. filtrare orașe mari) |
| timezone    | VARCHAR(50)    | NULL                                | Ex: Europe/Bucharest                |
| is_major    | BOOLEAN        | NOT NULL DEFAULT FALSE              | Oraș important (afișat pe hartă)    |
| created_at  | TIMESTAMP      | NOT NULL DEFAULT CURRENT_TIMESTAMP  |                                     |

Constrângeri suplimentare:
- `UNIQUE(name, country_id)`
- `CHECK(latitude BETWEEN -90 AND 90)`
- `CHECK(longitude BETWEEN -180 AND 180)`

---

#### Tabela `icon_types`

Stochează tipurile de pictograme meteo posibile.

| Coloană          | Tip           | Constrângeri                        | Observații                          |
|------------------|---------------|-------------------------------------|-------------------------------------|
| id               | SERIAL        | PRIMARY KEY                         |                                     |
| code             | VARCHAR(30)   | NOT NULL, UNIQUE                    | Identificator: sunny, cloudy, rain  |
| name_ro          | VARCHAR(100)  | NOT NULL                            | Nume în română: "Însorit"           |
| description_ro   | TEXT          | NULL                                | Descriere detaliată                 |
| icon_file_name   | VARCHAR(100)  | NULL                                | Nume fișier imagine (client-side)   |

Valori predefinite (seed): `sunny`, `partly_cloudy`, `cloudy`, `overcast`, `rain_light`,
`rain_heavy`, `thunderstorm`, `snow_light`, `snow_heavy`, `fog`, `windy`, `hail`, `sleet`.

---

#### Tabela `uv_levels`

Stochează nivelurile de radiație UV.

| Coloană          | Tip           | Constrângeri                        | Observații                          |
|------------------|---------------|-------------------------------------|-------------------------------------|
| id               | SERIAL        | PRIMARY KEY                         |                                     |
| name             | VARCHAR(30)   | NOT NULL, UNIQUE                    | scazut, moderat, ridicat, f_ridicat |
| min_value        | DECIMAL(3,1)  | NOT NULL                            | Prag inferior (inclusiv)            |
| max_value        | DECIMAL(3,1)  | NOT NULL                            | Prag superior (exclusiv)            |
| description_ro   | TEXT          | NULL                                | Recomandări de protecție            |
| color_code       | VARCHAR(7)    | NULL                                | Cod hex culoare (ex: #4CAF50)      |

Valori predefinite:
| name         | min_value | max_value |
|--------------|-----------|-----------|
| scazut       | 0.0       | 3.0       |
| moderat      | 3.0       | 6.0       |
| ridicat      | 6.0       | 8.0       |
| foarte_ridicat | 8.0     | 11.0      |
| extrem       | 11.0      | 99.0      |

---

#### Tabela `warnings`

Template-uri de avertizări meteo. Pot fi atribuite manual sau generate automat.

| Coloană                 | Tip           | Constrângeri                        | Observații                          |
|-------------------------|---------------|-------------------------------------|-------------------------------------|
| id                      | SERIAL        | PRIMARY KEY                         |                                     |
| code                    | VARCHAR(30)   | NOT NULL, UNIQUE                    | storm, heatwave, flood, etc.        |
| severity                | VARCHAR(20)   | NOT NULL                            | galben, portocaliu, rosu            |
| title_ro                | VARCHAR(200)  | NOT NULL                            | Titlu avertizare în română          |
| description_ro          | TEXT          | NOT NULL                            | Descriere detaliată                 |
| recommendations_ro      | TEXT          | NULL                                | Recomandări: "Luați umbrela", etc.  |
| auto_generation_rule    | TEXT          | NULL                                | Regulă JSON pt. generare automată   |
| keywords                | TEXT          | NULL                                | Cuvinte-cheie (separate prin virgulă)|

Exemplu regulă JSON în `auto_generation_rule`:
```json
{
  "conditions": "ALL",
  "rules": [
    {"field": "wind_speed", "operator": ">=", "value": 80},
    {"field": "humidity", "operator": ">=", "value": 85}
  ]
}
```

---

#### Tabela `users`

| Coloană           | Tip            | Constrângeri                        | Observații                          |
|-------------------|----------------|-------------------------------------|-------------------------------------|
| id                | SERIAL         | PRIMARY KEY                         |                                     |
| username          | VARCHAR(50)    | NOT NULL, UNIQUE                    |                                     |
| email             | VARCHAR(150)   | NOT NULL, UNIQUE                    |                                     |
| password_hash     | VARCHAR(255)   | NOT NULL                            | Hash criptat (bcrypt)               |
| full_name         | VARCHAR(150)   | NULL                                | Nume complet                        |
| reputation_score  | DECIMAL(5,2)   | NOT NULL DEFAULT 0.00               | Scor calculat automat               |
| total_votes_given | INT            | NOT NULL DEFAULT 0                  |                                     |
| accurate_votes    | INT            | NOT NULL DEFAULT 0                  | Voturi în consens cu majoritatea    |
| total_comments    | INT            | NOT NULL DEFAULT 0                  |                                     |
| role              | VARCHAR(20)    | NOT NULL DEFAULT 'user'             | user, moderator, admin              |
| is_active         | BOOLEAN        | NOT NULL DEFAULT TRUE               |                                     |
| last_login        | TIMESTAMP      | NULL                                |                                     |
| created_at        | TIMESTAMP      | NOT NULL DEFAULT CURRENT_TIMESTAMP  |                                     |

Constrângere: `CHECK(reputation_score >= 0.00 AND reputation_score <= 100.00)`

---

#### Tabela `forecasts`

Tabela centrală — câte un rând per oraș per zi.

| Coloană                     | Tip           | Constrângeri                        | Observații                          |
|-----------------------------|---------------|-------------------------------------|-------------------------------------|
| id                          | BIGSERIAL     | PRIMARY KEY                         |                                     |
| city_id                     | INT           | NOT NULL, FK → cities(id)           |                                     |
| forecast_date               | DATE          | NOT NULL                            | Data prognozei                      |
| temp_min                    | DECIMAL(4,1)  | NOT NULL                            | Temperatura minimă (°C)             |
| temp_max                    | DECIMAL(4,1)  | NOT NULL                            | Temperatura maximă (°C)             |
| temp_feels_like             | DECIMAL(4,1)  | NULL                                | Temperatura resimțită               |
| wind_speed                  | DECIMAL(5,2)  | NOT NULL                            | Viteza vântului (km/h)              |
| wind_direction              | VARCHAR(3)    | NULL                                | N, NE, E, SE, S, SW, W, NW          |
| humidity                    | DECIMAL(4,1)  | NOT NULL                            | Umiditate relativă (%) 0-100        |
| precipitation_probability   | DECIMAL(4,1)  | NULL                                | Probabilitate precipitații (%)      |
| precipitation_amount        | DECIMAL(5,1)  | NULL                                | Cantitate precipitații (mm)         |
| pressure                    | DECIMAL(6,1)  | NULL                                | Presiune atmosferică (hPa)          |
| visibility                  | DECIMAL(6,0)  | NULL                                | Vizibilitate (metri)                |
| uv_index                    | DECIMAL(3,1)  | NOT NULL                            | Valoare numerică UV                 |
| uv_level_id                 | INT           | NULL, FK → uv_levels(id)            | Completat prin trigger              |
| icon_type_id                | INT           | NOT NULL, FK → icon_types(id)       | Pictograma prognozei                |
| warning_id                  | INT           | NULL, FK → warnings(id)             | Avertizare (NULL dacă nu există)    |
| warning_generated           | BOOLEAN       | NOT NULL DEFAULT FALSE              | TRUE dacă avertizarea e auto-generată|
| is_severe                   | BOOLEAN       | NOT NULL DEFAULT FALSE              | Fenomen meteo sever                 |
| general_description_ro      | TEXT          | NULL                                | Descriere textuală generală         |
| sunrise                     | TIME          | NULL                                | Ora răsăritului                     |
| sunset                      | TIME          | NULL                                | Ora apusului                        |
| generated_by                | VARCHAR(20)   | NOT NULL DEFAULT 'auto'             | auto / manual                        |
| created_at                  | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP  |                                     |
| updated_at                  | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP  |                                     |

Constrângeri suplimentare:
- `UNIQUE(city_id, forecast_date)`
- `CHECK(temp_min <= temp_max)`
- `CHECK(humidity BETWEEN 0 AND 100)`
- `CHECK(precipitation_probability BETWEEN 0 AND 100)`
- `CHECK(wind_speed >= 0)`
- `CHECK(uv_index >= 0)`

---

#### Tabela `votes`

Un utilizator poate vota o singură dată o prognoză.

| Coloană      | Tip           | Constrângeri                         | Observații                          |
|--------------|---------------|--------------------------------------|-------------------------------------|
| id           | SERIAL        | PRIMARY KEY                          |                                     |
| user_id      | INT           | NOT NULL, FK → users(id)             |                                     |
| forecast_id  | BIGINT        | NOT NULL, FK → forecasts(id)         |                                     |
| is_accurate  | BOOLEAN       | NOT NULL                             | TRUE = prognoza corectă             |
| vote_type    | VARCHAR(20)   | NOT NULL DEFAULT 'accuracy'          | accuracy, severity, temperature     |
| comment      | TEXT          | NULL                                 | Justificare scurtă a votului        |
| created_at   | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

Constrângeri suplimentare:
- `UNIQUE(user_id, forecast_id)` — un vot per utilizator per prognoză
- `CHECK(vote_type IN ('accuracy', 'severity', 'temperature', 'wind', 'general'))`

---

#### Tabela `comments`

Comentarii la prognoze, cu suport pentru răspunsuri (threading).

| Coloană            | Tip           | Constrângeri                         | Observații                          |
|--------------------|---------------|--------------------------------------|-------------------------------------|
| id                 | SERIAL        | PRIMARY KEY                          |                                     |
| user_id            | INT           | NOT NULL, FK → users(id)             |                                     |
| forecast_id        | BIGINT        | NOT NULL, FK → forecasts(id)         |                                     |
| parent_comment_id  | INT           | NULL, FK → comments(id)              | NULL = comentariu principal         |
| content            | TEXT          | NOT NULL                             | Conținutul comentariului            |
| is_edited          | BOOLEAN       | NOT NULL DEFAULT FALSE               |                                     |
| is_deleted         | BOOLEAN       | NOT NULL DEFAULT FALSE               | Soft delete                         |
| created_at         | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |
| updated_at         | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

---

#### Tabela `forecast_history`

Valori agregate lunare per oraș. Completată periodic sau prin trigger.

| Coloană                 | Tip           | Constrângeri                         | Observații                          |
|-------------------------|---------------|--------------------------------------|-------------------------------------|
| id                      | SERIAL        | PRIMARY KEY                          |                                     |
| city_id                 | INT           | NOT NULL, FK → cities(id)            |                                     |
| year                    | INT           | NOT NULL                             |                                     |
| month                   | INT           | NOT NULL                             | 1–12                                |
| avg_temp_min            | DECIMAL(4,1)  | NULL                                 |                                     |
| avg_temp_max            | DECIMAL(4,1)  | NULL                                 |                                     |
| avg_temp_avg            | DECIMAL(4,1)  | NULL                                 | Media (min+max)/2 per zi, apoi avg  |
| avg_wind_speed          | DECIMAL(5,2)  | NULL                                 |                                     |
| avg_humidity            | DECIMAL(4,1)  | NULL                                 |                                     |
| avg_uv_index            | DECIMAL(3,1)  | NULL                                 |                                     |
| total_precipitation     | DECIMAL(7,1)  | NULL                                 | Suma precipitațiilor                |
| warning_days            | INT           | NOT NULL DEFAULT 0                   | Zile cu avertizare                  |
| extreme_events_count    | INT           | NOT NULL DEFAULT 0                   | Zile cu fenomene extreme            |
| dominant_icon_type_id   | INT           | NULL, FK → icon_types(id)            | Cea mai frecventă pictogramă        |
| computed_at             | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

Constrângere: `UNIQUE(city_id, year, month)`, `CHECK(month BETWEEN 1 AND 12)`

---

#### Tabela `seasonal_statistics`

Valori agregate pe sezoane. Completată de `sp_compute_seasonal_statistics`.

| Coloană                 | Tip           | Constrângeri                         | Observații                          |
|-------------------------|---------------|--------------------------------------|-------------------------------------|
| id                      | SERIAL        | PRIMARY KEY                          |                                     |
| city_id                 | INT           | NOT NULL, FK → cities(id)            |                                     |
| season                  | VARCHAR(10)   | NOT NULL                             | primavara, vara, toamna, iarna      |
| year                    | INT           | NOT NULL                             | Anul de referință                   |
| avg_temp_min            | DECIMAL(4,1)  | NULL                                 |                                     |
| avg_temp_max            | DECIMAL(4,1)  | NULL                                 |                                     |
| avg_temp_avg            | DECIMAL(4,1)  | NULL                                 |                                     |
| avg_wind_speed          | DECIMAL(5,2)  | NULL                                 |                                     |
| avg_humidity            | DECIMAL(4,1)  | NULL                                 |                                     |
| avg_uv_index            | DECIMAL(3,1)  | NULL                                 |                                     |
| total_precipitation     | DECIMAL(7,1)  | NULL                                 |                                     |
| extreme_events_count    | INT           | NOT NULL DEFAULT 0                   |                                     |
| dominant_icon_type_id   | INT           | NULL, FK → icon_types(id)            |                                     |
| computed_at             | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

Constrângere: `UNIQUE(city_id, season, year)`, `CHECK(season IN ('primavara','vara','toamna','iarna'))`

---

#### Tabela `anomaly_events`

Evenimente meteo anormale detectate automat.

| Coloană          | Tip           | Constrângeri                         | Observații                          |
|------------------|---------------|--------------------------------------|-------------------------------------|
| id               | SERIAL        | PRIMARY KEY                          |                                     |
| forecast_id      | BIGINT        | NOT NULL, FK → forecasts(id)         |                                     |
| anomaly_type     | VARCHAR(30)   | NOT NULL                             | temp_extreme, wind_extreme, etc.    |
| severity         | VARCHAR(20)   | NOT NULL DEFAULT 'medie'             | scazuta, medie, ridicata, critica   |
| description_ro   | TEXT          | NULL                                 | Descriere generată automat          |
| deviation_temp   | DECIMAL(5,2)  | NULL                                 | Abaterea temperaturii               |
| deviation_wind   | DECIMAL(5,2)  | NULL                                 | Abaterea vântului                   |
| deviation_humidity| DECIMAL(5,2) | NULL                                 | Abaterea umidității                 |
| deviation_uv     | DECIMAL(5,2)  | NULL                                 | Abaterea UV                         |
| expected_temp_min| DECIMAL(4,1)  | NULL                                 | Valoarea așteptată                  |
| expected_temp_max| DECIMAL(4,1)  | NULL                                 | Valoarea așteptată                  |
| detected_at      | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

Constrângere: `CHECK(anomaly_type IN ('temp_extreme','wind_extreme','humidity_extreme','uv_extreme','combined_extreme','precipitation_extreme'))`

---

#### Tabela `city_similarity_cache`

Cache pentru perechi de orașe similare. Completat de `sp_compute_city_similarities`.

| Coloană           | Tip           | Constrângeri                         | Observații                          |
|-------------------|---------------|--------------------------------------|-------------------------------------|
| id                | SERIAL        | PRIMARY KEY                          |                                     |
| city_id_1         | INT           | NOT NULL, FK → cities(id)            |                                     |
| city_id_2         | INT           | NOT NULL, FK → cities(id)            |                                     |
| similarity_score  | DECIMAL(6,5)  | NOT NULL                             | 0.0 = deloc similar, 1.0 = identic |
| period_start      | DATE          | NOT NULL                             |                                     |
| period_end        | DATE          | NOT NULL                             |                                     |
| metrics_used      | TEXT          | NULL                                 | JSON: ce metrici s-au folosit       |
| computed_at       | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

Constrângeri: `UNIQUE(city_id_1, city_id_2, period_start, period_end)`, `CHECK(city_id_1 < city_id_2)`, `CHECK(similarity_score BETWEEN 0 AND 1)`

---

#### Tabela `user_notifications`

Notificări pentru utilizatori (avertizări, răspunsuri la comentarii, anomalii).

| Coloană             | Tip           | Constrângeri                         | Observații                          |
|---------------------|---------------|--------------------------------------|-------------------------------------|
| id                  | SERIAL        | PRIMARY KEY                          |                                     |
| user_id             | INT           | NOT NULL, FK → users(id)             |                                     |
| forecast_id         | BIGINT        | NULL, FK → forecasts(id)             |                                     |
| notification_type   | VARCHAR(30)   | NOT NULL                             | warning, anomaly, comment_reply     |
| title_ro            | VARCHAR(200)  | NOT NULL                             | Titlu notificare                    |
| message_ro          | TEXT          | NOT NULL                             | Conținut notificare                 |
| is_read             | BOOLEAN       | NOT NULL DEFAULT FALSE               |                                     |
| created_at          | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

---

#### Tabela `user_reputation_history`

Istoricul modificărilor de reputație (audit trail).

| Coloană          | Tip           | Constrângeri                         | Observații                          |
|------------------|---------------|--------------------------------------|-------------------------------------|
| id               | SERIAL        | PRIMARY KEY                          |                                     |
| user_id          | INT           | NOT NULL, FK → users(id)             |                                     |
| old_score        | DECIMAL(5,2)  | NOT NULL                             | Scorul anterior                     |
| new_score        | DECIMAL(5,2)  | NOT NULL                             | Scorul nou                          |
| change_reason    | VARCHAR(50)   | NOT NULL                             | vote_cast, vote_updated, recalc     |
| related_vote_id  | INT           | NULL, FK → votes(id)                 |                                     |
| changed_at       | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

---

#### Tabela `city_climate_profiles`

Profil climatic per oraș — parametri pentru generarea realistă a prognozelor.

| Coloană               | Tip           | Constrângeri                         | Observații                          |
|-----------------------|---------------|--------------------------------------|-------------------------------------|
| id                    | SERIAL        | PRIMARY KEY                          |                                     |
| city_id               | INT           | NOT NULL, UNIQUE, FK → cities(id)    |                                     |
| climate_zone          | VARCHAR(30)   | NOT NULL                             | mediteranean, continental, oceanic  |
| avg_temp_january      | DECIMAL(4,1)  | NOT NULL                             | Temperatura medie în ianuarie       |
| avg_temp_july         | DECIMAL(4,1)  | NOT NULL                             | Temperatura medie în iulie          |
| temp_std_dev          | DECIMAL(3,1)  | NOT NULL DEFAULT 3.0                 | Deviația standard a temperaturii    |
| avg_humidity          | DECIMAL(4,1)  | NOT NULL                             | Umiditatea medie anuală             |
| humidity_std_dev      | DECIMAL(3,1)  | NOT NULL DEFAULT 5.0                 |                                     |
| avg_wind_speed        | DECIMAL(5,2)  | NOT NULL                             | Viteza medie a vântului             |
| wind_std_dev          | DECIMAL(3,1)  | NOT NULL DEFAULT 4.0                 |                                     |
| avg_uv_index          | DECIMAL(3,1)  | NOT NULL                             | Indice UV mediu                     |
| rainy_season_start    | INT           | NULL                                 | Luna de început (1-12)             |
| rainy_season_end      | INT           | NULL                                 | Luna de sfârșit (1-12)             |
| daily_temp_variation  | DECIMAL(3,1)  | NOT NULL DEFAULT 8.0                 | Diferența tipică min-max zilnică   |
| created_at            | TIMESTAMP     | NOT NULL DEFAULT CURRENT_TIMESTAMP   |                                     |

---

### 2.3 Indecși

```sql
-- forecasts: cel mai accesat tabel
CREATE INDEX idx_forecasts_city_id          ON forecasts(city_id);
CREATE INDEX idx_forecasts_forecast_date    ON forecasts(forecast_date);
CREATE INDEX idx_forecasts_warning_id       ON forecasts(warning_id) WHERE warning_id IS NOT NULL;
CREATE INDEX idx_forecasts_icon_type_id     ON forecasts(icon_type_id);
CREATE INDEX idx_forecasts_city_date_temp   ON forecasts(city_id, forecast_date, temp_max, temp_min);

-- votes
CREATE INDEX idx_votes_user_id              ON votes(user_id);
CREATE INDEX idx_votes_forecast_id          ON votes(forecast_id);

-- comments
CREATE INDEX idx_comments_forecast_id       ON comments(forecast_id) WHERE is_deleted = FALSE;
CREATE INDEX idx_comments_user_id           ON comments(user_id);
CREATE INDEX idx_comments_parent_id         ON comments(parent_comment_id);

-- cities
CREATE INDEX idx_cities_country_id          ON cities(country_id);
CREATE INDEX idx_cities_name                ON cities(name);

-- anomaly_events
CREATE INDEX idx_anomaly_events_forecast_id ON anomaly_events(forecast_id);
CREATE INDEX idx_anomaly_events_type        ON anomaly_events(anomaly_type);

-- seasonal / history
CREATE INDEX idx_seasonal_stats_city_season_year ON seasonal_statistics(city_id, season, year);
CREATE INDEX idx_forecast_history_city_year_month ON forecast_history(city_id, year, month);

-- notifications
CREATE INDEX idx_user_notifications_user_read ON user_notifications(user_id, is_read);

-- similarity
CREATE INDEX idx_city_similarity_cache_cities ON city_similarity_cache(city_id_1, city_id_2);

-- various lookups
CREATE INDEX idx_countries_continent         ON countries(continent);
CREATE INDEX idx_users_username              ON users(username);
CREATE INDEX idx_users_email                 ON users(email);
```

### 2.4 Constrângeri și Triggers

#### Triggeri

| Nume                                  | Tabelă      | Moment        | Eveniment  | Acțiune                                                                                     |
|---------------------------------------|-------------|---------------|------------|---------------------------------------------------------------------------------------------|
| `trg_forecasts_before_insert`         | forecasts   | BEFORE        | INSERT     | Calculează `uv_level_id` din `uv_index`; auto-atribuie `warning_id` dacă se depășesc praguri |
| `trg_forecasts_before_update`         | forecasts   | BEFORE        | UPDATE     | Recalculează `uv_level_id` și `warning_id` dacă s-au modificat datele meteo                  |
| `trg_forecasts_after_insert_anomaly`  | forecasts   | AFTER         | INSERT     | Verifică anomalii față de media istorică și inserează în `anomaly_events` dacă e cazul       |
| `trg_votes_after_insert`              | votes       | AFTER         | INSERT     | Apelează `sp_calculate_user_reputation`; actualizează `total_votes_given` pe `users`         |
| `trg_votes_after_update`              | votes       | AFTER         | UPDATE     | Recalculează reputația utilizatorului                                                        |
| `trg_votes_after_delete`              | votes       | AFTER         | DELETE     | Recalculează reputația utilizatorului                                                        |
| `trg_comments_after_insert`           | comments    | AFTER         | INSERT     | Incrementează `total_comments` pe `users`; creează notificare dacă e răspuns (reply)         |
| `trg_users_before_update_reputation`  | users       | BEFORE        | UPDATE     | Dacă `reputation_score` se modifică, înregistrează în `user_reputation_history`              |

#### Detaliu triggeri principali

**`trg_forecasts_before_insert` / `trg_forecasts_before_update`**

```
1. Determină uv_level_id:
   SELECT id INTO NEW.uv_level_id FROM uv_levels
   WHERE NEW.uv_index >= min_value AND NEW.uv_index < max_value
   ORDER BY min_value LIMIT 1;

2. Determină warning_id (dacă nu e deja setat manual):
   Verifică fiecare înregistrare din warnings unde auto_generation_rule IS NOT NULL.
   Parsează JSON-ul și evaluează condițiile pe câmpurile NEW (wind_speed, humidity,
   temp_max, temp_min, precipitation_probability).
   Dacă toate condițiile sunt îndeplinite, setează NEW.warning_id și NEW.warning_generated = TRUE.
   Dacă sunt mai multe warning-uri potrivite, alege-l pe cel cu severity maxim (rosu > portocaliu > galben).

3. Setează NEW.is_severe = TRUE dacă severity-ul warning-ului este 'rosu'.
4. Setează NEW.updated_at = CURRENT_TIMESTAMP (doar la UPDATE).
```

**`trg_forecasts_after_insert_anomaly`**

```
1. Calculează media istorică pe ±7 zile în jurul forecast_date, din anii anteriori:
   SELECT AVG(temp_max), AVG(temp_min), AVG(wind_speed), AVG(humidity), AVG(uv_index)
   FROM forecasts
   WHERE city_id = NEW.city_id
     AND forecast_date BETWEEN (NEW.forecast_date - INTERVAL '7 days' - INTERVAL '1 year')
                           AND (NEW.forecast_date + INTERVAL '7 days' - INTERVAL '1 year');

2. Pentru fiecare metrică, calculează abaterea în deviații standard.
3. Dacă |abaterea| > 2.5 sigma → inserează în anomaly_events cu tipul corespunzător.
4. Dacă mai multe metrici depășesc 2.0 sigma simultan → inserează anomaly_type = 'combined_extreme'.
```

### 2.5 Funcții Ajutătoare

| Funcție                                    | Returnează | Descriere                                                               |
|--------------------------------------------|------------|-------------------------------------------------------------------------|
| `fn_get_season(p_date DATE)`               | VARCHAR    | Returnează sezonul: primavara, vara, toamna, iarna                      |
| `fn_get_daily_temp_curve(p_day_of_year INT, p_avg_jan DECIMAL, p_avg_jul DECIMAL, p_variation DECIMAL)` | DECIMAL | Calculează temperatura așteptată într-o zi pe baza curbei sinusoidale anuale |
| `fn_cosine_similarity(p_vector1 DECIMAL[], p_vector2 DECIMAL[])` | DECIMAL | Calculează similaritatea cosinus între doi vectori de metrici meteo |
| `fn_euclidean_distance(p_vector1 DECIMAL[], p_vector2 DECIMAL[])` | DECIMAL | Distanța euclidiană între doi vectori |
| `fn_calculate_reputation(p_total_votes INT, p_accurate_votes INT, p_total_comments INT, p_account_age_days INT)` | DECIMAL | Formulă de scor reputație |
| `fn_json_condition_eval(p_rule_json TEXT, p_values JSONB)` | BOOLEAN | Evaluează o regulă JSON pe un set de valori (pentru warning-uri automate) |

---

## 3. Proceduri Stocate

Toate procedurile rulează pe serverul PostgreSQL. Clientul JavaFX le invocă prin `CallableStatement`.
Convenție: parametrii de ieșire de tip `REFCURSOR` pentru seturi de rezultate, `RETURNS TABLE(...)` în definiție.

### 3.1 Gestiune Utilizatori și Autentificare

---

#### `sp_register_user`
```
Înregistrează un utilizator nou.
IN:  p_username      VARCHAR(50)
     p_email         VARCHAR(150)
     p_password_hash VARCHAR(255)
     p_full_name     VARCHAR(150)
OUT: p_user_id       INT
EXCEPTIONS: username_exists, email_exists
```

#### `sp_authenticate_user`
```
Autentifică un utilizator. Returnează datele utilizatorului.
IN:  p_username      VARCHAR(50)
     p_password_hash VARCHAR(255)
RETURNS TABLE(
    user_id         INT,
    username        VARCHAR,
    email           VARCHAR,
    full_name       VARCHAR,
    reputation_score DECIMAL,
    role            VARCHAR,
    last_login      TIMESTAMP
)
Efect secundar: actualizează last_login.
```

#### `sp_update_user_profile`
```
Actualizează datele personale.
IN:  p_user_id    INT
     p_email      VARCHAR(150)
     p_full_name  VARCHAR(150)
OUT: p_success    BOOLEAN
```

#### `sp_get_user_profile`
```
Obține profilul unui utilizator.
IN:  p_user_id    INT
RETURNS TABLE(
    user_id         INT,
    username        VARCHAR,
    email           VARCHAR,
    full_name       VARCHAR,
    reputation_score DECIMAL,
    total_votes     INT,
    accurate_votes  INT,
    total_comments  INT,
    role            VARCHAR,
    joined_date     TIMESTAMP
)
```

#### `sp_get_user_activity_summary`
```
Rezumat activitate utilizator.
IN:  p_user_id    INT
RETURNS TABLE(
    total_votes_given       BIGINT,
    total_comments_written  BIGINT,
    last_vote_date          TIMESTAMP,
    last_comment_date       TIMESTAMP,
    account_age_days        INT,
    reputation_trend        VARCHAR   -- 'crescator', 'descrescator', 'stabil'
)
```

#### `sp_deactivate_user`
```
Dezactivează un cont de utilizator.
IN:  p_user_id    INT
OUT: p_success    BOOLEAN
```

---

### 3.2 Generare Date (Populare Bază de Date)

---

#### `sp_generate_forecast_for_day`
```
Generează o singură prognoză pentru un oraș și o dată.
Folosește city_climate_profiles + funcții de generare.
IN:  p_city_id     INT
     p_date        DATE
OUT: p_forecast_id BIGINT
```

#### `sp_generate_forecasts_for_city`
```
Generează prognoze pentru un oraș, pentru toate zilele dintr-un an.
IN:  p_city_id     INT
     p_year        INT
OUT: p_count       INT    -- numărul de prognoze generate
```

#### `sp_generate_forecasts_for_country`
```
Generează prognoze pentru toate orașele dintr-o țară, pentru un an.
IN:  p_country_id  INT
     p_year        INT
OUT: p_count       INT
```

#### `sp_generate_forecasts_for_all_cities`
```
Generează prognoze pentru toate orașele din BD, pentru un an.
IN:  p_year        INT
OUT: p_total_count INT
```

#### `sp_regenerate_forecast`
```
Regenerează o singură prognoză existentă (șterge vechea prognoză și o creează din nou).
IN:  p_forecast_id BIGINT
OUT: p_new_id      BIGINT
```

#### `sp_bulk_generate_forecasts`
```
Generează prognoze pentru mai multe orașe și un interval de date.
IN:  p_city_ids    INT[]          -- array de city_id
     p_start_date  DATE
     p_end_date    DATE
OUT: p_count       INT
```

---

### 3.3 Interogare Prognoze

---

#### `sp_get_daily_forecast`
```
Prognoza completă pentru un oraș într-o anumită zi.
IN:  p_city_id     INT
     p_date        DATE
RETURNS TABLE(
    forecast_id           BIGINT,
    city_id               INT,
    city_name             VARCHAR,
    country_id            INT,
    country_name          VARCHAR,
    country_code          VARCHAR,
    continent             VARCHAR,
    forecast_date         DATE,
    temp_min              DECIMAL,
    temp_max              DECIMAL,
    temp_feels_like       DECIMAL,
    wind_speed            DECIMAL,
    wind_direction        VARCHAR,
    humidity              DECIMAL,
    precipitation_prob    DECIMAL,
    precipitation_amount  DECIMAL,
    pressure              DECIMAL,
    visibility            DECIMAL,
    uv_index              DECIMAL,
    uv_level_name         VARCHAR,
    icon_code             VARCHAR,
    icon_name_ro          VARCHAR,
    icon_file_name        VARCHAR,
    warning_id            INT,
    warning_code          VARCHAR,
    warning_severity      VARCHAR,
    warning_title         VARCHAR,
    warning_description   TEXT,
    warning_recommendations TEXT,
    is_severe             BOOLEAN,
    general_description   TEXT,
    sunrise               TIME,
    sunset                TIME,
    sunrise_minute_diff   INT,       -- diferența față de ziua anterioară
    sunset_minute_diff    INT
)
```

#### `sp_get_weekly_forecast`
```
Prognoza pe 7 zile pentru un oraș.
IN:  p_city_id     INT
     p_start_date  DATE
RETURNS TABLE(aceleași coloane ca sp_get_daily_forecast, 7 rânduri)
```

#### `sp_get_10day_forecast`
```
Prognoza pe 10 zile pentru un oraș.
IN:  p_city_id     INT
     p_start_date  DATE
RETURNS TABLE(aceleași coloane ca sp_get_daily_forecast, 10 rânduri)
```

#### `sp_get_forecasts_by_date_range`
```
Prognoze pentru un oraș într-un interval de date.
IN:  p_city_id     INT
     p_start_date  DATE
     p_end_date    DATE
RETURNS TABLE(aceleași coloane ca sp_get_daily_forecast)
```

#### `sp_get_forecasts_by_country`
```
Prognozele pentru toate orașele principale dintr-o țară, într-o anumită zi.
IN:  p_country_id  INT
     p_date        DATE
     p_major_only  BOOLEAN DEFAULT TRUE   -- doar orașele marcate is_major
RETURNS TABLE(aceleași coloane ca sp_get_daily_forecast)
```

#### `sp_get_forecast_by_id`
```
Prognoza completă după ID.
IN:  p_forecast_id BIGINT
RETURNS TABLE(aceleași coloane ca sp_get_daily_forecast)
```

#### `sp_search_forecasts`
```
Căutare avansată de prognoze după multiple criterii.
IN:  p_city_name_pattern     VARCHAR DEFAULT NULL
     p_country_name_pattern  VARCHAR DEFAULT NULL
     p_start_date            DATE DEFAULT NULL
     p_end_date              DATE DEFAULT NULL
     p_min_temp              DECIMAL DEFAULT NULL
     p_max_temp              DECIMAL DEFAULT NULL
     p_icon_code             VARCHAR DEFAULT NULL
     p_has_warning           BOOLEAN DEFAULT NULL
     p_is_severe             BOOLEAN DEFAULT NULL
     p_limit                 INT DEFAULT 100
     p_offset                INT DEFAULT 0
RETURNS TABLE(aceleași coloane ca sp_get_daily_forecast)
```

#### `sp_get_latest_forecasts`
```
Cele mai recente prognoze (după data curentă) pentru un oraș.
IN:  p_city_id     INT
     p_days        INT DEFAULT 30
RETURNS TABLE(aceleași coloane ca sp_get_daily_forecast)
```

---

### 3.4 Avertizări Meteo

---

#### `sp_generate_warning_for_forecast`
```
Evaluează regulile de avertizare pentru o prognoză și atribuie un warning dacă e cazul.
IN:  p_forecast_id BIGINT
OUT: p_warning_id  INT       -- NULL dacă nu se aplică nici o avertizare
```

#### `sp_generate_warnings_batch`
```
Generează avertizări pentru toate prognozele dintr-un interval.
IN:  p_start_date  DATE
     p_end_date    DATE
OUT: p_count       INT       -- numărul de warning-uri nou-atribuite
```

#### `sp_get_active_warnings`
```
Toate avertizările active pentru o dată.
IN:  p_date        DATE
RETURNS TABLE(
    forecast_id          BIGINT,
    city_id              INT,
    city_name            VARCHAR,
    country_name         VARCHAR,
    latitude             DECIMAL,
    longitude            DECIMAL,
    warning_id           INT,
    warning_code         VARCHAR,
    warning_severity     VARCHAR,
    warning_title        VARCHAR,
    warning_description  TEXT,
    warning_recommendations TEXT,
    temp_min             DECIMAL,
    temp_max             DECIMAL,
    wind_speed           DECIMAL,
    humidity             DECIMAL
)
```

#### `sp_get_city_warnings_history`
```
Istoricul avertizărilor pentru un oraș într-un an.
IN:  p_city_id     INT
     p_year        INT
RETURNS TABLE(
    forecast_date   DATE,
    warning_code    VARCHAR,
    warning_severity VARCHAR,
    warning_title   VARCHAR,
    warning_description TEXT
)
```

#### `sp_get_warning_statistics`
```
Statistici agregate pe tipuri de avertizare.
IN:  p_start_date  DATE
     p_end_date    DATE
     p_country_id  INT DEFAULT NULL
RETURNS TABLE(
    warning_code    VARCHAR,
    severity        VARCHAR,
    count           BIGINT,
    affected_cities BIGINT,
    affected_countries BIGINT
)
```

#### `sp_get_warnings_by_keyword`
```
Caută avertizări după cuvinte-cheie în descriere sau recomandări.
IN:  p_keyword     VARCHAR
     p_start_date  DATE DEFAULT NULL
     p_end_date    DATE DEFAULT NULL
RETURNS TABLE(like sp_get_active_warnings)
```

---

### 3.5 Statistici și Comparații

---

#### `sp_compare_forecast_to_seasonal_avg`
```
Compară prognoza curentă cu media sezonieră.
IN:  p_city_id     INT
     p_date        DATE
RETURNS TABLE(
    metric             VARCHAR,   -- temp_min, temp_max, temp_avg, wind, humidity, uv, precip
    current_value      DECIMAL,
    seasonal_avg       DECIMAL,
    absolute_diff      DECIMAL,
    percent_diff       DECIMAL,
    trend_indicator    VARCHAR    -- peste_medie, sub_medie, normal
)
```

#### `sp_compare_forecast_to_historical_same_day`
```
Compară prognoza curentă cu media aceleiași zile din anii anteriori.
IN:  p_city_id     INT
     p_date        DATE
RETURNS TABLE(aceleași coloane ca sp_compare_forecast_to_seasonal_avg)
```

#### `sp_compare_forecast_to_monthly_avg`
```
Compară prognoza curentă cu media lunară.
IN:  p_city_id     INT
     p_year        INT
     p_month       INT
RETURNS TABLE(aceleași coloane ca sp_compare_forecast_to_seasonal_avg)
```

#### `sp_get_city_monthly_stats`
```
Statistici agregate pentru un oraș într-o lună.
IN:  p_city_id     INT
     p_year        INT
     p_month       INT
RETURNS TABLE(
    avg_temp_min       DECIMAL,
    avg_temp_max       DECIMAL,
    avg_temp_avg       DECIMAL,
    min_temp_abs       DECIMAL,
    max_temp_abs       DECIMAL,
    avg_wind           DECIMAL,
    max_wind           DECIMAL,
    avg_humidity       DECIMAL,
    avg_uv             DECIMAL,
    total_precip       DECIMAL,
    precip_days        INT,
    warning_days       INT,
    extreme_days       INT,
    sunny_days         INT,
    cloudy_days        INT,
    rainy_days         INT,
    snowy_days         INT,
    dominant_icon_code VARCHAR,
    dominant_icon_name VARCHAR
)
```

#### `sp_get_city_yearly_stats`
```
Statistici lunare pentru un oraș într-un an (12 rânduri).
IN:  p_city_id     INT
     p_year        INT
RETURNS TABLE(
    month              INT,
    avg_temp_min       DECIMAL,
    avg_temp_max       DECIMAL,
    avg_temp_avg       DECIMAL,
    avg_wind           DECIMAL,
    avg_humidity       DECIMAL,
    avg_uv             DECIMAL,
    total_precip       DECIMAL,
    precip_days        INT,
    warning_days       INT,
    extreme_days       INT,
    sunny_days         INT,
    cloudy_days        INT,
    rainy_days         INT,
    snowy_days         INT,
    dominant_icon_code VARCHAR
)
```

#### `sp_get_country_daily_summary`
```
Rezumat zilnic pentru toate orașele principale dintr-o țară.
IN:  p_country_id  INT
     p_date        DATE
RETURNS TABLE(
    city_id          INT,
    city_name        VARCHAR,
    temp_min         DECIMAL,
    temp_max         DECIMAL,
    temp_avg         DECIMAL,
    wind_speed       DECIMAL,
    humidity         DECIMAL,
    uv_index         DECIMAL,
    icon_code        VARCHAR,
    icon_name        VARCHAR,
    has_warning      BOOLEAN,
    warning_severity VARCHAR
)
```

#### `sp_get_temperature_trend`
```
Evoluția temperaturii pentru un oraș într-un interval, cu medie mobilă pe 7 zile.
IN:  p_city_id     INT
     p_start_date  DATE
     p_end_date    DATE
RETURNS TABLE(
    forecast_date    DATE,
    temp_min         DECIMAL,
    temp_max         DECIMAL,
    temp_avg         DECIMAL,
    moving_avg_7day  DECIMAL,
    moving_avg_30day DECIMAL,
    anomaly_flag     BOOLEAN
)
```

#### `sp_get_seasonal_stats`
```
Statistici agregate pe sezoane pentru un oraș într-un an.
IN:  p_city_id     INT
     p_year        INT
RETURNS TABLE(
    season             VARCHAR,
    avg_temp_min       DECIMAL,
    avg_temp_max       DECIMAL,
    avg_temp_avg       DECIMAL,
    avg_wind           DECIMAL,
    avg_humidity       DECIMAL,
    avg_uv             DECIMAL,
    total_precip       DECIMAL,
    precip_days        INT,
    extreme_days       INT,
    dominant_icon_code VARCHAR,
    dominant_icon_name VARCHAR
)
```

#### `sp_compare_two_cities`
```
Compară statisticile a două orașe într-o perioadă.
IN:  p_city_id_1   INT
     p_city_id_2   INT
     p_start_date  DATE
     p_end_date    DATE
RETURNS TABLE(
    metric           VARCHAR,
    city_1_name      VARCHAR,
    city_1_value     DECIMAL,
    city_2_name      VARCHAR,
    city_2_value     DECIMAL,
    difference       DECIMAL
)
```

---

### 3.6 Detectare Anomalii

---

#### `sp_detect_anomalies`
```
Detectează anomalii pentru toate prognozele dintr-un interval.
Compară fiecare prognoză cu media istorică (±zile, ani anteriori).
IN:  p_start_date       DATE
     p_end_date         DATE
     p_threshold_sigma  DECIMAL DEFAULT 2.5   -- pragul în deviații standard
     p_country_id       INT DEFAULT NULL
OUT: p_anomalies_found  INT
RETURNS TABLE(
    forecast_id      BIGINT,
    city_id          INT,
    city_name        VARCHAR,
    country_name     VARCHAR,
    forecast_date    DATE,
    anomaly_type     VARCHAR,
    severity         VARCHAR,
    description_ro   TEXT,
    temp_min         DECIMAL,
    temp_max         DECIMAL,
    expected_temp_min DECIMAL,
    expected_temp_max DECIMAL,
    deviation_temp   DECIMAL,
    deviation_wind   DECIMAL,
    deviation_humidity DECIMAL,
    deviation_uv     DECIMAL
)
```

#### `sp_detect_city_anomalies`
```
Detectare anomalii pentru un singur oraș.
IN:  p_city_id          INT
     p_start_date       DATE
     p_end_date         DATE
     p_threshold_sigma  DECIMAL DEFAULT 2.0
RETURNS TABLE(aceleași coloane ca sp_detect_anomalies)
```

#### `sp_detect_temperature_anomalies_specific`
```
Detectare anomalii doar de temperatură.
IN:  p_city_id          INT
     p_start_date       DATE
     p_end_date         DATE
     p_threshold_sigma  DECIMAL DEFAULT 2.0
RETURNS TABLE(aceleași coloane — filtrat doar anomaly_type = 'temp_extreme')
```

#### `sp_get_anomaly_summary`
```
Rezumatul anomaliilor detectate pentru un oraș într-un an.
IN:  p_city_id     INT
     p_year        INT
RETURNS TABLE(
    anomaly_type    VARCHAR,
    severity        VARCHAR,
    count           BIGINT,
    avg_deviation   DECIMAL,
    max_deviation   DECIMAL,
    earliest_date   DATE,
    latest_date     DATE
)
```

#### `sp_get_global_anomaly_summary`
```
Rezumatul anomaliilor la nivel global (toate orașele) pentru un an.
IN:  p_year        INT
RETURNS TABLE(
    city_name        VARCHAR,
    country_name     VARCHAR,
    total_anomalies  BIGINT,
    avg_deviation    DECIMAL,
    most_common_type VARCHAR,
    most_severe_date DATE
)
```

#### `sp_get_anomalies_for_notification`
```
Obține anomaliile recente care necesită notificare utilizatori.
IN:  p_hours_back  INT DEFAULT 24
RETURNS TABLE(like sp_detect_anomalies)
```

---

### 3.7 Clasamente și Similarități

---

#### `sp_rank_cities_by_temperature`
```
Clasamentul orașelor după temperatură (cea mai caldă / cea mai rece).
IN:  p_season      VARCHAR DEFAULT NULL   -- NULL = tot anul
     p_year        INT
     p_sort_metric VARCHAR DEFAULT 'avg_temp_max'  -- avg_temp_max, avg_temp_min, avg_temp_avg
     p_ascending   BOOLEAN DEFAULT FALSE  -- FALSE = descrescător (cele mai calde primele)
     p_limit       INT DEFAULT 20
RETURNS TABLE(
    rank_position    BIGINT,
    city_id          INT,
    city_name        VARCHAR,
    country_name     VARCHAR,
    continent        VARCHAR,
    avg_temp_min     DECIMAL,
    avg_temp_max     DECIMAL,
    avg_temp_avg     DECIMAL,
    avg_wind         DECIMAL,
    avg_humidity     DECIMAL,
    avg_uv           DECIMAL,
    extreme_days     INT,
    climate_zone     VARCHAR
)
```

#### `sp_rank_cities_by_extreme_weather`
```
Clasamentul orașelor după severitatea vremii (cele mai multe fenomene extreme).
IN:  p_year        INT
     p_limit       INT DEFAULT 20
RETURNS TABLE(
    rank_position    BIGINT,
    city_name        VARCHAR,
    country_name     VARCHAR,
    extreme_days     INT,
    warning_days     INT,
    anomaly_days     INT,
    extreme_score    DECIMAL,
    worst_temp_max   DECIMAL,
    worst_temp_min   DECIMAL,
    max_wind_speed   DECIMAL,
    max_humidity     DECIMAL
)
```

#### `sp_find_similar_cities`
```
Găsește orașele cele mai similare din punct de vedere meteo cu un oraș dat.
Folosește similaritatea cosinus pe vectorii de metrici (temp, vânt, umiditate, UV, precipitații).
IN:  p_city_id     INT
     p_start_date  DATE
     p_end_date    DATE
     p_limit       INT DEFAULT 10
RETURNS TABLE(
    rank_position     BIGINT,
    similar_city_id   INT,
    similar_city_name VARCHAR,
    similar_country_name VARCHAR,
    similarity_score  DECIMAL,
    avg_temp_diff     DECIMAL,
    avg_wind_diff     DECIMAL,
    avg_humidity_diff DECIMAL,
    climate_zone_match BOOLEAN
)
```

#### `sp_get_city_similarity_matrix`
```
Matricea de similaritate între toate orașele (sau filtrat pe o țară).
IN:  p_start_date  DATE
     p_end_date    DATE
     p_country_id  INT DEFAULT NULL
     p_min_score   DECIMAL DEFAULT 0.7
RETURNS TABLE(
    city_1_name         VARCHAR,
    city_2_name         VARCHAR,
    country_1_name      VARCHAR,
    country_2_name      VARCHAR,
    similarity_score    DECIMAL,
    period_start        DATE,
    period_end          DATE
)
```

#### `sp_compute_city_similarities`
```
Calculează și stochează similaritățile în city_similarity_cache.
IN:  p_start_date  DATE
     p_end_date    DATE
     p_country_id  INT DEFAULT NULL
OUT: p_pairs_computed INT
```

#### `sp_get_city_clusters`
```
Grupează orașele în clustere pe baza similarității meteo.
IN:  p_start_date  DATE
     p_end_date    DATE
     p_cluster_count INT DEFAULT 5
RETURNS TABLE(
    cluster_id       INT,
    city_name        VARCHAR,
    country_name     VARCHAR,
    cluster_label    VARCHAR,   -- eticheta generată (ex: "cald și umed")
    avg_temp         DECIMAL,
    avg_humidity     DECIMAL,
    dominant_icon    VARCHAR
)
```

#### `sp_get_seasonal_rankings`
```
Clasament sezonier al orașelor după o metrică aleasă.
IN:  p_year        INT
     p_season      VARCHAR
     p_metric      VARCHAR DEFAULT 'temperature'  -- temperature, wind, humidity, uv, precipitation
     p_limit       INT DEFAULT 20
RETURNS TABLE(like sp_rank_cities_by_temperature)
```

---

### 3.8 Predicții Meteo

---

#### `sp_predict_week`
```
Prezice prognoza pe 7 zile pe baza datelor istorice (aceeași perioadă, ani anteriori).
IN:  p_city_id     INT
     p_start_date  DATE
RETURNS TABLE(
    forecast_date      DATE,
    predicted_temp_min DECIMAL,
    predicted_temp_max DECIMAL,
    predicted_temp_avg DECIMAL,
    predicted_wind     DECIMAL,
    predicted_humidity DECIMAL,
    predicted_uv       DECIMAL,
    predicted_icon_code VARCHAR,
    predicted_icon_name VARCHAR,
    confidence_score   DECIMAL,        -- 0-100, cât de încredere e predicția
    sample_size        INT             -- câte date istorice s-au folosit
)
```

#### `sp_predict_10days`
```
Prezice prognoza pe 10 zile.
IN:  p_city_id     INT
     p_start_date  DATE
RETURNS TABLE(aceleași coloane ca sp_predict_week, 10 rânduri)
```

#### `sp_predict_by_weighted_average`
```
Predicție prin medie ponderată (ponderile descresc cu distanța în timp față de data țintă).
IN:  p_city_id     INT
     p_target_date DATE
RETURNS TABLE(aceleași coloane ca sp_predict_week, 1 rând)
```

#### `sp_compute_prediction_accuracy`
```
Compară predicțiile făcute în trecut cu prognozele reale generate ulterior.
IN:  p_city_id     INT
     p_year        INT
RETURNS TABLE(
    prediction_date    DATE,
    forecast_date      DATE,
    predicted_temp_max DECIMAL,
    actual_temp_max    DECIMAL,
    temp_error         DECIMAL,
    predicted_temp_min DECIMAL,
    actual_temp_min    DECIMAL,
    avg_mae            DECIMAL        -- Mean Absolute Error global
)
```

---

### 3.9 Voturi și Comentarii

---

#### `sp_cast_vote`
```
Înregistrează sau actualizează votul unui utilizator pe o prognoză.
IN:  p_user_id     INT
     p_forecast_id BIGINT
     p_is_accurate BOOLEAN
     p_vote_type   VARCHAR(20)
     p_comment     TEXT DEFAULT NULL
OUT: p_vote_id     INT
Efect: Dacă există deja un vot (user_id, forecast_id), face UPDATE.
       Altfel face INSERT. Triggerul de AFTER recalculează reputația.
```

#### `sp_update_vote`
```
Actualizează un vot existent.
IN:  p_vote_id      INT
     p_is_accurate  BOOLEAN
     p_vote_type    VARCHAR
     p_comment      TEXT
OUT: p_success      BOOLEAN
```

#### `sp_delete_vote`
```
Șterge un vot.
IN:  p_vote_id     INT
OUT: p_success     BOOLEAN
```

#### `sp_add_comment`
```
Adaugă un comentariu la o prognoză (sau răspuns la alt comentariu).
IN:  p_user_id          INT
     p_forecast_id      BIGINT
     p_parent_comment_id INT DEFAULT NULL
     p_content          TEXT
OUT: p_comment_id       INT
```

#### `sp_edit_comment`
```
Editează un comentariu existent.
IN:  p_comment_id  INT
     p_content     TEXT
OUT: p_success     BOOLEAN
```

#### `sp_delete_comment`
```
Șterge soft un comentariu (setează is_deleted = TRUE).
IN:  p_comment_id  INT
OUT: p_success     BOOLEAN
```

#### `sp_get_forecast_votes`
```
Toate voturile pentru o prognoză, cu detalii utilizator.
IN:  p_forecast_id BIGINT
RETURNS TABLE(
    vote_id         INT,
    user_id         INT,
    username        VARCHAR,
    user_reputation DECIMAL,
    is_accurate     BOOLEAN,
    vote_type       VARCHAR,
    comment         TEXT,
    voted_at        TIMESTAMP
)
```

#### `sp_get_forecast_vote_summary`
```
Rezumatul voturilor pentru o prognoză.
IN:  p_forecast_id BIGINT
RETURNS TABLE(
    total_votes      BIGINT,
    accurate_votes   BIGINT,
    inaccurate_votes BIGINT,
    accuracy_percent DECIMAL,
    weighted_score   DECIMAL,        -- ponderat cu reputația
    user_consensus   VARCHAR         -- 'acurat', 'inacurat', 'divizat'
)
```

#### `sp_get_forecast_comments`
```
Comentariile la o prognoză, cu structură arborescentă.
IN:  p_forecast_id    BIGINT
     p_limit          INT DEFAULT 50
     p_offset         INT DEFAULT 0
RETURNS TABLE(
    comment_id       INT,
    parent_comment_id INT,
    user_id          INT,
    username         VARCHAR,
    user_reputation  DECIMAL,
    content          TEXT,
    is_edited        BOOLEAN,
    created_at       TIMESTAMP,
    reply_count      INT              -- numărul de răspunsuri la acest comentariu
)
```

#### `sp_get_user_comments`
```
Toate comentariile unui utilizator.
IN:  p_user_id     INT
     p_limit       INT DEFAULT 50
     p_offset      INT DEFAULT 0
RETURNS TABLE(
    comment_id      INT,
    forecast_id     BIGINT,
    city_name       VARCHAR,
    forecast_date   DATE,
    content         TEXT,
    is_edited       BOOLEAN,
    created_at      TIMESTAMP,
    reply_count     INT
)
```

---

### 3.10 Reputație Utilizatori

---

#### `sp_calculate_user_reputation`
```
Calculează și actualizează reputația unui utilizator.
Formula: bazată pe acuratețea voturilor (față de consens), vechime cont, număr comentarii.
IN:  p_user_id     INT
OUT: p_new_score   DECIMAL
Efect: UPDATE users SET reputation_score = p_new_score WHERE id = p_user_id
```

#### `sp_calculate_all_reputations`
```
Recalculează reputația pentru toți utilizatorii.
OUT: p_users_updated INT
```

#### `sp_get_top_users`
```
Clasament utilizatori după reputație.
IN:  p_limit       INT DEFAULT 10
RETURNS TABLE(
    rank_position    BIGINT,
    user_id          INT,
    username         VARCHAR,
    reputation_score DECIMAL,
    total_votes      INT,
    accurate_votes   INT,
    total_comments   INT,
    account_age_days INT,
    last_active      TIMESTAMP
)
```

#### `sp_get_forecast_accuracy_score`
```
Scorul de acuratețe al unei prognoze pe baza voturilor ponderate.
IN:  p_forecast_id BIGINT
RETURNS TABLE(
    total_votes       BIGINT,
    accurate_votes    BIGINT,
    accuracy_percent  DECIMAL,
    weighted_score    DECIMAL,
    user_consensus    BOOLEAN,
    controversy_level VARCHAR     -- scazut, mediu, ridicat
)
```

---

### 3.11 Rapoarte

---

#### `sp_generate_city_annual_report`
```
Raport anual complet pentru un oraș (12 rânduri, defalcare lunară).
IN:  p_city_id     INT
     p_year        INT
RETURNS TABLE(
    month              INT,
    avg_temp_min       DECIMAL,
    avg_temp_max       DECIMAL,
    avg_temp_avg       DECIMAL,
    avg_wind           DECIMAL,
    avg_humidity       DECIMAL,
    avg_uv             DECIMAL,
    total_precip       DECIMAL,
    sunny_days         INT,
    cloudy_days        INT,
    rainy_days         INT,
    snowy_days         INT,
    stormy_days        INT,
    warning_days       INT,
    extreme_days       INT,
    anomaly_count      INT,
    most_common_icon   VARCHAR,
    temp_trend         VARCHAR     -- in crestere, in scadere, stabil
)
```

#### `sp_generate_country_annual_report`
```
Raport anual pentru toate orașele principale dintr-o țară.
IN:  p_country_id  INT
     p_year        INT
RETURNS TABLE(
    city_name          VARCHAR,
    avg_temp_min       DECIMAL,
    avg_temp_max       DECIMAL,
    avg_temp_avg       DECIMAL,
    total_precip       DECIMAL,
    extreme_days       INT,
    total_warnings     INT,
    dominant_climate   VARCHAR,
    anomaly_count      INT
)
```

#### `sp_generate_continent_report`
```
Raport comparativ pe continente și sezoane.
IN:  p_continent   VARCHAR
     p_year        INT
     p_season      VARCHAR DEFAULT NULL
RETURNS TABLE(
    country_name       VARCHAR,
    city_count         INT,
    avg_temp_min       DECIMAL,
    avg_temp_max       DECIMAL,
    avg_temp_avg       DECIMAL,
    avg_wind           DECIMAL,
    avg_humidity       DECIMAL,
    extreme_cities     TEXT,
    most_extreme_city  VARCHAR
)
```

#### `sp_generate_global_summary`
```
Rezumat meteo global pentru o zi.
IN:  p_date        DATE
RETURNS TABLE(
    total_forecasts    INT,
    avg_global_temp    DECIMAL,
    max_temp_city      VARCHAR,
    max_temp_country   VARCHAR,
    max_temp_value     DECIMAL,
    min_temp_city      VARCHAR,
    min_temp_country   VARCHAR,
    min_temp_value     DECIMAL,
    most_windy_city    VARCHAR,
    max_wind_value     DECIMAL,
    most_humid_city    VARCHAR,
    max_humidity_value DECIMAL,
    warning_cities     TEXT,
    extreme_weather_cities TEXT
)
```

#### `sp_generate_comparison_report`
```
Raport comparativ: an curent vs. an precedent vs. medie istorică.
IN:  p_city_id     INT
     p_year        INT
RETURNS TABLE(
    metric             VARCHAR,
    current_year_value DECIMAL,
    previous_year_value DECIMAL,
    historical_avg      DECIMAL,
    diff_vs_prev_year   DECIMAL,
    diff_vs_historical  DECIMAL
)
```

---

### 3.12 Mentenanță

---

#### `sp_refresh_materialized_stats`
```
Actualizează tabelele de statistici agregate (forecast_history, seasonal_statistics).
IN:  p_year INT DEFAULT NULL  -- NULL = anul curent
OUT: p_updated INT
```

#### `sp_archive_old_forecasts`
```
Arhivează prognoze mai vechi de un anumit an (mută în tabele de arhivă).
IN:  p_cutoff_year  INT
OUT: p_archived_count INT
```

#### `sp_cleanup_old_notifications`
```
Șterge notificările citite mai vechi de N zile.
IN:  p_days INT DEFAULT 90
OUT: p_deleted_count INT
```

#### `sp_vacuum_analyze_tables`
```
Rulează VACUUM ANALYZE pe tabelele principale.
OUT: p_success BOOLEAN
```

#### `sp_get_database_stats`
```
Statistici despre volumul de date din BD.
RETURNS TABLE(
    table_name       VARCHAR,
    row_count        BIGINT,
    table_size_mb    DECIMAL,
    index_size_mb    DECIMAL,
    last_vacuum      TIMESTAMP,
    last_analyze     TIMESTAMP
)
```

---

## 4. Vizualizări (Views)

Toate view-urile sunt prefixate cu `v_`.

| Nume View                      | Bazată pe Tabelele                                    | Descriere                                                                 |
|--------------------------------|-------------------------------------------------------|---------------------------------------------------------------------------|
| `v_daily_forecast_full`        | forecasts + cities + countries + icon_types + uv_levels + warnings | Prognoză completă denormalizată (toate join-urile). View-ul principal consumat de client. |
| `v_forecast_accuracy`          | forecasts + votes + users                             | Scor ponderat de acuratețe per prognoză.                                  |
| `v_user_reputation_summary`    | users                                                 | Reputația utilizatorilor cu poziție în clasament (ROW_NUMBER).            |
| `v_seasonal_averages`          | seasonal_statistics + cities + countries              | Medii sezoniere per oraș, denormalizate.                                  |
| `v_monthly_averages`           | forecast_history + cities + countries                 | Medii lunare per oraș, denormalizate.                                     |
| `v_active_warnings`            | forecasts + cities + countries + warnings             | Avertizări active (forecast_date = CURRENT_DATE).                         |
| `v_anomaly_summary`            | anomaly_events + forecasts + cities + countries       | Anomalii recente cu denormalizare completă.                               |
| `v_city_rankings_temperature`  | seasonal_statistics + cities + countries              | Clasament temperatură cu RANK() — se actualizează automat.                |
| `v_city_rankings_extreme`      | forecast_history + cities + countries                 | Clasament după zile extreme.                                              |
| `v_city_similar_pairs`         | city_similarity_cache + cities                        | Cele mai similare perechi de orașe (similarity_score DESC).               |
| `v_forecast_icons_distribution`| forecasts + icon_types + cities                       | Distribuția tipurilor de pictograme per oraș și lună.                     |
| `v_user_activity_summary`      | users + votes + comments                              | Sumar activitate: voturi, comentarii, ultima activitate.                  |
| `v_weekly_trend`               | forecasts + cities                                    | Trend pe 7 zile cu funcții fereastră (LAG, AVG OVER).                     |
| `v_top_controversial_forecasts`| forecasts + v_forecast_accuracy + cities              | Prognozele cu cele mai divizate voturi (controversy_level = ridicat).     |

---

## 5. Strategia de Generare a Datelor

### 5.1 Sursa Datelor Inițiale

Baza de date se populează în doi pași:

1. **Date de referință** (seed manual):
   - `countries`: ~50 de țări (toate țările din Europa + câteva mari din restul lumii)
   - `cities`: ~3–10 orașe per țară (200–400 orașe total)
   - `city_climate_profiles`: un profil per oraș, cu valori reale aproximative
   - `icon_types`: 13 tipuri predefinite
   - `uv_levels`: 5 niveluri predefinite
   - `warnings`: 10–15 template-uri cu reguli JSON
   - `users`: 20–50 utilizatori de test

2. **Prognoze generate** (automat, prin proceduri):
   - Pentru fiecare oraș, se generează 365 de prognoze pe an
   - Se pot genera pentru 1–3 ani calendaristici

### 5.2 Algoritmul de Generare a unei Prognoze (`sp_generate_forecast_for_day`)

```
Intrare: city_id, data țintă

1. Citește city_climate_profiles pentru oraș.
2. Calculează day_of_year (1–365) din data țintă.
3. Temperatura de bază (expected_temp) se calculează cu o sinusoidă:
   expected_temp = avg_jan + (avg_jul - avg_jan) * (1 + sin(2π * (day_of_year - 80) / 365)) / 2
   (vârful verii ≈ ziua 172, vârful iernii ≈ ziua 355)
4. Adaugă zgomot Gaussian:
   temp_avg = expected_temp + random_normal(0, temp_std_dev)
5. Calculează temp_min și temp_max:
   temp_min = temp_avg - daily_temp_variation / 2 + random_normal(0, 1.5)
   temp_max = temp_avg + daily_temp_variation / 2 + random_normal(0, 1.5)
   Asigură temp_min <= temp_max.
6. Umiditatea:
   humidity = avg_humidity + random_normal(0, humidity_std_dev)
   Corelație inversă cu temperatura: humidity = humidity - 0.3 * (temp_avg - expected_temp)
   Limitează la [10, 100].
7. Viteza vântului:
   wind_speed = ABS(avg_wind_speed + random_normal(0, wind_std_dev))
   Probabilitate 5% de rafală (înmulțește cu factor 2–4).
8. Indice UV:
   Factor sezonier: mai mare în lunile de vară (aprilie–septembrie în emisfera nordică).
   uv_index = max(0, avg_uv_index * seasonal_factor + random_normal(0, 1.0))
   Limitează la [0, 15].
9. Probabilitatea de precipitații:
   Dacă luna curentă este în rainy_season [start, end], crește probabilitatea.
   precipitation_prob = base_prob + seasonal_rain_bonus
   Corelată cu umiditatea: dacă humidity > 70, crește probabilitatea.
10. Selectează icon_type_id pe baza regulilor:
    - Dacă precipitation_prob > 70 AND temp_avg < 2 → snow_heavy
    - Dacă precipitation_prob > 70 AND temp_avg >= 2 → rain_heavy
    - Dacă precipitation_prob > 40 AND temp_avg < 2 → snow_light
    - Dacă precipitation_prob > 40 AND temp_avg >= 2 → rain_light
    - Dacă wind_speed > 60 → windy
    - Dacă humidity > 90 AND visibility < 1000 → fog
    - Dacă precipitation_prob < 20 AND uv_index > 6 → sunny
    - Dacă precipitation_prob < 20 AND uv_index <= 6 → partly_cloudy
    - Altfel → cloudy / overcast
11. Calculează sunrise și sunset pe baza day_of_year și latitudine.
12. Generează descrierea textuală (general_description_ro) pe baza parametrilor.
13. INSERAREA în forecasts; triggerul BEFORE INSERT va seta uv_level_id și warning_id.
```

### 5.3 Generarea Datelor pe Mai Mulți Ani

Procedura `sp_generate_forecasts_for_city(p_city_id, p_year)` iterează prin toate
zilele anului și apelează `sp_generate_forecast_for_day` pentru fiecare. Pentru ani
diferiți, variația naturală vine din zgomotul Gaussian (seed diferit bazat pe an).

Pentru a popula rapid baza de date:
```sql
-- Generează pentru toate orașele, anii 2024, 2025, 2026
SELECT sp_generate_forecasts_for_all_cities(2024);
SELECT sp_generate_forecasts_for_all_cities(2025);
SELECT sp_generate_forecasts_for_all_cities(2026);
-- Reîmprospătează statisticile agregate
SELECT sp_refresh_materialized_stats(NULL);
-- Generează avertizări
SELECT sp_generate_warnings_batch('2024-01-01', '2026-12-31');
-- Detectează anomalii
SELECT sp_detect_anomalies('2024-01-01', '2026-12-31', 2.5);
```

---

## 6. Arhitectura Aplicației JavaFX

### 6.1 Structură Pachete și Clase

```
com.sgbd.weatherforecast
│
├── Main.java                          // Entry point: lansează JavaFX, încarcă scena principală
│
├── config/
│   ├── DatabaseConfig.java            // Încarcă datele de conexiune din database.properties
│   └── ConnectionPool.java            // Pool de conexiuni JDBC (HikariCP sau simplu)
│
├── model/                             // POJO-uri — mapare 1:1 pe tabele/views
│   ├── Country.java
│   ├── City.java
│   ├── CityClimateProfile.java
│   ├── Forecast.java                  // Câmpurile din v_daily_forecast_full
│   ├── ForecastDetail.java            // Versiunea extinsă cu toate join-urile
│   ├── IconType.java
│   ├── UvLevel.java
│   ├── Warning.java
│   ├── User.java
│   ├── UserReputation.java
│   ├── Vote.java
│   ├── VoteSummary.java
│   ├── Comment.java
│   ├── AnomalyEvent.java
│   ├── CityRanking.java               // O poziție în clasament
│   ├── CitySimilarity.java            // Pereche de orașe similare
│   ├── ForecastComparison.java        // Rezultat comparație
│   ├── SeasonalStats.java
│   ├── MonthlyStats.java
│   ├── Prediction.java                // O predicție pe o zi
│   ├── Notification.java
│   └── Report.java                    // Date agregate pentru rapoarte
│
├── dao/                               // Data Access Objects — fiecare metodă = CallableStatement
│   ├── CountryDAO.java                // getById, getAll, getByContinent
│   ├── CityDAO.java                   // getById, getByCountry, searchByName, getAllMajor
│   ├── ClimateProfileDAO.java         // getByCityId
│   ├── ForecastDAO.java               // getDaily, getWeekly, get10Day, getByDateRange,
│   │                                   //   getByCountry, search, getById, getLatest
│   ├── WarningDAO.java                // getActive, getHistory, getStatistics, getByKeyword
│   ├── UserDAO.java                   // register, authenticate, getProfile, updateProfile,
│   │                                   //   deactivate, getActivitySummary
│   ├── VoteDAO.java                   // castVote, updateVote, deleteVote,
│   │                                   //   getForecastVotes, getForecastVoteSummary
│   ├── CommentDAO.java                // add, edit, delete, getByForecast, getByUser
│   ├── StatisticsDAO.java             // compareToSeasonal, compareToHistorical,
│   │                                   //   compareToMonthly, getMonthlyStats, getYearlyStats,
│   │                                   //   getCountryDailySummary, getTemperatureTrend,
│   │                                   //   getSeasonalStats, compareTwoCities
│   ├── AnomalyDAO.java                // detectAnomalies, detectCityAnomalies,
│   │                                   //   getAnomalySummary, getGlobalAnomalySummary
│   ├── RankingDAO.java                // rankByTemperature, rankByExtreme, findSimilarCities,
│   │                                   //   getSimilarityMatrix, getCityClusters, getSeasonalRankings
│   ├── PredictionDAO.java             // predictWeek, predict10Days, predictWeightedAvg,
│   │                                   //   computePredictionAccuracy
│   ├── ReputationDAO.java             // calculateReputation, calculateAll, getTopUsers,
│   │                                   //   getForecastAccuracyScore
│   ├── ReportDAO.java                 // generateCityAnnual, generateCountryAnnual,
│   │                                   //   generateContinent, generateGlobalSummary,
│   │                                   //   generateComparisonReport
│   ├── DataGenerationDAO.java         // generateForDay, generateForCity, generateForAllCities,
│   │                                   //   regenerateForecast, bulkGenerate
│   ├── NotificationDAO.java           // getUnread, markAsRead, cleanupOld
│   └── MaintenanceDAO.java            // refreshStats, archiveOld, cleanupNotifications,
│                                       //   vacuumAnalyze, getDatabaseStats
│
├── service/                           // Strat de business logic (subțire — orchestrează DAO-uri)
│   ├── AuthenticationService.java     // login, register, session management
│   ├── ForecastService.java           // obține prognoze, caching simplu
│   ├── WarningService.java            // logica de avertizări
│   ├── StatisticsService.java         // orchestrare statistici și comparații
│   ├── AnomalyService.java            // orchestrare detectare anomalii
│   ├── RankingService.java            // orchestrare clasamente și similarități
│   ├── PredictionService.java         // orchestrare predicții
│   ├── VotingService.java             // orchestrare voturi și comentarii
│   ├── ReputationService.java         // orchestrare reputație
│   ├── ReportService.java             // orchestrare rapoarte
│   ├── DataGenerationService.java     // orchestrare generare date
│   └── NotificationService.java       // orchestrare notificări
│
├── ui/
│   ├── controllers/                   // Controlori JavaFX (câte unul per ecran/ferestru)
│   │   ├── MainController.java        // Controlor principal: navigation, sidebar, content area
│   │   ├── LoginController.java       // Fereastra de login
│   │   ├── RegisterController.java    // Fereastra de înregistrare
│   │   ├── DashboardController.java   // Dashboard cu sumar zilnic, hartă, statistici rapide
│   │   ├── ForecastListController.java // Listă prognoze (săptămână/10 zile) pentru un oraș
│   │   ├── ForecastDetailController.java // Detaliu prognoză + voturi + comentarii
│   │   ├── CitySearchController.java  // Căutare oraș și afișare prognoză
│   │   ├── MapViewController.java     // Hartă (JavaFX WebView cu Leaflet/OpenLayers sau canvas)
│   │   ├── ComparisonController.java  // Comparații (sezoniere, istorice, între orașe)
│   │   ├── StatisticsController.java  // Grafice statistici (lunare, anuale, trend)
│   │   ├── AnomalyController.java     // Listă anomalii + filtre
│   │   ├── RankingController.java     // Clasamente orașe (temperatură, extreme)
│   │   ├── SimilarityController.java  // Orașe similare + clustere
│   │   ├── PredictionController.java  // Predicții pe 7/10 zile
│   │   ├── ReportsController.java     // Rapoarte anuale, globale
│   │   ├── UserProfileController.java // Profil utilizator + istoric activitate
│   │   ├── AdminPanelController.java  // Panou admin: gestionare utilizatori, generare date
│   │   └── SettingsController.java    // Setări aplicație
│   │
│   ├── components/                    // Componente reutilizabile JavaFX
│   │   ├── ForecastCard.java          // Card care afișează o prognoză (icon, temp, descriere)
│   │   ├── ForecastCardSmall.java     // Varianta compactă (pentru liste)
│   │   ├── WeatherIcon.java           // Încarcă și afișează pictograma meteo din resources/icons/
│   │   ├── WarningBanner.java         // Banner de avertizare (galben/portocaliu/roșu)
│   │   ├── CitySearchBox.java         // Autocomplete căutare oraș
│   │   ├── DateNavigator.java         // Navigator calendar (săptămâni/luni)
│   │   ├── ChartPanel.java            // Wrapper peste charts (LineChart, BarChart, PieChart)
│   │   ├── TemperatureChart.java      // Grafic temperatură min/max pe o perioadă
│   │   ├── VoteWidget.java            // Widget vot (butoane acuratețe + comentariu)
│   │   ├── CommentSection.java        // Listă comentarii cu reply-uri (structură arbore)
│   │   ├── ReputationBadge.java       // Insignă reputație utilizator
│   │   ├── LoadingSpinner.java        // Indicator de loading pentru operații lungi
│   │   ├── AnomalyMarker.java         // Marcaj vizual pentru anomalii pe grafice
│   │   └── NotificationPopup.java     // Popup notificări
│   │
│   └── util/                          // Utilitare UI
│       ├── IconMapper.java            // Mapare icon_code → cale fișier imagine
│       ├── ColorMapper.java           // Mapare severity/temperature → coduri culoare
│       ├── FXMLoader.java             // Încărcare fișiere FXML cu controller injectat
│       └── AlertHelper.java           // Dialoguri de eroare/informare standard
│
└── util/                              // Utilitare generale
    ├── DateUtils.java                 // Conversii date, calcul zi din an, sezon
    ├── StringUtils.java               // Sanitizare input, validare
    ├── PasswordHasher.java            // Hash parole (bcrypt)
    ├── SessionManager.java            // Gestiune sesiune utilizator curent
    └── Constants.java                 // Constante: sezoane, praguri, dimensiuni
```

### 6.2 Maparea DAO — Proceduri Stocate

Fiecare metodă din DAO:
1. Obține o conexiune din `ConnectionPool`
2. Pregătește un `CallableStatement` cu sintaxa `{call sp_nume(?, ?, ...)}`
3. Setează parametrii de intrare
4. Înregistrează parametrii de ieșire (inclusiv REFCURSOR pentru `RETURNS TABLE`)
5. Execută și mapează `ResultSet` → obiecte model (POJO)
6. Închide resursele în `finally`

Exemplu de mapping (conceptual):

| DAO Method                              | Stored Procedure Called                |
|-----------------------------------------|----------------------------------------|
| `ForecastDAO.getDaily(cityId, date)`    | `sp_get_daily_forecast(?, ?)`          |
| `ForecastDAO.getWeekly(cityId, date)`   | `sp_get_weekly_forecast(?, ?)`         |
| `ForecastDAO.search(criteria)`          | `sp_search_forecasts(?, ..., ?)`       |
| `VoteDAO.castVote(...)`                 | `sp_cast_vote(?, ?, ?, ?, ?)`          |
| `StatisticsDAO.compareToHistorical(...)`| `sp_compare_forecast_to_historical_same_day(?, ?)` |
| `AnomalyDAO.detect(cityId, start, end)` | `sp_detect_city_anomalies(?, ?, ?, ?)` |
| `RankingDAO.getSimilar(cityId, ...)`    | `sp_find_similar_cities(?, ?, ?, ?)`   |
| `PredictionDAO.predictWeek(cityId, d)`  | `sp_predict_week(?, ?)`                |

### 6.3 Fluxuri Principale în UI

#### Flux 1: Vizualizare Prognoză Zilnică
```
Utilizator selectează oraș din CitySearchBox
  → ForecastListController.onCitySelected(cityId)
    → ForecastService.getWeeklyForecast(cityId, today)
      → ForecastDAO.getWeekly(cityId, today)
        → CallableStatement: {call sp_get_weekly_forecast(?, ?)}
    ← List<ForecastDetail>
  → Se populează ForecastCard[] în interfață
```

#### Flux 2: Comparație Sezonieră
```
Utilizatorul selectează un oraș și apasă "Compară cu sezonul"
  → ComparisonController.onCompareSeasonal(cityId, date)
    → StatisticsService.compareToSeasonal(cityId, date)
      → StatisticsDAO.compareToSeasonal(cityId, date)
        → {call sp_compare_forecast_to_seasonal_avg(?, ?)}
    ← List<ForecastComparison>
  → Se afișează într-un BarChart + tabel
```

#### Flux 3: Hartă cu Temperaturi
```
MapViewController.onLoad()
  → ForecastService.getCountryDailySummary(countryId, today)
    → ForecastDAO.getByCountry(countryId, today)
      → {call sp_get_forecasts_by_country(?, ?, ?)}
  ← List<ForecastDetail>
  → Pentru fiecare oraș, se plasează un marker pe hartă
    cu culoare în funcție de temperatură (albastru = rece, roșu = cald)
```

#### Flux 4: Clasamente
```
RankingController.onLoad(season, year, metric)
  → RankingService.getTemperatureRanking(season, year, metric)
    → RankingDAO.rankByTemperature(season, year, metric)
      → {call sp_rank_cities_by_temperature(?, ?, ?, ?, ?)}
  ← List<CityRanking>
  → Se populează un TableView sortabil
```

---

## 7. Structura Directoarelor

```
SGBD/
│
├── AGENTS.md                              // Instrucțiuni pentru asistenți AI
├── ARCHITECTURE.md                        // Acest document
├── README.md                              // Descriere proiect, cerințe sistem, setup
│
├── database/
│   ├── migrations/                        // Migrații numerotate incremental
│   │   ├── 001_create_countries.sql
│   │   ├── 002_create_cities.sql
│   │   ├── 003_create_icon_types.sql
│   │   ├── 004_create_uv_levels.sql
│   │   ├── 005_create_warnings.sql
│   │   ├── 006_create_users.sql
│   │   ├── 007_create_forecasts.sql       // + indecși + constrângeri
│   │   ├── 008_create_votes.sql
│   │   ├── 009_create_comments.sql
│   │   ├── 010_create_forecast_history.sql
│   │   ├── 011_create_seasonal_statistics.sql
│   │   ├── 012_create_anomaly_events.sql
│   │   ├── 013_create_user_notifications.sql
│   │   ├── 014_create_user_reputation_history.sql
│   │   ├── 015_create_city_similarity_cache.sql
│   │   ├── 016_create_city_climate_profiles.sql
│   │   └── 017_create_remaining_indexes.sql  // Indecși care nu sunt în CREATE TABLE
│   │
│   ├── triggers/                          // Triggeri (ordinea contează)
│   │   ├── 018_trg_forecasts_before_insert.sql
│   │   ├── 019_trg_forecasts_before_update.sql
│   │   ├── 020_trg_forecasts_after_insert_anomaly.sql
│   │   ├── 021_trg_votes_after_insert.sql
│   │   ├── 022_trg_votes_after_update.sql
│   │   ├── 023_trg_votes_after_delete.sql
│   │   ├── 024_trg_comments_after_insert.sql
│   │   └── 025_trg_users_before_update_reputation.sql
│   │
│   ├── functions/                         // Funcții SQL ajutătoare
│   │   ├── 026_fn_get_season.sql
│   │   ├── 027_fn_get_daily_temp_curve.sql
│   │   ├── 028_fn_cosine_similarity.sql
│   │   ├── 029_fn_euclidean_distance.sql
│   │   ├── 030_fn_calculate_reputation.sql
│   │   └── 031_fn_json_condition_eval.sql
│   │
│   ├── procedures/                        // Proceduri stocate, grupate pe domenii
│   │   ├── 032_sp_register_user.sql
│   │   ├── 033_sp_authenticate_user.sql
│   │   ├── 034_sp_users_crud.sql          // get/update/deactivate/getActivity
│   │   ├── 035_sp_generate_forecasts.sql  // toate procedurile de generare
│   │   ├── 036_sp_get_forecasts.sql       // toate procedurile de interogare
│   │   ├── 037_sp_warnings.sql            // generate/getActive/getHistory/getStats
│   │   ├── 038_sp_statistics.sql          // toate comparațiile și statisticile
│   │   ├── 039_sp_anomalies.sql           // detect/getSummary
│   │   ├── 040_sp_rankings.sql            // rank/findSimilar/computeSimilar/getClusters
│   │   ├── 041_sp_predictions.sql         // predictWeek/predict10Days/accuracy
│   │   ├── 042_sp_votes_comments.sql      // castVote/addComment/getVotes/getComments
│   │   ├── 043_sp_reputation.sql          // calculate/topUsers/accuracyScore
│   │   ├── 044_sp_reports.sql             // toate rapoartele
│   │   └── 045_sp_maintenance.sql         // refresh/archive/cleanup/vacuum
│   │
│   ├── views/                             // View-uri
│   │   ├── 046_v_daily_forecast_full.sql
│   │   ├── 047_v_forecast_accuracy.sql
│   │   ├── 048_v_user_reputation_summary.sql
│   │   ├── 049_v_seasonal_averages.sql
│   │   ├── 050_v_monthly_averages.sql
│   │   ├── 051_v_active_warnings.sql
│   │   ├── 052_v_anomaly_summary.sql
│   │   ├── 053_v_city_rankings_temperature.sql
│   │   ├── 054_v_city_rankings_extreme.sql
│   │   ├── 055_v_city_similar_pairs.sql
│   │   ├── 056_v_forecast_icons_distribution.sql
│   │   ├── 057_v_user_activity_summary.sql
│   │   ├── 058_v_weekly_trend.sql
│   │   └── 059_v_top_controversial_forecasts.sql
│   │
│   ├── seeds/                             // Date inițiale
│   │   ├── seed_countries.sql
│   │   ├── seed_cities.sql
│   │   ├── seed_city_climate_profiles.sql
│   │   ├── seed_icon_types.sql
│   │   ├── seed_uv_levels.sql
│   │   ├── seed_warnings.sql
│   │   └── seed_users.sql
│   │
│   └── run_all.sh                         // Script bash: psql -f migrations/* -f triggers/*
│       run_all.bat                        //   -f functions/* -f procedures/* -f views/* -f seeds/*
│
├── client/
│   ├── pom.xml                            // Maven (sau build.gradle pentru Gradle)
│   ├── .gitignore
│   │
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/
│           │       └── sgbd/
│           │           └── weatherforecast/
│           │               ├── Main.java
│           │               ├── config/
│           │               │   ├── DatabaseConfig.java
│           │               │   └── ConnectionPool.java
│           │               ├── model/
│           │               │   └── ... (toate POJO-urile)
│           │               ├── dao/
│           │               │   └── ... (toate DAO-urile)
│           │               ├── service/
│           │               │   └── ... (toate Service-urile)
│           │               ├── ui/
│           │               │   ├── controllers/
│           │               │   │   └── ... (toți controlorii)
│           │               │   ├── components/
│           │               │   │   └── ... (componentele reutilizabile)
│           │               │   └── util/
│           │               │       ├── IconMapper.java
│           │               │       ├── ColorMapper.java
│           │               │       ├── FXMLoader.java
│           │               │       └── AlertHelper.java
│           │               └── util/
│           │                   ├── DateUtils.java
│           │                   ├── StringUtils.java
│           │                   ├── PasswordHasher.java
│           │                   ├── SessionManager.java
│           │                   └── Constants.java
│           │
│           └── resources/
│               ├── fxml/                  // Fișiere FXML (câte unul per controller)
│               │   ├── main.fxml
│               │   ├── login.fxml
│               │   ├── register.fxml
│               │   ├── dashboard.fxml
│               │   ├── forecast_list.fxml
│               │   ├── forecast_detail.fxml
│               │   ├── city_search.fxml
│               │   ├── map_view.fxml
│               │   ├── comparison.fxml
│               │   ├── statistics.fxml
│               │   ├── anomaly.fxml
│               │   ├── ranking.fxml
│               │   ├── similarity.fxml
│               │   ├── prediction.fxml
│               │   ├── reports.fxml
│               │   ├── user_profile.fxml
│               │   ├── admin_panel.fxml
│               │   └── settings.fxml
│               │
│               ├── css/
│               │   ├── main.css           // Stiluri principale
│               │   ├── forecast_card.css
│               │   ├── map.css
│               │   └── charts.css
│               │
│               ├── icons/                 // Pictograme meteo (imagini PNG/SVG)
│               │   ├── sunny.png
│               │   ├── partly_cloudy.png
│               │   ├── cloudy.png
│               │   ├── overcast.png
│               │   ├── rain_light.png
│               │   ├── rain_heavy.png
│               │   ├── thunderstorm.png
│               │   ├── snow_light.png
│               │   ├── snow_heavy.png
│               │   ├── fog.png
│               │   ├── windy.png
│               │   ├── hail.png
│               │   └── sleet.png
│               │
│               ├── images/                // Alte imagini
│               │   ├── logo.png
│               │   ├── default_avatar.png
│               │   └── map_marker.png
│               │
│               └── config/
│                   └── database.properties // Host, port, db_name, user, password
│
├── docs/
│   ├── diagrams/
│   │   ├── er_diagram.png
│   │   ├── class_diagram.png
│   │   └── ui_wireframes.png
│   └── manual_de_utilizare.md
│
└── .gitignore
```

---

## Anexa A: Dependențe Java (Maven `pom.xml`)

| Grup                | Artifact              | Versiune | Scop              |
|---------------------|-----------------------|----------|-------------------|
| org.openjfx         | javafx-controls       | 21       | UI                |
| org.openjfx         | javafx-fxml           | 21       | FXML              |
| org.openjfx         | javafx-web            | 21       | WebView (hartă)   |
| org.postgresql      | postgresql            | 42.7     | Driver JDBC       |
| com.zaxxer          | HikariCP              | 5.1      | Connection Pool   |
| org.mindrot         | jbcrypt               | 0.4      | Hash parole       |
| com.google.code.gson| gson                  | 2.10     | Parsare JSON      |
| org.junit.jupiter   | junit-jupiter         | 5.10     | Testare           |
| org.testcontainers  | postgresql            | 1.19     | Teste integrare   |

---

## Anexa B: Formule de Calcul Relevante

**Reputație utilizator** (în `fn_calculate_reputation`):
```
reputation = 50
  + (accurate_ratio - 0.5) * 40                          // [-20, +20]: cât de des votează în consens
  + MIN(total_votes / 10, 20)                            // [0, +20]: bonus pentru activitate
  + MIN(total_comments / 5, 10)                          // [0, +10]: bonus pentru comentarii
  - MAX(0, (0.7 - accurate_ratio)) * 30                  // penalizare dacă acuratețea < 70%
Limitat la [0, 100]
```

**Similaritate orașe** (distanța cosinus):
```
similarity = 1 - cosine_distance(vector_A, vector_B)
unde vectorul = [avg_temp, temp_variance, avg_wind, avg_humidity, avg_uv, precip_freq]
toate normalizate la [0, 1]
```

**Predicție temperatură** (medie ponderată istorică):
```
predicted_temp(date) = Σ (w_i * historical_temp_i) / Σ w_i
unde w_i = 1 / (1 + year_diff_i)  -- ponderi mai mari pentru anii recenți
```

---

*Aprobat: Arhitectura este completă și gata pentru implementare.*
