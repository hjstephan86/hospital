-- ============================================================
-- Krankenhaus-Patientenverwaltung: PostgreSQL Schema
-- Optimiert für 10 Mio.+ Patienten
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- ------------------------------------------------------------
-- Tabelle: patients
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS patients (
    id               BIGSERIAL PRIMARY KEY,
    patient_number   VARCHAR(20)  UNIQUE NOT NULL,
    vorname          VARCHAR(100) NOT NULL,
    nachname         VARCHAR(100) NOT NULL,
    geburtsdatum     DATE         NOT NULL,
    geschlecht       VARCHAR(10),
    versicherungsnr  VARCHAR(30)  UNIQUE,
    blutgruppe       VARCHAR(5),
    status           VARCHAR(20)  NOT NULL DEFAULT 'active'
                         CHECK (status IN ('active', 'discharged', 'deceased')),
    epa_status       VARCHAR(20)  DEFAULT 'pending'
                         CHECK (epa_status IN ('pending', 'active', 'revoked')),
    allergien        TEXT,
    notfallkontakt   VARCHAR(255),
    adresse          TEXT,
    telefon          VARCHAR(30),
    email            VARCHAR(150),
    aufnahmedatum    TIMESTAMP    DEFAULT NOW(),
    entlassdatum     TIMESTAMP,
    created_at       TIMESTAMP    DEFAULT NOW(),
    updated_at       TIMESTAMP    DEFAULT NOW()
);

-- ------------------------------------------------------------
-- Tabelle: diagnosen
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS diagnosen (
    id          BIGSERIAL PRIMARY KEY,
    patient_id  BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    icd10_code  VARCHAR(10),
    bezeichnung TEXT NOT NULL,
    datum       DATE DEFAULT CURRENT_DATE,
    arzt        VARCHAR(150)
);

-- ------------------------------------------------------------
-- Tabelle: medikamente
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS medikamente (
    id          BIGSERIAL PRIMARY KEY,
    patient_id  BIGINT NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    dosierung   VARCHAR(100),
    einheit     VARCHAR(50),
    beginn      DATE DEFAULT CURRENT_DATE,
    ende        DATE
);

-- ============================================================
-- INDIZES — kritisch bei 10 Mio. Datensätzen
-- ============================================================

-- unaccent() ist als STABLE markiert und daher nicht direkt in
-- Indexausdrücken zulässig; immutable Wrapper-Funktion notwendig.
CREATE OR REPLACE FUNCTION immutable_unaccent(TEXT)
RETURNS TEXT AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, $1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- Volltextsuche (GIN) über Vor- und Nachname
CREATE INDEX idx_patients_fts
    ON patients
    USING GIN (
        to_tsvector('german',
            immutable_unaccent(vorname) || ' ' || immutable_unaccent(nachname)
        )
    );

-- Trigram-Index für Präfix- und Fuzzy-Suche (pg_trgm)
CREATE INDEX idx_patients_trgm
    ON patients
    USING GIN (
        immutable_unaccent(vorname || ' ' || nachname) gin_trgm_ops
    );

-- B-Tree Indizes für häufige Filter/Sortierung
CREATE INDEX idx_patients_nachname    ON patients (nachname);
CREATE INDEX idx_patients_status      ON patients (status);
CREATE INDEX idx_patients_aufnahme    ON patients (aufnahmedatum DESC);
CREATE INDEX idx_patients_number      ON patients (patient_number);
CREATE INDEX idx_patients_versich     ON patients (versicherungsnr);

-- Trigram-Indizes für die einzelnen OR-Zweige von search_patients():
-- Bei einem gemeinsamen WHERE ... OR ... verwirft der Planer bei 10 Mio.
-- Zeilen GIN-Indizes, sobald nur ein Zweig ohne passenden Index dabei ist
-- (kein BitmapOr möglich) — daher pro Zweig ein eigener Index, kombiniert
-- über UNION in der Funktion statt über OR in einem einzigen Scan.
CREATE INDEX idx_patients_nachname_trgm ON patients USING GIN (immutable_unaccent(nachname) gin_trgm_ops);
CREATE INDEX idx_patients_number_trgm   ON patients USING GIN (patient_number gin_trgm_ops);
CREATE INDEX idx_patients_versich_trgm  ON patients USING GIN (versicherungsnr gin_trgm_ops);

CREATE INDEX idx_diagnosen_patient    ON diagnosen (patient_id);
CREATE INDEX idx_medikamente_patient  ON medikamente (patient_id);

-- ============================================================
-- FUNKTION: Zufällige Stichprobe (performant bei 10 Mio.)
-- Verwendet TABLESAMPLE statt ORDER BY RANDOM()
-- ============================================================
CREATE OR REPLACE FUNCTION random_patients(target INT DEFAULT 30)
RETURNS SETOF patients AS $$
DECLARE
    total BIGINT;
    pct   DOUBLE PRECISION;
BEGIN
    SELECT reltuples::BIGINT INTO total
    FROM pg_class WHERE relname = 'patients';

    IF total <= 0 THEN total := 1; END IF;
    pct := LEAST((target::DOUBLE PRECISION / total) * 100 * 3, 100);

    RETURN QUERY
        SELECT * FROM patients
        TABLESAMPLE BERNOULLI(pct)
        LIMIT target;
END;
$$ LANGUAGE plpgsql STABLE;

-- ============================================================
-- FUNKTION: Filtersuche — sortiert, ohne LIMIT
-- Für virtuelles Scrolling im Frontend
-- ============================================================
-- Als SQL-Funktion mit UNION statt einem einzigen OR-Filter: jeder Zweig
-- nutzt so seinen eigenen GIN/Trigram-Index (siehe Indizes oben). Ein
-- gemeinsamer OR-Filter über alle Bedingungen zwingt den Planer bei
-- 10 Mio. Zeilen sonst zu einem vollständigen Sequential Scan.
CREATE OR REPLACE FUNCTION search_patients(suchbegriff TEXT)
RETURNS SETOF patients AS $$
    SELECT * FROM (
        SELECT p.* FROM patients p
        WHERE to_tsvector('german',
                immutable_unaccent(p.vorname) || ' ' || immutable_unaccent(p.nachname)
              ) @@ plainto_tsquery('german', immutable_unaccent(suchbegriff))
        UNION
        SELECT p.* FROM patients p
        WHERE immutable_unaccent(p.vorname || ' ' || p.nachname)
              ILIKE immutable_unaccent(suchbegriff) || '%'
        UNION
        SELECT p.* FROM patients p
        WHERE immutable_unaccent(p.nachname)
              ILIKE immutable_unaccent(suchbegriff) || '%'
        UNION
        SELECT p.* FROM patients p
        WHERE p.patient_number ILIKE suchbegriff || '%'
        UNION
        SELECT p.* FROM patients p
        WHERE p.versicherungsnr ILIKE suchbegriff || '%'
    ) p
    ORDER BY nachname, vorname;
$$ LANGUAGE sql STABLE;

-- ============================================================
-- Beispieldaten
-- ============================================================
INSERT INTO patients (patient_number, vorname, nachname, geburtsdatum, geschlecht,
                      versicherungsnr, blutgruppe, status, epa_status, allergien,
                      notfallkontakt, telefon, email)
VALUES
('P-000001','Anna','Müller','1985-03-12','weiblich','AOK-123456789','A+','active','active','Penicillin','Hans Müller: 0521-111222','0521-334455','anna.mueller@example.de'),
('P-000002','Klaus','Schmidt','1972-07-24','männlich','TKK-987654321','0+','active','pending',NULL,'Petra Schmidt: 0521-222333','0521-445566','k.schmidt@example.de'),
('P-000003','Maria','Weber','1990-11-05','weiblich','BKK-456789123','B-','discharged','revoked','Latex, Ibuprofen','Johann Weber: 0521-333444','0521-556677','maria.weber@example.de'),
('P-000004','Thomas','Fischer','1965-01-30','männlich','AOK-741852963','AB+','active','active',NULL,'Sandra Fischer: 0521-444555','0521-667788','t.fischer@example.de'),
('P-000005','Sabine','Meyer','1998-06-18','weiblich','TKK-369258147','A-','active','pending','Nüsse','Ralf Meyer: 0521-555666','0521-778899','sabine.meyer@example.de');