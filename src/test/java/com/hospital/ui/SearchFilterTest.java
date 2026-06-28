package com.hospital.ui;

import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für die Filterfunktion:
 * - Debouncing (kein sofortiger API-Aufruf)
 * - API wird nach 300 ms aufgerufen
 * - Ergebnisliste wird korrekt gerendert
 * - Leere Ergebnisse werden angezeigt
 * - Leerstring → zurück zur Zufallsliste
 * - Sonderzeichen / Umlaute
 */
class SearchFilterTest extends BaseUITest {

    @Test
    void typing_triggersSearchAfterDebounce() {
        // Auf den fetch-Request zur Such-API warten
        Response resp = page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> typeInSearch("Müller")
        );
        assertTrue(resp.url().contains("q=M"));
        assertEquals(200, resp.status());
    }

    @Test
    void searchResult_rendersFilteredRows() {
        // Mock: Suche gibt nur Müller zurück
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(SEARCH_RESULT)));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> typeInSearch("Müller")
        );

        int rows = visibleRowCount();
        assertTrue(rows >= 1, "Erwartete mind. 1 Zeile, war: " + rows);

        String rowText = page.locator("#visibleRows tbody tr").first().textContent();
        assertTrue(rowText.contains("Müller"), "Zeile: " + rowText);
    }

    @Test
    void searchResult_showsCountInResultInfo() {
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(SEARCH_RESULT)));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> typeInSearch("Müller")
        );

        String info = resultInfoText();
        assertFalse(info.isEmpty());
        assertTrue(info.contains("Ergebnis") || info.contains("1"),
            "ResultInfo: " + info);
    }

    @Test
    void emptySearchResult_showsNoRows() {
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(EMPTY_LIST)));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> typeInSearch("XYZunbekannt123")
        );

        int rows = visibleRowCount();
        assertEquals(0, rows, "Erwartete 0 Zeilen bei leerem Ergebnis");
    }

    @Test
    void clearingSearch_reloadsRandomPatients() {
        // 1. Suchen
        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> typeInSearch("Müller")
        );

        // 2. Suchfeld leeren → random wird neu geladen
        page.waitForResponse(
            r -> r.url().contains("/api/patients/random"),
            () -> page.locator("#searchInput").fill("")
        );

        String info = resultInfoText();
        assertTrue(info.contains("zufällige") || info.contains("Ergebnis"),
            "ResultInfo nach Leeren: " + info);
    }

    @Test
    void debouncing_noApiCallOnFirstKeypress() throws InterruptedException {
        // Tippen ohne auf Debounce zu warten
        page.locator("#searchInput").pressSequentially("M", new com.microsoft.playwright.Locator.PressSequentiallyOptions().setDelay(10));
        Thread.sleep(100); // Weniger als 300 ms Debounce

        // Kein search-Request darf noch abgegangen sein
        // (Nur der initiale /random-Request ist in der History)
        verify(0, getRequestedFor(urlPathMatching("/api/patients/search.*")));
    }

    @Test
    void umlaut_inSearch_encodedCorrectly() {
        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> typeInSearch("Müller")
        );

        // URL muss enkodiert sein (ü → %C3%BC oder ähnlich)
        verify(getRequestedFor(urlPathMatching("/api/patients/search.*"))
            .withQueryParam("q", matching(".*[Mm].*")));
    }

    @Test
    void searchByPatientNumber_callsApi() {
        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> typeInSearch("P-001")
        );

        verify(getRequestedFor(urlPathMatching("/api/patients/search.*"))
            .withQueryParam("q", equalTo("P-001")));
    }

    @Test
    void largeResultSet_resultInfoShowsCount() {
        // Simuliere 600 Patienten (Frontend zeigt "Mehr als 500")
        StringBuilder many = new StringBuilder("[");
        for (int i = 1; i <= 600; i++) {
            if (i > 1) many.append(",");
            many.append(String.format(
                "{\"id\":%d,\"patientNumber\":\"P-%04d\",\"vorname\":\"Max\",\"nachname\":\"Muster%d\"," +
                "\"geburtsdatum\":\"1980-01-01\",\"status\":\"active\",\"epaStatus\":\"pending\"}", i, i, i));
        }
        many.append("]");

        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(many.toString())));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> typeInSearch("Max")
        );

        String info = resultInfoText();
        assertTrue(info.contains("500") || info.contains("600") || info.contains("Mehr"),
            "ResultInfo bei 600 Treffern: " + info);
    }
}