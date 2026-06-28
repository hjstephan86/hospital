package com.hospital.ui;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Basisklasse für alle Playwright-UI-Tests.
 *
 * Architektur:
 *   index.html  →  fetch("/api/patients/…")
 *                       ↓
 *                  WireMock (Port 8089)   ← gemockter Backend-Ersatz
 *
 * index.html wird über einen eingebetteten WireMock-Server als statische
 * Datei ausgeliefert, damit fetch() auf denselben Origin trifft und
 * keine CORS-Probleme entstehen.
 */
public abstract class BaseUITest {

    protected static WireMockServer wm;
    protected static Playwright     playwright;
    protected static Browser        browser;

    protected BrowserContext context;
    protected Page           page;

    // ── Fixtures: Beispiel-JSON ──────────────────────────
    protected static final String PATIENT_MUELLER = """
        {"id":1,"patientNumber":"P-001","vorname":"Anna","nachname":"Müller",
         "geburtsdatum":"1985-03-12","geschlecht":"weiblich",
         "versicherungsnr":"AOK-123","blutgruppe":"A+",
         "status":"active","epaStatus":"active",
         "allergien":"Penicillin","notfallkontakt":"Hans: 0521-1",
         "adresse":"Musterstr. 1","telefon":"0521-999","email":"anna@example.de",
         "aufnahmedatum":"2024-01-01T08:00:00"}
        """;

    protected static final String PATIENT_SCHMIDT = """
        {"id":2,"patientNumber":"P-002","vorname":"Klaus","nachname":"Schmidt",
         "geburtsdatum":"1972-07-24","geschlecht":"männlich",
         "versicherungsnr":"TK-999","blutgruppe":"0+",
         "status":"discharged","epaStatus":"pending",
         "allergien":null,"notfallkontakt":null,
         "adresse":null,"telefon":null,"email":null,
         "aufnahmedatum":"2024-01-02T09:00:00"}
        """;

    protected static final String RANDOM_LIST =
        "[" + PATIENT_MUELLER + "," + PATIENT_SCHMIDT + "]";

    protected static final String SEARCH_RESULT =
        "[" + PATIENT_MUELLER + "]";

    protected static final String EMPTY_LIST = "[]";

    // ── Lifecycle ────────────────────────────────────────

    @BeforeAll
    static void startInfrastructure() {
        // WireMock auf Port 8089
        wm = new WireMockServer(WireMockConfiguration.options()
            .port(8089)
            .usingFilesUnderClasspath("wiremock"));
        wm.start();
        configureFor("localhost", 8089);

        // Playwright einmalig hochfahren (Headless Chromium)
        playwright = Playwright.create();
        browser    = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true)
        );
    }

    @AfterAll
    static void stopInfrastructure() {
        if (browser    != null) browser.close();
        if (playwright != null) playwright.close();
        if (wm         != null) wm.stop();
    }

    @BeforeEach
    void openPage() {
        // resetMappings() allein löscht nicht das Request-Journal — ohne
        // resetAll() sehen verify()-Aufrufe in einem Test auch Requests aus
        // vorangegangenen Tests derselben Klasse (Testreihenfolge-Abhängigkeit).
        wm.resetAll();

        // Standard-Stub: initiale Zufallsliste
        stubFor(get(urlPathEqualTo("/api/patients/random"))
            .willReturn(okJson(RANDOM_LIST)));

        // Standard-Stub: Suche gibt Müller zurück
        stubFor(get(urlPathMatching("/api/patients/search.*"))
            .willReturn(okJson(SEARCH_RESULT)));

        // Standard-Stub: Einzel-Patient
        stubFor(get(urlPathEqualTo("/api/patients/1"))
            .willReturn(okJson(PATIENT_MUELLER)));
        stubFor(get(urlPathEqualTo("/api/patients/2"))
            .willReturn(okJson(PATIENT_SCHMIDT)));

        // Standard-Stubs: CRUD
        stubFor(post(urlPathEqualTo("/api/patients"))
            .willReturn(aResponse().withStatus(201).withHeader("Content-Type","application/json")
                .withBody(PATIENT_MUELLER)));
        stubFor(put(urlPathMatching("/api/patients/.*"))
            .willReturn(okJson(PATIENT_MUELLER)));
        stubFor(delete(urlPathMatching("/api/patients/.*"))
            .willReturn(aResponse().withStatus(204)));

        // Neue Browser-Context + Page pro Test (sauberer State)
        context = browser.newContext(new Browser.NewContextOptions()
            .setBaseURL("http://localhost:8089"));
        page = context.newPage();

        // index.html über WireMock als statische Datei bereitstellen
        Path htmlPath = Paths.get("src/main/webapp/index.html");
        String html;
        try {
            html = java.nio.file.Files.readString(htmlPath)
                // API_URL relativ lassen — zeigt auf WireMock
                .replace("const API = '/api/patients'",
                         "const API = '/api/patients'");
        } catch (Exception e) {
            throw new RuntimeException("index.html nicht gefunden: " + htmlPath.toAbsolutePath(), e);
        }

        stubFor(get(urlEqualTo("/"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/html; charset=utf-8")
                .withBody(html)));

        page.navigate("http://localhost:8089/");
        // Warten bis initiale Daten geladen
        page.waitForSelector("td", new Page.WaitForSelectorOptions()
            .setTimeout(5000));
    }

    @AfterEach
    void closePage() {
        if (page    != null) page.close();
        if (context != null) context.close();
    }

    // ── Hilfsmethoden ────────────────────────────────────

    protected void typeInSearch(String text) {
        Locator input = page.locator("#searchInput");
        // fill() löst bereits ein "input"-Event aus; ein zusätzliches
        // press("a") würde den eingegebenen Text um ein "a" verlängern.
        input.fill(text);
        // Auf Debouncing warten (300 ms + Puffer)
        page.waitForTimeout(450);
    }

    protected String resultInfoText() {
        return page.locator("#resultInfo").textContent().trim();
    }

    protected int visibleRowCount() {
        return page.locator("#visibleRows tbody tr").count();
    }
}