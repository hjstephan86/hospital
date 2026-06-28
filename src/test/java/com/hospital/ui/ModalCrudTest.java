package com.hospital.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für das Modal (Neuer Patient / Bearbeiten):
 * - Modal öffnet/schließt korrekt
 * - Pflichtfeld-Validierung
 * - Neuen Patienten anlegen (POST)
 * - Patienten bearbeiten (PUT)
 * - Patienten löschen (DELETE)
 * - Toast-Meldungen erscheinen
 */
class ModalCrudTest extends BaseUITest {

    private void openNewPatientModal() {
        page.locator("header button").click();
        page.waitForSelector(".modal-overlay.open");
    }

    private void fillMandatoryFields(String vorname, String nachname, String datum) {
        page.locator("#fVorname").fill(vorname);
        page.locator("#fNachname").fill(nachname);
        page.locator("#fGeburtsdatum").fill(datum);
    }

    // ── Modal öffnen / schließen ──────────────────────────

    @Test
    void newPatientButton_opensModal() {
        openNewPatientModal();
        assertTrue(page.locator(".modal-overlay").getAttribute("class").contains("open"));
    }

    @Test
    void modalTitle_isNeuPatient() {
        openNewPatientModal();
        assertEquals("Neuer Patient", page.locator("#modalTitle").textContent());
    }

    @Test
    void abbrechenButton_closesModal() {
        openNewPatientModal();
        page.locator(".btn-outline").click();
        assertFalse(page.locator(".modal-overlay").getAttribute("class").contains("open"));
    }

    @Test
    void clickOutsideModal_closesModal() {
        openNewPatientModal();
        // Klick auf den Overlay-Hintergrund (nicht auf .modal selbst)
        page.locator("#modalOverlay").click(
            new com.microsoft.playwright.Locator.ClickOptions().setPosition(5, 5));
        page.waitForTimeout(300);
        assertFalse(page.locator(".modal-overlay").getAttribute("class").contains("open"));
    }

    // ── Formularfelder ────────────────────────────────────

    @Test
    void allFormFields_arePresent() {
        openNewPatientModal();
        assertTrue(page.locator("#fVorname").isVisible());
        assertTrue(page.locator("#fNachname").isVisible());
        assertTrue(page.locator("#fGeburtsdatum").isVisible());
        assertTrue(page.locator("#fGeschlecht").isVisible());
        assertTrue(page.locator("#fVersicherungsnr").isVisible());
        assertTrue(page.locator("#fBlutgruppe").isVisible());
        assertTrue(page.locator("#fStatus").isVisible());
        assertTrue(page.locator("#fEpaStatus").isVisible());
        assertTrue(page.locator("#fTelefon").isVisible());
        assertTrue(page.locator("#fEmail").isVisible());
        assertTrue(page.locator("#fAdresse").isVisible());
        assertTrue(page.locator("#fNotfallkontakt").isVisible());
        assertTrue(page.locator("#fAllergien").isVisible());
    }

    @Test
    void statusSelect_hasAllOptions() {
        openNewPatientModal();
        Locator opts = page.locator("#fStatus option");
        assertEquals(3, opts.count());
    }

    @Test
    void epaStatusSelect_hasAllOptions() {
        openNewPatientModal();
        Locator opts = page.locator("#fEpaStatus option");
        assertEquals(3, opts.count());
    }

    @Test
    void blutgruppeSelect_hasEightOptions() {
        openNewPatientModal();
        // 8 Blutgruppen + 1 leere Option
        assertTrue(page.locator("#fBlutgruppe option").count() >= 8);
    }

    // ── Validierung ───────────────────────────────────────

    @Test
    void saveWithoutMandatoryFields_showsToast() {
        openNewPatientModal();
        page.locator(".modal .btn").last().click(); // Speichern ohne Pflichtfelder
        page.waitForTimeout(200);
        assertTrue(page.locator(".toast").textContent()
            .contains("Vorname") || page.locator(".toast").isVisible());
    }

    @Test
    void saveWithoutNachname_showsValidationToast() {
        openNewPatientModal();
        page.locator("#fVorname").fill("Anna");
        // Nachname fehlt
        page.locator("#fGeburtsdatum").fill("1985-03-12");
        page.locator(".modal .btn").last().click();
        page.waitForTimeout(200);
        assertTrue(page.locator(".toast").isVisible());
    }

    @Test
    void saveWithoutGeburtsdatum_showsValidationToast() {
        openNewPatientModal();
        page.locator("#fVorname").fill("Anna");
        page.locator("#fNachname").fill("Müller");
        // Geburtsdatum fehlt
        page.locator(".modal .btn").last().click();
        page.waitForTimeout(200);
        assertTrue(page.locator(".toast").isVisible());
    }

    // ── Neuen Patienten anlegen (POST) ────────────────────

    @Test
    void createPatient_sendsPostRequest() {
        openNewPatientModal();
        fillMandatoryFields("Klaus", "Fischer", "1972-06-15");
        page.locator("#fPatientNumber").fill("P-TEST-01");
        page.locator("#fBlutgruppe").selectOption("AB+");

        Response resp = page.waitForResponse(
            r -> r.url().contains("/api/patients") && r.request().method().equals("POST"),
            () -> page.locator(".modal .btn").last().click()
        );

        assertEquals(201, resp.status());
    }

    @Test
    void createPatient_closesModalAfterSuccess() {
        openNewPatientModal();
        fillMandatoryFields("Klaus", "Fischer", "1972-06-15");

        page.waitForResponse(
            r -> r.url().contains("/api/patients") && r.request().method().equals("POST"),
            () -> page.locator(".modal .btn").last().click()
        );

        page.waitForTimeout(300);
        assertFalse(page.locator(".modal-overlay").getAttribute("class").contains("open"));
    }

    @Test
    void createPatient_showsSuccessToast() {
        openNewPatientModal();
        fillMandatoryFields("Max", "Muster", "2000-01-01");

        page.waitForResponse(
            r -> r.url().contains("/api/patients") && r.request().method().equals("POST"),
            () -> page.locator(".modal .btn").last().click()
        );

        page.waitForTimeout(200);
        String toast = page.locator(".toast").textContent();
        assertTrue(toast.contains("angelegt") || toast.contains("Patient"),
            "Toast: " + toast);
    }

    @Test
    void createPatient_withAllFields_sendsCorrectBody() {
        openNewPatientModal();
        fillMandatoryFields("Sabine", "Weber", "1990-11-05");
        page.locator("#fGeschlecht").selectOption("weiblich");
        page.locator("#fVersicherungsnr").fill("BKK-123");
        page.locator("#fBlutgruppe").selectOption("B-");
        page.locator("#fAllergien").fill("Latex");
        page.locator("#fTelefon").fill("0521-777");
        page.locator("#fEmail").fill("sabine@example.de");

        Response resp = page.waitForResponse(
            r -> r.url().contains("/api/patients") && r.request().method().equals("POST"),
            () -> page.locator(".modal .btn").last().click()
        );

        String body = resp.request().postData();
        assertTrue(body.contains("Sabine"));
        assertTrue(body.contains("Weber"));
    }

    // ── Patienten bearbeiten (PUT) ────────────────────────

    @Test
    void editButton_opensModalWithPatientData() {
        // Ersten Edit-Button klicken
        page.waitForResponse(
            r -> r.url().matches(".*\\/api\\/patients\\/\\d+$"),
            () -> page.locator("button.btn-warning").first().click()
        );
        page.waitForSelector(".modal-overlay.open");

        String title = page.locator("#modalTitle").textContent();
        assertEquals("Patient bearbeiten", title);

        // Felder müssen befüllt sein
        String vorname = page.locator("#fVorname").inputValue();
        assertFalse(vorname.isEmpty(), "Vorname sollte befüllt sein");
    }

    @Test
    void editPatient_sendsPutRequest() {
        page.waitForResponse(
            r -> r.url().matches(".*\\/api\\/patients\\/\\d+$") && r.request().method().equals("GET"),
            () -> page.locator("button.btn-warning").first().click()
        );
        page.waitForSelector(".modal-overlay.open");

        page.locator("#fVorname").fill("Geändert");

        Response resp = page.waitForResponse(
            r -> r.url().contains("/api/patients/") && r.request().method().equals("PUT"),
            () -> page.locator(".modal .btn").last().click()
        );

        assertEquals(200, resp.status());
        assertTrue(resp.request().postData().contains("Geändert"));
    }

    @Test
    void editPatient_showsAktualisierunToast() {
        page.waitForResponse(
            r -> r.url().matches(".*\\/api\\/patients\\/\\d+$") && r.request().method().equals("GET"),
            () -> page.locator("button.btn-warning").first().click()
        );
        page.waitForSelector(".modal-overlay.open");
        page.locator("#fNachname").fill("Geändert");

        page.waitForResponse(
            r -> r.url().contains("/api/patients/") && r.request().method().equals("PUT"),
            () -> page.locator(".modal .btn").last().click()
        );

        page.waitForTimeout(200);
        String toast = page.locator(".toast").textContent();
        assertTrue(toast.contains("aktualisiert") || toast.contains("Patient"),
            "Toast: " + toast);
    }

    // ── Patienten löschen (DELETE) ────────────────────────

    @Test
    void deleteButton_sendsDeleteRequest() {
        // confirm()-Dialog auto-bestätigen
        page.onDialog(dialog -> dialog.accept());

        Response resp = page.waitForResponse(
            r -> r.url().contains("/api/patients/") && r.request().method().equals("DELETE"),
            () -> page.locator("button.btn-danger").first().click()
        );

        assertEquals(204, resp.status());
    }

    @Test
    void deleteButton_showsConfirmDialog() {
        boolean[] dialogShown = {false};
        page.onDialog(dialog -> {
            dialogShown[0] = true;
            dialog.dismiss(); // Abbrechen → kein DELETE
        });

        page.locator("button.btn-danger").first().click();
        page.waitForTimeout(300);
        assertTrue(dialogShown[0], "Confirm-Dialog soll erscheinen");
    }

    @Test
    void deleteCancel_doesNotSendDeleteRequest() {
        page.onDialog(dialog -> dialog.dismiss());
        page.locator("button.btn-danger").first().click();
        page.waitForTimeout(400);

        verify(0, deleteRequestedFor(urlPathMatching("/api/patients/.*")));
    }

    @Test
    void deletePatient_showsGelöschtToast() {
        page.onDialog(dialog -> dialog.accept());

        page.waitForResponse(
            r -> r.url().contains("/api/patients/") && r.request().method().equals("DELETE"),
            () -> page.locator("button.btn-danger").first().click()
        );

        page.waitForTimeout(200);
        String toast = page.locator(".toast").textContent();
        assertTrue(toast.contains("gelöscht") || toast.contains("Patient"),
            "Toast: " + toast);
    }
}