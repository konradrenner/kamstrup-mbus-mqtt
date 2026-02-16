package org.kore.kamstrup.rest;

import org.kore.kamstrup.LastReadingStore;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ReadingResource {

  @Inject LastReadingStore store;

  @GET
  @Path("/last")
  public Response last() {
    return store.last()
        .<Response>map(r -> Response.ok(r).build())
        .orElseGet(() -> Response.status(Response.Status.NO_CONTENT).build());
  }

  @GET
  @Path("/status")
  public Status status() {
    return new Status("ok", store.last().isPresent());
  }

  public record Status(String status, boolean hasData) {}
}