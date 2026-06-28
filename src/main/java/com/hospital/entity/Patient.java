package com.hospital.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_number", unique = true, nullable = false, length = 20)
    private String patientNumber;

    @Column(name = "vorname", nullable = false, length = 100)
    private String vorname;

    @Column(name = "nachname", nullable = false, length = 100)
    private String nachname;

    @Column(name = "geburtsdatum", nullable = false)
    private LocalDate geburtsdatum;

    @Column(name = "geschlecht", length = 10)
    private String geschlecht;

    @Column(name = "versicherungsnr", unique = true, length = 30)
    private String versicherungsnr;

    @Column(name = "blutgruppe", length = 5)
    private String blutgruppe;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "epa_status", length = 20)
    private String epaStatus = "pending";

    @Column(name = "allergien", columnDefinition = "TEXT")
    private String allergien;

    @Column(name = "notfallkontakt", length = 255)
    private String notfallkontakt;

    @Column(name = "adresse", columnDefinition = "TEXT")
    private String adresse;

    @Column(name = "telefon", length = 30)
    private String telefon;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "aufnahmedatum")
    private LocalDateTime aufnahmedatum;

    @Column(name = "entlassdatum")
    private LocalDateTime entlassdatum;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (aufnahmedatum == null) aufnahmedatum = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ---- Getter & Setter ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPatientNumber() { return patientNumber; }
    public void setPatientNumber(String v) { this.patientNumber = v; }

    public String getVorname() { return vorname; }
    public void setVorname(String v) { this.vorname = v; }

    public String getNachname() { return nachname; }
    public void setNachname(String v) { this.nachname = v; }

    public LocalDate getGeburtsdatum() { return geburtsdatum; }
    public void setGeburtsdatum(LocalDate v) { this.geburtsdatum = v; }

    public String getGeschlecht() { return geschlecht; }
    public void setGeschlecht(String v) { this.geschlecht = v; }

    public String getVersicherungsnr() { return versicherungsnr; }
    public void setVersicherungsnr(String v) { this.versicherungsnr = v; }

    public String getBlutgruppe() { return blutgruppe; }
    public void setBlutgruppe(String v) { this.blutgruppe = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public String getEpaStatus() { return epaStatus; }
    public void setEpaStatus(String v) { this.epaStatus = v; }

    public String getAllergien() { return allergien; }
    public void setAllergien(String v) { this.allergien = v; }

    public String getNotfallkontakt() { return notfallkontakt; }
    public void setNotfallkontakt(String v) { this.notfallkontakt = v; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String v) { this.adresse = v; }

    public String getTelefon() { return telefon; }
    public void setTelefon(String v) { this.telefon = v; }

    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }

    public LocalDateTime getAufnahmedatum() { return aufnahmedatum; }
    public void setAufnahmedatum(LocalDateTime v) { this.aufnahmedatum = v; }

    public LocalDateTime getEntlassdatum() { return entlassdatum; }
    public void setEntlassdatum(LocalDateTime v) { this.entlassdatum = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}