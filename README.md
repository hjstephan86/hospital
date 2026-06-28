# Krankenhaus-Patientenverwaltung

Vollständiges Krankenhaus-Patientenverwaltungssystem auf Basis von **Jakarta EE 10**, **PostgreSQL** und einer modernen Single-Page-Webanwendung mit virtuellem Scrolling — ausgelegt für **10 Millionen+ Patienten**.

---

## Inhaltsverzeichnis

1. [Architektur](#architektur)
2. [Voraussetzungen](#voraussetzungen)
3. [Installation](#installation)
4. [Konfiguration](#konfiguration)
5. [Anwendung starten](#anwendung-starten)
6. [Anwendung aktualisieren (Hot-Redeploy)](#anwendung-aktualisieren-hot-redeploy)
7. [Testausführung](#testausführung)
8. [Coverage-Report](#coverage-report)
9. [Generierung von Patientendaten](#generierung-von-patientendaten)
10. [API-Referenz](#api-referenz)
11. [Betriebliche Hinweise](#betriebliche-hinweise)
12. [Projektstruktur](#projektstruktur)

---

## Architektur

```
Browser (index.html)
    │  virtuelles Scrolling, 300 ms Debouncing
    ▼
Jakarta EE 10 REST API  (WildFly 40.0.1.Final, RESTEasy)
    │  JPA / Hibernate 7.3.2
    ▼
PostgreSQL 18.4
    │  GIN-Index (Volltext + Trigram, je ein Index pro Suchzweig)
    │  TABLESAMPLE BERNOULLI (Zufallsanzeige)
    ▼
10 Mio.+ Patienten
```

**Designprinzip der Benutzeroberfläche:**
Die initiale Ansicht zeigt 45 zufällige, **unsortierte** Patienten (`random_patients()` via `TABLESAMPLE`). Erst wenn der Benutzer in das Suchfeld tippt, entsteht Ordnung: Die Ergebnisse werden alphabetisch sortiert geladen (begrenzt auf maximal 500 Treffer, siehe [Betriebliche Hinweise](#betriebliche-hinweise)). Das virtuelle Scrolling rendert dabei nur die sichtbaren Zeilen im DOM.

Farbschema: grau/hellgrau gehalten, mit einer dezenten Petrol-Akzentfarbe (`--accent: #3b5b6e`) für Banner, primäre Buttons und aktive Badges.

---

## Voraussetzungen

| Komponente | In diesem Projekt verwendete Version |
|---|---|
| Java JDK | 26 |
| Apache Maven | 3.9.16 |
| PostgreSQL | 18.4 |
| WildFly | 40.0.1.Final |
| Python (Datengenerierung) | 3.10+ (`faker`, `psycopg2-binary`) |

---

## Installation

### 1. Repository klonen

```bash
git clone https://github.com/hjstephan86/hospital.git
cd hospital
```

### 2. JDK und Maven verfügbar machen (Windows)

```powershell
$env:JAVA_HOME  = "C:\Users\sepp5\Downloads\jdk-26_windows-x64_bin\jdk-26.0.1"
$env:MAVEN_HOME = "C:\Users\sepp5\Downloads\apache-maven-3.9.16-bin\apache-maven-3.9.16"
$env:PATH = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:PATH"
```

### 3. PostgreSQL einrichten

```bash
psql -U postgres -c "CREATE DATABASE hospitaldb;"
psql -U postgres -c "CREATE USER hospitaluser WITH PASSWORD 'hospital';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE hospitaldb TO hospitaluser;"

# Schema, Indizes und Funktionen einspielen
psql -U hospitaluser -d hospitaldb -f schema.sql
```

`schema.sql` richtet ein:
- Tabellen `patients`, `diagnosen`, `medikamente`
- `immutable_unaccent()` als IMMUTABLE-Wrapper für `unaccent()` (Voraussetzung für Indexausdrücke)
- GIN-Index für Volltextsuche (`idx_patients_fts`) sowie Trigram-Index über Vor-/Nachname (`idx_patients_trgm`)
- Je einen eigenen Trigram-GIN-Index für Nachname, `patient_number` und `versicherungsnr` (`idx_patients_nachname_trgm`, `idx_patients_number_trgm`, `idx_patients_versich_trgm`) — notwendig, damit jeder `UNION`-Zweig von `search_patients()` seinen eigenen Index nutzen kann (siehe [Betriebliche Hinweise](#betriebliche-hinweise))
- PostgreSQL-Funktion `random_patients(n)` via `TABLESAMPLE BERNOULLI`
- PostgreSQL-Funktion `search_patients(q)` als `LANGUAGE sql`-`UNION` aus fünf einzeln indexierbaren Teilabfragen

### 4. PostgreSQL-JDBC-Treiber in WildFly installieren

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  --command="module add --name=org.postgresql \
  --resources=postgresql-42.7.3.jar \
  --dependencies=javax.api,javax.transaction.api"
```

### 5. DataSource in `standalone.xml` anlegen

```xml
<datasource jndi-name="java:/PostgresDS" pool-name="PostgresDS" enabled="true" use-java-context="true">
    <connection-url>jdbc:postgresql://localhost:5432/hospitaldb</connection-url>
    <driver>postgresql</driver>
    <pool>
        <min-pool-size>5</min-pool-size>
        <max-pool-size>25</max-pool-size>
    </pool>
    <security user-name="hospitaluser" password="hospital"/>
</datasource>
```

> **Hinweis:** Diese DataSource hat aktuell **keinen** `<validation>`-Block (ein `PostgreSQLValidConnectionChecker` führte in diesem Setup zu Schema-Inkompatibilitäten). Das bedeutet, dass abgebrochene/geschlossene Connections im Pool nicht automatisch erkannt werden — siehe [Betriebliche Hinweise](#betriebliche-hinweise) zum manuellen Flush des Pools.

---

## Konfiguration

| Datei | Zweck |
|---|---|
| `src/main/resources/META-INF/persistence.xml` | JPA-Konfiguration, Persistence-Unit `hospitalPU` (JTA, `java:/PostgresDS`) |
| `src/test/resources/META-INF/persistence.xml` | Persistence-Unit `hospitalTestPU`, H2 In-Memory (`RESOURCE_LOCAL`) für Integrationstests |
| `schema.sql` | Datenbankschema, Indizes, Funktionen |
| `src/main/webapp/index.html` | Single-Page-Frontend (Vanilla JS, virtuelles Scrolling, CSS-Variablen für das Farbschema) |

---

## Anwendung starten

### 1. WildFly starten (im Vordergrund)

```bash
cd $WILDFLY_HOME/bin
./standalone.sh          # Linux/macOS
standalone.bat           # Windows
```

WildFly läuft damit im Vordergrund auf Port `8080`. Der Standard-Heap ist in `standalone.conf(.bat)` auf `-Xmx512M` begrenzt (`JBOSS_JAVA_SIZING`) — bei dauerhaft hoher Last auf der 10-Mio.-Zeilen-Tabelle ggf. erhöhen.

### 2. Projekt bauen

```bash
mvn clean package -DskipTests
```

### 3. WAR deployen

```bash
cp target/hospital-management.war $WILDFLY_HOME/standalone/deployments/
```

Der Deployment-Scanner (Scan-Intervall 5 s) erkennt die neue Datei automatisch und legt nach erfolgreichem Deploy eine `hospital-management.war.deployed`-Markerdatei an.

### 4. Anwendung aufrufen

```
http://localhost:8080/hospital-management/
```

---

## Anwendung aktualisieren (Hot-Redeploy)

Der WildFly-**Prozess** sollte nach Möglichkeit nicht neu gestartet werden (laufende Verbindungen, Caches, lange Hochlaufzeit bei 10 Mio. Zeilen). Code-/Frontend-Änderungen werden stattdessen per Hot-Redeploy übernommen:

```bash
mvn clean package -DskipTests
cp target/hospital-management.war $WILDFLY_HOME/standalone/deployments/hospital-management.war
```

Kontrolle, ob der Scanner das neue WAR übernommen hat (Zeitstempel der `.war.deployed`-Markerdatei muss zum neuen WAR passen):

```bash
ls -la $WILDFLY_HOME/standalone/deployments/
```

**Falls der Scanner nicht reagiert** (z. B. nach kurzfristiger Speicherlast — der Scanner-Thread kann dadurch ins Stocken geraten, siehe [Betriebliche Hinweise](#betriebliche-hinweise)), per Management-API direkt redeployen, ohne den Prozess neu zu starten:

```bash
$WILDFLY_HOME/bin/jboss-cli.sh --connect \
  --command="deploy target/hospital-management.war --force"
```

Danach mit `curl` verifizieren, dass die neue Version live ist.

---

## Testausführung

### Unit-, Integrations- und E2E-Tests

```bash
mvn test
```

Aktuell **78 Tests**, alle grün:

| Klasse | Typ | Tests |
|---|---|---|
| `PatientResourceMockTest` | Unit (Mockito) | 21 |
| `PatientResourceIntegrationTest` | Integration (H2) | 10 |
| `InitialLoadTest` | E2E (Playwright) | 10 |
| `SearchFilterTest` | E2E (Playwright) | 9 |
| `ModalCrudTest` | E2E (Playwright) | 22 |
| `VirtualScrollTest` | E2E (Playwright) | 6 |
| **Gesamt** | | **78** |

Einzelne Klasse/Test gezielt ausführen:

```bash
mvn test -Dtest="PatientResourceMockTest"
mvn test -Dtest="SearchFilterTest#typing_triggersSearchAfterDebounce"
```

### Frontend-Tests (Playwright)

Die E2E-Tests (`InitialLoadTest`, `SearchFilterTest`, `ModalCrudTest`, `VirtualScrollTest`) starten einen headless Chromium über Playwright und einen eingebetteten WireMock-Server (Port 8089), der `index.html` sowie die `/api/patients/*`-Endpunkte mockt — sie laufen automatisch mit `mvn test` mit, kein separates CI-System nötig.

Chromium-Browser-Binary einmalig installieren (falls noch nicht vorhanden):

```bash
mvn exec:java -e \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"
```

### Voraussetzungen für `mockito-core` / RESTEasy

- `mockito-core`/`mockito-junit-jupiter` Version **5.23.0** (ältere Versionen scheitern auf JDK 26 mit `MockitoException: Could not modify all classes` wegen einer zu alten Byte-Buddy-Version)
- `org.jboss.resteasy:resteasy-core:7.0.2.Final` als Test-Scope-Abhängigkeit (liefert die `jakarta.ws.rs.ext.RuntimeDelegate`-Implementierung, die `jakarta.jakartaee-api` selbst nicht enthält)

---

## Coverage-Report

```bash
mvn clean verify
```

`verify` führt zusätzlich zu den Tests den JaCoCo-Report sowie eine Coverage-Prüfung aus (Minimum **87 % Line-Coverage** pro Package, schlägt sonst fehl).

Report öffnen:

```
doc/coverage/index.html
```

(`jacoco-maven-plugin`, Execution `report`, `outputDirectory` auf `${project.basedir}/doc/coverage` konfiguriert — Standard wäre `target/site/jacoco`.)

Aktueller Stand: ~98 % Line-Coverage über `PatientResource`, `Patient` und `RestApplication`.

---

## Generierung von Patientendaten

### Voraussetzungen

```bash
pip install faker psycopg2-binary
```

### Skript ausführen

```bash
python gen-patients.py \
  --host localhost \
  --port 5432 \
  --db hospitaldb \
  --user hospitaluser \
  --password hospital \
  --count 10000000
```

(Die Defaults in `gen-patients.py` lauten `hospital_db`/`hospital_user` — für dieses Setup müssen `--db`/`--user` wie oben explizit auf `hospitaldb`/`hospitaluser` gesetzt werden.)

`gen-patients.py` nutzt:
- Das `COPY`-Protokoll via `psycopg2.copy_expert` (deutlich schneller als einzelne `INSERT`s)
- Realistische deutsche Namen/Adressen via `Faker` (Locale `de_DE`)
- **Index-basierte** Versicherungsnummern (`{Kasse}-{index:09d}`) statt rein zufälliger Nummern — bei 10 Mio. Zeilen und nur 8 Kassen-Präfixen führt reiner Zufall wegen des Geburtstagsparadoxons zwangsläufig zu `UNIQUE`-Verletzungen auf `versicherungsnr`

### Nach der Generierung: Indizes neu aufbauen

```bash
psql -U hospitaluser -d hospitaldb -c "
REINDEX TABLE patients;
VACUUM ANALYZE patients;
"
```

### Performance-Check

```bash
psql -U hospitaluser -d hospitaldb -c "EXPLAIN ANALYZE SELECT * FROM search_patients('Müller');"
```

Erwartete Query-Zeit bei 10 Mio. Zeilen: **~650–820 ms** (Bitmap-Index-Scans über die Trigram-/Volltext-Indizes je `UNION`-Zweig). Ein `Seq Scan` über die gesamte Tabelle deutet auf veraltete/fehlende Indizes hin (siehe `schema.sql`).

---

## API-Referenz

| Method | Endpoint | Beschreibung |
|---|---|---|
| `GET` | `/api/patients/random?limit=30` | Zufällige Stichprobe (`TABLESAMPLE`). Frontend ruft mit `limit=45` |
| `GET` | `/api/patients/search?q={term}` | Volltext-/Trigram-Suche, sortiert, **maximal 500 Treffer** |
| `GET` | `/api/patients/{id}` | Einzelpatient per ID |
| `POST` | `/api/patients` | Neuen Patienten anlegen |
| `PUT` | `/api/patients/{id}` | Patienten aktualisieren |
| `DELETE` | `/api/patients/{id}` | Patienten löschen |

---

## Betriebliche Hinweise

- **`search`-Endpoint ist auf 500 Treffer begrenzt** (`PatientResource.search()`, `SELECT * FROM search_patients(:q) LIMIT 500`). Ohne diese Grenze kann ein breiter Suchbegriff bei 10 Mio. Zeilen Millionen Treffer liefern und den Heap sprengen (`OutOfMemoryError: Java heap space`) — das ist in diesem Setup bereits einmal aufgetreten und hat anschließend über kaskadierende `PSQLException: This connection has been closed`-Fehler auch den Connection-Pool in Mitleidenschaft gezogen.
- **Connection-Pool nach einem solchen Vorfall zurücksetzen** (ohne WildFly neu zu starten):
  ```bash
  $WILDFLY_HOME/bin/jboss-cli.sh --connect \
    --command="/subsystem=datasources/data-source=PostgresDS:flush-all-connection-in-pool"
  ```
- **`search_patients()` ist als `UNION` aus fünf Teilabfragen implementiert** (statt eines einzelnen `WHERE … OR …`). Grund: Der PostgreSQL-Planer verwirft bei einem gemeinsamen `OR`-Filter alle GIN-Indizes, sobald nur ein Zweig keinen passenden Index hat (kein `BitmapOr` möglich) — bei 10 Mio. Zeilen führt das sonst zu einem vollständigen Sequential Scan.
- **Deployment-Scanner kann nach Speicherlast stocken**: Tritt das auf (`.war.deployed`-Marker bleibt trotz neuer WAR-Datei auf altem Zeitstempel stehen), per `jboss-cli deploy --force` direkt über die Management-API redeployen (siehe [Anwendung aktualisieren](#anwendung-aktualisieren-hot-redeploy)) — das ist unabhängig vom Scanner-Thread.

---

## Projektstruktur

```
hospital/
├── README.md
├── pom.xml
├── schema.sql
├── gen-patients.py
├── doc/
│   └── coverage/              # JaCoCo-HTML-Report (mvn verify)
└── src/
    ├── main/
    │   ├── java/com/hospital/
    │   │   ├── entity/Patient.java
    │   │   ├── rest/PatientResource.java
    │   │   └── rest/RestApplication.java
    │   ├── resources/META-INF/persistence.xml
    │   └── webapp/index.html
    └── test/
        ├── java/com/hospital/
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
