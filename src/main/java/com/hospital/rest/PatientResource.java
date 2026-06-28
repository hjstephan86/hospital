package com.hospital.rest;

import com.hospital.entity.Patient;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.List;

@Path("/patients")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class PatientResource {

    @PersistenceContext(unitName = "hospitalPU")
    private EntityManager em;

    // ----------------------------------------------------------
    // GET /patients/random?limit=30
    // Initiale Ansicht: zufällige Stichprobe, unsortiert
    // Verwendet die DB-Funktion random_patients() via TABLESAMPLE
    // ----------------------------------------------------------
    @GET
    @Path("/random")
    public Response getRandom(@QueryParam("limit") @DefaultValue("30") int limit) {
        List<Patient> result = em
            .createNativeQuery("SELECT * FROM random_patients(:lim)", Patient.class)
            .setParameter("lim", limit)
            .getResultList();
        return Response.ok(result).build();
    }

    // ----------------------------------------------------------
    // GET /patients/search?q=Müller
    // Filtersuche: sortiert, kein LIMIT — virtuelles Scrolling
    // übernimmt Mengenbegrenzung im Frontend
    // ----------------------------------------------------------
    @GET
    @Path("/search")
    public Response search(@QueryParam("q") @DefaultValue("") String q) {
        if (q == null || q.isBlank()) {
            return getRandom(30);
        }
        // Ohne Obergrenze kann ein häufiger Suchbegriff bei 10 Mio. Zeilen
        // Millionen Treffer liefern und den Heap sprengen (OutOfMemoryError).
        List<Patient> result = em
            .createNativeQuery("SELECT * FROM search_patients(:q) LIMIT 500", Patient.class)
            .setParameter("q", q.trim())
            .getResultList();
        return Response.ok(result).build();
    }

    // ----------------------------------------------------------
    // GET /patients/{id}
    // ----------------------------------------------------------
    @GET
    @Path("/{id}")
    public Response getById(@PathParam("id") Long id) {
        Patient p = em.find(Patient.class, id);
        if (p == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(p).build();
    }

    // ----------------------------------------------------------
    // POST /patients
    // ----------------------------------------------------------
    @POST
    @Transactional
    public Response create(Patient p) {
        em.persist(p);
        em.flush();
        return Response.status(Response.Status.CREATED).entity(p).build();
    }

    // ----------------------------------------------------------
    // PUT /patients/{id}
    // ----------------------------------------------------------
    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") Long id, Patient updated) {
        Patient existing = em.find(Patient.class, id);
        if (existing == null) return Response.status(Response.Status.NOT_FOUND).build();

        existing.setVorname(updated.getVorname());
        existing.setNachname(updated.getNachname());
        existing.setGeburtsdatum(updated.getGeburtsdatum());
        existing.setGeschlecht(updated.getGeschlecht());
        existing.setVersicherungsnr(updated.getVersicherungsnr());
        existing.setBlutgruppe(updated.getBlutgruppe());
        existing.setStatus(updated.getStatus());
        existing.setEpaStatus(updated.getEpaStatus());
        existing.setAllergien(updated.getAllergien());
        existing.setNotfallkontakt(updated.getNotfallkontakt());
        existing.setAdresse(updated.getAdresse());
        existing.setTelefon(updated.getTelefon());
        existing.setEmail(updated.getEmail());
        existing.setEntlassdatum(updated.getEntlassdatum());

        em.merge(existing);
        return Response.ok(existing).build();
    }

    // ----------------------------------------------------------
    // DELETE /patients/{id}
    // ----------------------------------------------------------
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Patient p = em.find(Patient.class, id);
        if (p == null) return Response.status(Response.Status.NOT_FOUND).build();
        em.remove(p);
        return Response.noContent().build();
    }
}