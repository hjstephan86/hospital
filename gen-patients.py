"""
generate_patients.py
Generiert bis zu 10 Mio. realistische Patientendaten
und lädt sie via PostgreSQL COPY-Protokoll (schnellste Methode).

Verwendung:
  python3 generate_patients.py \
      --host localhost --port 5432 \
      --db hospital_db --user hospital_user \
      --password geheimes_passwort \
      --count 10000000
"""

import argparse
import io
import random
import sys
from datetime import date, timedelta

import psycopg2
from faker import Faker
from tqdm import tqdm

fake = Faker("de_DE")
random.seed(42)

BLUTGRUPPEN   = ["A+", "A-", "B+", "B-", "AB+", "AB-", "0+", "0-"]
STATUS        = ["active", "active", "active", "discharged", "deceased"]
EPA_STATUS    = ["active", "pending", "pending", "revoked"]
KRANKENKASSEN = ["AOK", "TKK", "BKK", "DAK", "Barmer", "HEK", "IKK", "KKH"]
ALLERGIEN     = [
    None, None, None,
    "Penicillin", "Latex", "Nüsse", "Ibuprofen", "Aspirin",
    "Sulfonamide", "Kontrastmittel", "Pollen", "Hausstaubmilben",
]

BATCH_SIZE = 1_000   # Zeilen pro COPY-Puffer


def random_date(start: date, end: date) -> date:
    delta = (end - start).days
    return start + timedelta(days=random.randint(0, delta))


def generate_row(index: int) -> tuple:
    """Erzeugt eine einzelne Patienten-Zeile als Tupel."""
    vorname        = fake.first_name()
    nachname       = fake.last_name()
    geburtsdatum   = random_date(date(1930, 1, 1), date(2005, 12, 31))
    geschlecht     = random.choice(["männlich", "weiblich", "divers"])
    kasse          = random.choice(KRANKENKASSEN)
    versicherungsnr= f"{kasse}-{random.randint(100_000_000, 999_999_999)}"
    blutgruppe     = random.choice(BLUTGRUPPEN)
    status         = random.choice(STATUS)
    epa_status     = random.choice(EPA_STATUS)
    allergie       = random.choice(ALLERGIEN)
    telefon        = fake.phone_number()
    email          = fake.email()
    adresse        = fake.address().replace("\n", ", ")
    notfall        = f"{fake.name()}: {fake.phone_number()}"
    aufnahme       = random_date(date(2000, 1, 1), date(2024, 12, 31))
    entlass        = (
        random_date(aufnahme, date(2025, 6, 30))
        if status == "discharged"
        else None
    )
    patient_number = f"P-{index:010d}"

    return (
        patient_number, vorname, nachname, geburtsdatum,
        geschlecht, versicherungsnr, blutgruppe,
        status, epa_status, allergie, notfall,
        adresse, telefon, email, aufnahme, entlass,
    )


def rows_to_tsv(rows: list[tuple]) -> io.StringIO:
    """Konvertiert Zeilen in ein Tab-getrenntes Format für COPY."""
    buf = io.StringIO()
    for row in rows:
        line = "\t".join(
            r"\N" if v is None else str(v).replace("\t", " ").replace("\n", " ")
            for v in row
        )
        buf.write(line + "\n")
    buf.seek(0)
    return buf


COPY_SQL = """
COPY patients (
    patient_number, vorname, nachname, geburtsdatum,
    geschlecht, versicherungsnr, blutgruppe,
    status, epa_status, allergien, notfallkontakt,
    adresse, telefon, email, aufnahmedatum, entlassdatum
)
FROM STDIN
WITH (FORMAT text, NULL '\\N', DELIMITER E'\\t')
"""


def run(args):
    conn = psycopg2.connect(
        host=args.host, port=args.port,
        dbname=args.db, user=args.user, password=args.password,
    )
    conn.autocommit = False
    cur  = conn.cursor()

    # Bestehende Sequence-Position ermitteln
    cur.execute("SELECT COALESCE(MAX(id), 0) FROM patients")
    start_index = cur.fetchone()[0] + 1

    total   = args.count
    written = 0

    print(f"Starte Generierung von {total:,} Patienten via PostgreSQL COPY …")
    print(f"Batch-Größe: {BATCH_SIZE:,} | Startindex: {start_index:,}\n")

    with tqdm(total=total, unit="Pat.", unit_scale=True, dynamic_ncols=True) as pbar:
        while written < total:
            batch_size = min(BATCH_SIZE, total - written)
            rows = [generate_row(start_index + written + i) for i in range(batch_size)]
            buf  = rows_to_tsv(rows)

            try:
                cur.copy_expert(COPY_SQL, buf)
                conn.commit()
            except Exception as exc:
                conn.rollback()
                print(f"\n[FEHLER] Batch ab Index {start_index + written}: {exc}",
                      file=sys.stderr)
                raise

            written += batch_size
            pbar.update(batch_size)

    cur.close()
    conn.close()
    print(f"\n✓ {written:,} Patienten erfolgreich eingefügt.")
    print("Führe jetzt aus:")
    print("  REINDEX TABLE patients;")
    print("  VACUUM ANALYZE patients;")


def main():
    parser = argparse.ArgumentParser(
        description="Generiert Patientendaten für hospital_db"
    )
    parser.add_argument("--host",     default="localhost")
    parser.add_argument("--port",     default=5432, type=int)
    parser.add_argument("--db",       default="hospital_db")
    parser.add_argument("--user",     default="hospital_user")
    parser.add_argument("--password", required=True)
    parser.add_argument("--count",    default=10_000_000, type=int,
                        help="Anzahl zu generierender Patienten (default: 10_000_000)")
    args = parser.parse_args()
    run(args)


if __name__ == "__main__":
    main()