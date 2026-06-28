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

-- Volltextsuche (GIN) über Vor- und Nachname
CREATE INDEX idx_patients_fts
    ON patients
    USING GIN (
        to_tsvector('german',
            unaccent(vorname) || ' ' || unaccent(nachname)
        )
    );

-- Trigram-Index für Präfix- und Fuzzy-Suche (pg_trgm)
CREATE INDEX idx_patients_trgm
    ON patients
    USING GIN (
        unaccent(vorname || ' ' || nachname) gin_trgm_ops
    );

-- B-Tree Indizes für häufige Filter/Sortierung
CREATE INDEX idx_patients_nachname    ON patients (nachname);
CREATE INDEX idx_patients_status      ON patients (status);
CREATE INDEX idx_patients_aufnahme    ON patients (aufnahmedatum DESC);
CREATE INDEX idx_patients_number      ON patients (patient_number);
CREATE INDEX idx_patients_versich     ON patients (versicherungsnr);

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
CREATE OR REPLACE FUNCTION search_patients(suchbegriff TEXT)
RETURNS SETOF patients AS $$
BEGIN
    RETURN QUERY
        SELECT p.*
        FROM patients p
        WHERE
            to_tsvector('german',
                unaccent(p.vorname) || ' ' || unaccent(p.nachname)
            ) @@ plainto_tsquery('german', unaccent(suchbegriff))
            OR unaccent(p.vorname || ' ' || p.nachname)
                ILIKE unaccent(suchbegriff) || '%'
            OR unaccent(p.nachname)
                ILIKE unaccent(suchbegriff) || '%'
            OR p.patient_number ILIKE suchbegriff || '%'
            OR p.versicherungsnr ILIKE suchbegriff || '%'
        ORDER BY
            p.nachname,
            p.vorname;
END;
$$ LANGUAGE plpgsql STABLE;

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