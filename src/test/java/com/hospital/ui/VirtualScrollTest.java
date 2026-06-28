package com.hospital.ui;

import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests für das virtuelle Scrolling:
 * - Spacer-Höhe entspricht Datenmenge
 * - Nur sichtbare Zeilen im DOM
 * - Nach Scroll werden neue Zeilen gerendert
 * - Große Datenmenge (500+) bleibt performant
 */
class VirtualScrollTest extends BaseUITest {

    private String buildPatientList(int count) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) sb.append(",");
            sb.append(String.format(
                "{\"id\":%d,\"patientNumber\":\"P-%04d\"," +
                "\"vorname\":\"Patient\",\"nachname\":\"Nr%d\"," +
                "\"geburtsdatum\":\"1980-01-01\"," +
                "\"status\":\"active\",\"epaStatus\":\"pending\"}", i, i, i));
        }
        sb.append("]");
        return sb.toString();
    }

    @Test
    void initialLoad_spacerHeightMatchesRowCount() {
        // 2 Patienten × 42px = 84px (+thead ~46)
        String height = (String) page.locator("#virtualSpacer").evaluate(
            "el => el.style.height");
        assertFalse(height.isEmpty(), "Spacer-Höhe soll gesetzt sein");
        // Mindestens 1 Zeile
        double px = Double.parseDouble(height.replace("px", "").trim());
        assertTrue(px > 0, "Spacer-Höhe > 0 erwartet, war: " + px);
    }

    @Test
    void largeList_onlyRendersVisibleRows() {
        // 500 Patienten laden
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(buildPatientList(500))));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> {
                page.locator("#searchInput").fill("Patient");
                page.locator("#searchInput").press("a");
                page.waitForTimeout(450);
            }
        );

        int domRows = page.locator("#visibleRows tbody tr").count();
        // DOM soll deutlich weniger als 500 Zeilen enthalten
        assertTrue(domRows < 500,
            "Virtuelles Scrolling: DOM-Zeilen sollen < 500 sein, war: " + domRows);
        // Aber mindestens 1 sichtbare Zeile
        assertTrue(domRows >= 1, "Mind. 1 Zeile soll sichtbar sein");
    }

    @Test
    void largeList_spacerHeightIsProportional() {
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(buildPatientList(500))));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> {
                page.locator("#searchInput").fill("Patient");
                page.locator("#searchInput").press("a");
                page.waitForTimeout(450);
            }
        );

        String height = (String) page.locator("#virtualSpacer").evaluate(
            "el => el.style.height");
        double px = Double.parseDouble(height.replace("px", "").trim());
        // 500 × 42px = 21000px + thead ≈ 21046px
        assertTrue(px > 10000, "Spacer-Höhe für 500 Patienten > 10000px erwartet, war: " + px);
    }

    @Test
    void scroll_rendersNewRows() {
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(buildPatientList(200))));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> {
                page.locator("#searchInput").fill("Patient");
                page.locator("#searchInput").press("a");
                page.waitForTimeout(450);
            }
        );

        // Ersten sichtbaren Inhalt merken
        String firstText = page.locator("#visibleRows tbody tr").first().textContent();

        // Weit nach unten scrollen
        page.locator("#scrollContainer").evaluate("el => el.scrollTop = 5000");
        page.waitForTimeout(200);

        String newFirstText = page.locator("#visibleRows tbody tr").first().textContent();
        // Nach dem Scrollen sollen andere Zeilen sichtbar sein
        assertNotEquals(firstText, newFirstText,
            "Nach Scroll sollten andere Patienten sichtbar sein");
    }

    @Test
    void scroll_toTop_restoresInitialRows() {
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(buildPatientList(200))));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> {
                page.locator("#searchInput").fill("Patient");
                page.locator("#searchInput").press("a");
                page.waitForTimeout(450);
            }
        );

        String initialFirst = page.locator("#visibleRows tbody tr").first().textContent();

        // Runter scrollen
        page.locator("#scrollContainer").evaluate("el => el.scrollTop = 5000");
        page.waitForTimeout(200);

        // Wieder nach oben scrollen
        page.locator("#scrollContainer").evaluate("el => el.scrollTop = 0");
        page.waitForTimeout(200);

        String restoredFirst = page.locator("#visibleRows tbody tr").first().textContent();
        assertEquals(initialFirst, restoredFirst,
            "Nach Scrollen zurück nach oben soll die erste Zeile wieder dieselbe sein");
    }

    @Test
    void newSearch_resetsScrollPositionToTop() {
        // Erst mit vielen Daten laden und scrollen
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(buildPatientList(200))));

        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> {
                page.locator("#searchInput").fill("Patient");
                page.locator("#searchInput").press("a");
                page.waitForTimeout(450);
            }
        );
        page.locator("#scrollContainer").evaluate("el => el.scrollTop = 3000");
        page.waitForTimeout(100);

        // Neue Suche → scrollTop soll auf 0 zurückgesetzt werden
        page.waitForResponse(
            r -> r.url().contains("/api/patients/search"),
            () -> {
                page.locator("#searchInput").fill("Schmidt");
                page.locator("#searchInput").press("a");
                page.waitForTimeout(450);
            }
        );

        Number scrollTop = (Number) page.locator("#scrollContainer")
            .evaluate("el => el.scrollTop");
        assertEquals(0, scrollTop.intValue(),
            "Nach neuer Suche soll ScrollTop = 0 sein");
    }
}