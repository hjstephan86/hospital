package com.hospital.rest;

import com.hospital.entity.Patient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mock-basierte Unit-Tests für PatientResource.
 * Kein Applikationsserver nötig — EntityManager wird gemockt.
 * Ziel: ≥ 87 % Line-Coverage auf PatientResource.java
 */
@ExtendWith(MockitoExtension.class)
class PatientResourceMockTest {

    @Mock EntityManager em;
    @Mock Query         query;

    @InjectMocks
    PatientResource resource;

    // ── Hilfsmethode: em-Feld per Reflection injizieren ──
    @BeforeEach
    void injectEm() throws Exception {
        Field f = PatientResource.class.getDeclaredField("em");
        f.setAccessible(true);
        f.set(resource, em);
    }

    private Patient samplePatient(Long id) {
        Patient p = new Patient();
        p.setId(id);
        p.setPatientNumber("P-00" + id);
        p.setVorname("Anna");
        p.setNachname("Müller");
        p.setGeburtsdatum(LocalDate.of(1985, 3, 12));
        p.setGeschlecht("weiblich");
        p.setVersicherungsnr("AOK-" + id);
        p.setBlutgruppe("A+");
        p.setStatus("active");
        p.setEpaStatus("active");
        p.setAllergien("Penicillin");
        p.setNotfallkontakt("Hans: 0521-1");
        p.setAdresse("Musterstr. 1");
        p.setTelefon("0521-999");
        p.setEmail("anna@example.de");
        p.setAufnahmedatum(LocalDateTime.now());
        return p;
    }

    // ════════════════════════════════════════════════════════
    // GET /patients/random
    // ════════════════════════════════════════════════════════

    @Test
    void getRandom_returnsPatientList() {
        List<Patient> patients = List.of(samplePatient(1L), samplePatient(2L));
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(patients);

        Response r = resource.getRandom(30);

        assertEquals(200, r.getStatus());
        assertEquals(patients, r.getEntity());
    }

    @Test
    void getRandom_defaultLimit() {
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        Response r = resource.getRandom(30);
        assertEquals(200, r.getStatus());
    }

    @Test
    void getRandom_emptyResult() {
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        Response r = resource.getRandom(30);
        assertEquals(200, r.getStatus());
        assertTrue(((List<?>) r.getEntity()).isEmpty());
    }

    // ════════════════════════════════════════════════════════
    // GET /patients/search
    // ════════════════════════════════════════════════════════

    @Test
    void search_withQuery_returnsFilteredList() {
        List<Patient> found = List.of(samplePatient(1L));
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(found);

        Response r = resource.search("Müller");
        assertEquals(200, r.getStatus());
        assertEquals(found, r.getEntity());
    }

    @Test
    void search_emptyString_delegatesToRandom() {
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        Response r = resource.search("");
        assertEquals(200, r.getStatus());
    }

    @Test
    void search_nullQuery_delegatesToRandom() {
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        Response r = resource.search(null);
        assertEquals(200, r.getStatus());
    }

    @Test
    void search_blankQuery_delegatesToRandom() {
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        Response r = resource.search("   ");
        assertEquals(200, r.getStatus());
    }

    @Test
    void search_trimsWhitespace() {
        List<Patient> found = List.of(samplePatient(3L));
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(eq("q"), eq("Müller"))).thenReturn(query);
        when(query.getResultList()).thenReturn(found);

        Response r = resource.search("  Müller  ");
        assertEquals(200, r.getStatus());
        verify(query).setParameter("q", "Müller");
    }

    @Test
    void search_noResults_returns200WithEmptyList() {
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        Response r = resource.search("XYZunbekannt");
        assertEquals(200, r.getStatus());
        assertTrue(((List<?>) r.getEntity()).isEmpty());
    }

    // ════════════════════════════════════════════════════════
    // GET /patients/{id}
    // ════════════════════════════════════════════════════════

    @Test
    void getById_found_returns200() {
        Patient p = samplePatient(1L);
        when(em.find(Patient.class, 1L)).thenReturn(p);

        Response r = resource.getById(1L);
        assertEquals(200, r.getStatus());
        assertEquals(p, r.getEntity());
    }

    @Test
    void getById_notFound_returns404() {
        when(em.find(Patient.class, 99L)).thenReturn(null);

        Response r = resource.getById(99L);
        assertEquals(404, r.getStatus());
    }

    // ════════════════════════════════════════════════════════
    // POST /patients
    // ════════════════════════════════════════════════════════

    @Test
    void create_persistsAndReturns201() {
        Patient p = samplePatient(null);
        doNothing().when(em).persist(p);
        doNothing().when(em).flush();

        Response r = resource.create(p);
        assertEquals(201, r.getStatus());
        assertEquals(p, r.getEntity());
        verify(em).persist(p);
        verify(em).flush();
    }

    @Test
    void create_minimalPatient_returns201() {
        Patient p = new Patient();
        p.setVorname("Max");
        p.setNachname("Mustermann");
        p.setGeburtsdatum(LocalDate.of(2000, 1, 1));
        doNothing().when(em).persist(p);
        doNothing().when(em).flush();

        Response r = resource.create(p);
        assertEquals(201, r.getStatus());
    }

    // ════════════════════════════════════════════════════════
    // PUT /patients/{id}
    // ════════════════════════════════════════════════════════

    @Test
    void update_found_updatesAllFieldsAndReturns200() {
        Patient existing = samplePatient(1L);
        when(em.find(Patient.class, 1L)).thenReturn(existing);
        when(em.merge(existing)).thenReturn(existing);

        Patient updated = new Patient();
        updated.setVorname("Klaus");
        updated.setNachname("Fischer");
        updated.setGeburtsdatum(LocalDate.of(1970, 7, 7));
        updated.setGeschlecht("männlich");
        updated.setVersicherungsnr("TK-999");
        updated.setBlutgruppe("0-");
        updated.setStatus("discharged");
        updated.setEpaStatus("revoked");
        updated.setAllergien("Latex");
        updated.setNotfallkontakt("Petra: 0521-2");
        updated.setAdresse("Neuestr. 5");
        updated.setTelefon("0521-888");
        updated.setEmail("k@example.de");
        updated.setEntlassdatum(LocalDateTime.of(2024, 2, 1, 10, 0));

        Response r = resource.update(1L, updated);

        assertEquals(200, r.getStatus());
        assertEquals("Klaus",        existing.getVorname());
        assertEquals("Fischer",      existing.getNachname());
        assertEquals("männlich",     existing.getGeschlecht());
        assertEquals("TK-999",       existing.getVersicherungsnr());
        assertEquals("0-",           existing.getBlutgruppe());
        assertEquals("discharged",   existing.getStatus());
        assertEquals("revoked",      existing.getEpaStatus());
        assertEquals("Latex",        existing.getAllergien());
        assertEquals("Petra: 0521-2",existing.getNotfallkontakt());
        assertEquals("Neuestr. 5",   existing.getAdresse());
        assertEquals("0521-888",     existing.getTelefon());
        assertEquals("k@example.de", existing.getEmail());
        assertNotNull(existing.getEntlassdatum());
        verify(em).merge(existing);
    }

    @Test
    void update_notFound_returns404() {
        when(em.find(Patient.class, 99L)).thenReturn(null);
        Response r = resource.update(99L, new Patient());
        assertEquals(404, r.getStatus());
        verify(em, never()).merge(any());
    }

    @Test
    void update_nullOptionalFields_setToNull() {
        Patient existing = samplePatient(1L);
        when(em.find(Patient.class, 1L)).thenReturn(existing);
        when(em.merge(existing)).thenReturn(existing);

        Patient updated = new Patient();
        updated.setVorname("A");
        updated.setNachname("B");
        updated.setGeburtsdatum(LocalDate.now());
        // alle optionalen Felder bleiben null

        resource.update(1L, updated);
        assertNull(existing.getAllergien());
        assertNull(existing.getNotfallkontakt());
        assertNull(existing.getEntlassdatum());
    }

    // ════════════════════════════════════════════════════════
    // DELETE /patients/{id}
    // ════════════════════════════════════════════════════════

    @Test
    void delete_found_returns204() {
        Patient p = samplePatient(1L);
        when(em.find(Patient.class, 1L)).thenReturn(p);
        doNothing().when(em).remove(p);

        Response r = resource.delete(1L);
        assertEquals(204, r.getStatus());
        verify(em).remove(p);
    }

    @Test
    void delete_notFound_returns404() {
        when(em.find(Patient.class, 99L)).thenReturn(null);

        Response r = resource.delete(99L);
        assertEquals(404, r.getStatus());
        verify(em, never()).remove(any());
    }

    // ════════════════════════════════════════════════════════
    // Edge Cases
    // ════════════════════════════════════════════════════════

    @Test
    void getRandom_largeLimit_stillReturnsOk() {
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        Response r = resource.getRandom(1000);
        assertEquals(200, r.getStatus());
    }

    @Test
    void search_specialChars_doesNotThrow() {
        when(em.createNativeQuery(anyString(), eq(Patient.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        assertDoesNotThrow(() -> resource.search("Müller-Lüdenscheid"));
    }

    @Test
    void create_patientWithAllFields_returns201() {
        Patient p = samplePatient(null);
        p.setEntlassdatum(LocalDateTime.now());
        doNothing().when(em).persist(p);
        doNothing().when(em).flush();

        Response r = resource.create(p);
        assertEquals(201, r.getStatus());
    }
}