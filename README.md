# Krankenhaus-Patientenverwaltung

Vollständiges Krankenhaus-Patientenverwaltungssystem auf Basis von **Jakarta EE 10**, **PostgreSQL** und einer modernen Single-Page-Webanwendung mit virtuellem Scrolling — ausgelegt für **10 Millionen+ Patienten**.

---

## Inhaltsverzeichnis

1. [Architektur](#architektur)
2. [Voraussetzungen](#voraussetzungen)
3. [Installation](#installation)
4. [Konfiguration](#konfiguration)
5. [Build & Deployment](#build--deployment)
6. [Testausführung](#testausführung)
7. [Generierung von 10 Millionen Patientendaten](#generierung-von-10-millionen-patientendaten)
8. [API-Referenz](#api-referenz)
9. [Projektstruktur](#projektstruktur)

---

## Architektur

```
Browser (index.html)
    │  virtuelles Scrolling, 300 ms Debouncing
    ▼
Jakarta EE REST API  (WildFly / Payara)
    │  JPA / Hibernate
    ▼
PostgreSQL
    │  GIN-Index (Volltext + Trigram)
    │  TABLESAMPLE BERNOULLI (Zufallsanzeige)
    ▼
10 Mio.+ Patienten
```

**Designprinzip der Benutzeroberfläche:**  
Die initiale Ansicht zeigt ~30 zufällige, **unsortierte** Patienten. Erst wenn der Benutzer in das Suchfeld tippt, entsteht Ordnung: Die Ergebnisse werden alphabetisch sortiert und vollständig (ohne LIMIT) geladen. Das virtuelle Scrolling rendert dabei nur die sichtbaren Zeilen im DOM.

---

## Voraussetzungen

| Komponente | Version |
|---|---|
| Java JDK | 17+ |
| Apache Maven | 3.9+ |
| PostgreSQL | 15+ |
| WildFly / Payara | 27+ / 6+ |
| Python (Datengenerierung) | 3.10+ |

---

## Installation

### 1. Repository klonen

```bash
git clone https://github.com/hjstephan86/hospital.git
cd hospital
```

### 2. PostgreSQL einrichten

```bash
# Datenbank anlegen
psql -U postgres -c "CREATE DATABASE hospital_db;"
psql -U postgres -c "CREATE USER hospital_user WITH PASSWORD 'geheimes_passwort';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE hospital_db TO hospital_user;"

# Schema, Indizes und Funktionen einspielen
psql -U hospital_user -d hospital_db -f schema.sql
```

Das `schema.sql` richtet folgendes ein:
- Tabellen: `patients`, `diagnosen`, `medikamente`
- GIN-Index für Volltextsuche (`pg_trgm`, `unaccent`)
- PostgreSQL-Funktion `random_patients(n)` via `TABLESAMPLE BERNOULLI`
- PostgreSQL-Funktion `search_patients(q)` mit Trigram-Fuzzy-Matching

### 3. WildFly DataSource konfigurieren

PostgreSQL JDBC-Treiber in WildFly installieren:

```bash
# Treiber herunterladen
wget https://jdbc.postgresql.org/download/postgresql-42.7.3.jar

# In WildFly-Modul installieren
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  --command="module add --name=org.postgresql \
  --resources=postgresql-42.7.3.jar \
  --dependencies=javax.api,javax.transaction.api"
```

DataSource in `standalone.xml` hinzufügen:

```xml
<datasource jndi-name="java:/PostgresDS" pool-name="PostgresDS">
    <connection-url>jdbc:postgresql://localhost:5432/hospital_db</connection-url>
    <driver>postgresql</driver>
    <pool>
        <min-pool-size>5</min-pool-size>
        <max-pool-size>25</max-pool-size>
    </pool>
    <security>
        <user-name>hospital_user</user-name>
        <password>geheimes_passwort</password>
    </security>
    <validation>
        <valid-connection-checker-class-name>
            org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker
        </valid-connection-checker-class-name>
    </validation>
</datasource>
```

---

## Konfiguration

| Datei | Zweck |
|---|---|
| `src/main/resources/META-INF/persistence.xml` | JPA-Konfiguration (Produktion) |
| `src/test/resources/META-INF/persistence.xml` | H2 In-Memory für Tests |
| `schema.sql` | Datenbankschema |

---

## Build & Deployment

```bash
# Projekt bauen
mvn clean package -DskipTests

# WAR deployen (WildFly muss laufen)
cp target/hospital-management.war $WILDFLY_HOME/standalone/deployments/

# Anwendung aufrufen
open http://localhost:8080/hospital-management/
```

---

## Testausführung

### Unit- und Integrationstests

```bash
# Alle Tests ausführen + JaCoCo-Report
mvn clean verify

# Nur Unit-Tests
mvn test -Dtest="PatientTest,PatientResourceMockTest"

# Nur Integrationstests
mvn test -Dtest="PatientResourceIntegrationTest"
```

### Frontend-Tests (Playwright)

Playwright-Browser beim ersten Mal herunterladen:

```bash
mvn exec:java -e \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"
```

Dann alle Frontend-Tests:

```bash
# Alle Playwright-Tests
mvn test -Dtest="InitialLoadTest,SearchFilterTest,ModalCrudTest,VirtualScrollTest"

# Einen einzelnen Test
mvn test -Dtest="SearchFilterTest#typing_triggersSearchAfterDebounce"
```

### Coverage-Report

```bash
mvn clean verify
# Report öffnen:
open target/site/jacoco/index.html
```

Konfiguriertes Minimum: **87 % Line-Coverage** (schlägt fehl wenn unterschritten).

### Testübersicht

| Klasse | Typ | Tests |
|---|---|---|
| `PatientTest` | Unit | ~35 |
| `PatientResourceMockTest` | Unit (Mockito) | ~20 |
| `PatientResourceIntegrationTest` | Integration (H2) | ~10 |
| `InitialLoadTest` | E2E (Playwright) | 10 |
| `SearchFilterTest` | E2E (Playwright) | 9 |
| `ModalCrudTest` | E2E (Playwright) | 17 |
| `VirtualScrollTest` | E2E (Playwright) | 6 |
| **Gesamt** | | **~107** |

---

## Generierung von 10 Millionen Patientendaten

### Voraussetzungen

```bash
pip install faker psycopg2-binary tqdm
```

### Skript ausführen

```bash
python3 gen-patients.py \
  --host localhost \
  --port 5432 \
  --db hospital_db \
  --user hospital_user \
  --password geheimes_passwort \
  --count 10000000
```

Das Skript (`gen-patients.py`) nutzt:
- **Batch-Inserts** (1000 Zeilen pro Transaktion) für maximale Performance
- **`COPY`-Protokoll** via `psycopg2` (deutlich schneller als einzelne INSERTs)
- Realistische deutsche Namen, Adressen, Versicherungsnummern via `Faker`
- Fortschrittsbalken via `tqdm`

**Erwartete Laufzeit** auf moderner Hardware:

| Methode | 10 Mio. Datensätze |
|---|---|
| Einzelne INSERTs | ~8–12 Stunden |
| Batch-Inserts (1000er) | ~45–90 Minuten |
| PostgreSQL COPY | **~8–15 Minuten** |

### Nach der Generierung: Indizes neu aufbauen

Da die Indizes nach Masseninserts veraltet sein können:

```bash
psql -U hospital_user -d hospital_db -c "
REINDEX TABLE patients;
ANALYZE patients;
VACUUM ANALYZE patients;
"
```

### Performance-Check

```bash
psql -U hospital_user -d hospital_db -c "
EXPLAIN ANALYZE SELECT * FROM search_patients('Müller');
"
```

Der Query-Plan soll `Bitmap Index Scan on idx_patients_trgm` oder `idx_patients_fts` zeigen — kein `Seq Scan`.

---

## API-Referenz

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/patients/random?limit=30` | Zufällige Stichprobe (TABLESAMPLE) |
| `GET` | `/api/patients/search?q={term}` | Volltext-/Trigram-Suche, sortiert |
| `GET` | `/api/patients/{id}` | Einzelpatient per ID |
| `POST` | `/api/patients` | Neuen Patienten anlegen |
| `PUT` | `/api/patients/{id}` | Patienten aktualisieren |
| `DELETE` | `/api/patients/{id}` | Patienten löschen |

---

## Projektstruktur

```
hospital/
├── README.md
├── pom.xml
├── schema.sql
├── gen-patients.py
└── src/
    ├── main/
    │   ├── java/com/hospital/
    │   │   ├── entity/Patient.java
    │   │   └── rest/PatientResource.java
    │   ├── resources/META-INF/persistence.xml
    │   └── webapp/index.html
    └── test/
        ├── java/com/hospital/
        │   ├── entity/PatientTest.java
        │   ├── rest/PatientResourceMockTest.java
        │   ├── rest/PatientResourceIntegrationTest.java
        │   └── ui/
        │       ├── BaseUITest.java
        │       ├── InitialLoadTest.java
        │       ├── SearchFilterTest.java
        │       ├── ModalCrudTest.java
        │       └── VirtualScrollTest.java
        └── resources/META-INF/persistence.xml
```