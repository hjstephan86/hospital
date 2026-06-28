package com.hospital.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für den initialen Seitenaufbau:
 * - Seite lädt
 * - Zufällige Patientenliste wird angezeigt (unsortiert, kein Filter)
 * - Suchfeld ist leer
 * - Header und Toolbar sichtbar
 */
class InitialLoadTest extends BaseUITest {

    @Test
    void pageTitle_isCorrect() {
        assertEquals("Patientenverwaltung", page.title());
    }

    @Test
    void header_isVisible() {
        assertTrue(page.locator("header h1").isVisible());
        assertTrue(page.locator("header h1").textContent().contains("Patientenverwaltung"));
    }

    @Test
    void searchInput_isEmptyOnLoad() {
        String val = page.locator("#searchInput").inputValue();
        assertEquals("", val);
    }

    @Test
    void neuerPatientButton_isVisible() {
        assertTrue(page.locator("header button").isVisible());
        assertTrue(page.locator("header button").textContent().contains("Neuer Patient"));
    }

    @Test
    void randomPatients_areDisplayedOnLoad() {
        // WireMock gibt 2 Patienten zurück → mind. 1 Zeile sichtbar
        assertTrue(visibleRowCount() >= 1);
    }

    @Test
    void resultInfo_showsRandomMessage() {
        String info = resultInfoText();
        assertTrue(info.contains("zufällige Patienten") || info.contains("Ergebnis"),
            "ResultInfo war: " + info);
    }

    @Test
    void tableHeaders_arePresent() {
        assertTrue(page.locator("thead th").count() >= 7);
    }

    @Test
    void patientRow_containsExpectedColumns() {
        // Erste Zeile soll Pat.-Nr., Name, Geburtsdatum, Status enthalten
        String rowText = page.locator("#visibleRows tbody tr").first().textContent();
        assertTrue(rowText.contains("P-00") || rowText.contains("Müller")
            || rowText.contains("Schmidt"), "Zeile: " + rowText);
    }

    @Test
    void statusBadge_isRendered() {
        assertTrue(page.locator(".badge").count() >= 1);
    }

    @Test
    void actionButtons_editAndDeletePresent() {
        assertTrue(page.locator("button.btn-warning").count() >= 1);
        assertTrue(page.locator("button.btn-danger").count() >= 1);
    }
}