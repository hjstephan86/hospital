package com.hospital.rest;

import com.hospital.entity.Patient;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integrations-Tests mit echter H2-Datenbank (In-Memory).
 * Verifiziert das Zusammenspiel von PatientResource + JPA.
 * Persistence-Unit: hospitalTestPU (src/test/resources/META-INF/persistence.xml)
 */
class PatientResourceIntegrationTest {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private PatientResource resource;
    private EntityTransaction tx;

    @BeforeAll
    static void setUpEmf() {
        emf = Persistence.createEntityManagerFactory("hospitalTestPU");
    }

    @AfterAll
    static void tearDownEmf() {
        if (emf != null) emf.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        em       = emf.createEntityManager();
        tx       = em.getTransaction();
        resource = new PatientResource();

        Field f = PatientResource.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(resource, em);
    }

    @AfterEach
    void tearDown() {
        if (tx.isActive()) tx.rollback();
        // Tabelle leeren für Isolation
        tx.begin();
        em.createQuery("DELETE FROM Patient").executeUpdate();
        tx.commit();
        em.close();
    }

    // ── Hilfsmethode ─────────────────────────────────────
    private Patient persist(String vorname, String nachname, String number) {
        Patient p = new Patient();
        p.setPatientNumber(number);
        p.setVorname(vorname);
        p.setNachname(nachname);
        p.setGeburtsdatum(LocalDate.of(1980, 1, 1));
        p.setStatus("active");
        p.setEpaStatus("pending");
        tx.begin();
        em.persist(p);
        tx.commit();
        em.clear();
        return p;
    }

    // ── getById ────────────────────────────────────────
    @Test
    void getById_existingPatient_found() {
        Patient p = persist("Anna", "Müller", "P-001");
        var r = resource.getById(p.getId());
        assertEquals(200, r.getStatus());
        Patient found = (Patient) r.getEntity();
        assertEquals("Anna", found.getVorname());
    }

    @Test
    void getById_unknownId_returns404() {
        var r = resource.getById(Long.MAX_VALUE);
        assertEquals(404, r.getStatus());
    }

    // ── create ─────────────────────────────────────────
    @Test
    void create_persistsToDatabase() {
        Patient p = new Patient();
        p.setPatientNumber("P-INT-001");
        p.setVorname("Klaus");
        p.setNachname("Schmidt");
        p.setGeburtsdatum(LocalDate.of(1972, 6, 15));

        tx.begin();
        var r = resource.create(p);
        tx.commit();

        assertEquals(201, r.getStatus());
        assertNotNull(p.getId());

        Patient fromDb = em.find(Patient.class, p.getId());
        assertNotNull(fromDb);
        assertEquals("Klaus", fromDb.getVorname());
    }

    // ── update ─────────────────────────────────────────
    @Test
    void update_changesNameInDatabase() {
        Patient p = persist("Alt", "Name", "P-INT-002");

        Patient updated = new Patient();
        updated.setVorname("Neu");
        updated.setNachname("Nachname");
        updated.setGeburtsdatum(LocalDate.of(1990, 2, 2));
        updated.setStatus("discharged");
        updated.setEpaStatus("revoked");

        tx.begin();
        var r = resource.update(p.getId(), updated);
        tx.commit();

        assertEquals(200, r.getStatus());
        em.clear();
        Patient fromDb = em.find(Patient.class, p.getId());
        assertEquals("Neu",       fromDb.getVorname());
        assertEquals("discharged", fromDb.getStatus());
    }

    @Test
    void update_unknownId_returns404() {
        var r = resource.update(Long.MAX_VALUE, new Patient());
        assertEquals(404, r.getStatus());
    }

    // ── delete ─────────────────────────────────────────
    @Test
    void delete_removesFromDatabase() {
        Patient p = persist("Zu", "Löschen", "P-INT-003");
        Long id = p.getId();

        tx.begin();
        var r = resource.delete(id);
        tx.commit();

        assertEquals(204, r.getStatus());
        assertNull(em.find(Patient.class, id));
    }

    @Test
    void delete_unknownId_returns404() {
        var r = resource.delete(Long.MAX_VALUE);
        assertEquals(404, r.getStatus());
    }

    // ── @PrePersist via JPA ────────────────────────────
    @Test
    void create_onCreateSetsDates() {
        Patient p = new Patient();
        p.setPatientNumber("P-INT-004");
        p.setVorname("Test");
        p.setNachname("User");
        p.setGeburtsdatum(LocalDate.of(2000, 1, 1));

        tx.begin();
        resource.create(p);
        tx.commit();

        assertNotNull(p.getCreatedAt());
        assertNotNull(p.getUpdatedAt());
        assertNotNull(p.getAufnahmedatum());
    }

    // ── mehrere Patienten ─────────────────────────────
    @Test
    void multiplePatients_canBeCreatedAndDeleted() {
        Patient p1 = persist("A1", "B1", "P-INT-005");
        Patient p2 = persist("A2", "B2", "P-INT-006");

        tx.begin();
        resource.delete(p1.getId());
        tx.commit();

        assertNull(em.find(Patient.class, p1.getId()));
        assertNotNull(em.find(Patient.class, p2.getId()));
    }

    @Test
    void update_allOptionalFields() {
        Patient p = persist("Orig", "Orig", "P-INT-007");

        Patient upd = new Patient();
        upd.setVorname("Upd");
        upd.setNachname("Upd");
        upd.setGeburtsdatum(LocalDate.of(1985, 5, 5));
        upd.setGeschlecht("weiblich");
        upd.setVersicherungsnr("TK-UPD");
        upd.setBlutgruppe("B+");
        upd.setStatus("active");
        upd.setEpaStatus("active");
        upd.setAllergien("Nüsse");
        upd.setNotfallkontakt("Max: 0521-0");
        upd.setAdresse("Neue Str. 99");
        upd.setTelefon("0521-111");
        upd.setEmail("upd@example.de");

        tx.begin();
        var r = resource.update(p.getId(), upd);
        tx.commit();

        assertEquals(200, r.getStatus());
        em.clear();
        Patient fromDb = em.find(Patient.class, p.getId());
        assertEquals("TK-UPD",   fromDb.getVersicherungsnr());
        assertEquals("B+",        fromDb.getBlutgruppe());
        assertEquals("Nüsse",     fromDb.getAllergien());
        assertEquals("upd@example.de", fromDb.getEmail());
    }
}