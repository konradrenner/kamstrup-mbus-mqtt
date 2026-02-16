package org.kore.kamstrup.mbus;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import jakarta.inject.Inject;

@Readiness
public class MbusReadinessHealthCheck implements HealthCheck {

  @Inject
  MbusStatus status;

  @Override
  public HealthCheckResponse call() {
    Instant now = Instant.now();

    long thresholdMs = Math.max(20_000L, status.pollIntervalMs() * 4L);
    boolean recentReading = status.lastReadingAt()
        .map(t -> Duration.between(t, now).toMillis() <= thresholdMs)
        .orElse(false);

    // "UP" wenn seriell verbunden ODER gerade erst gelesen wurde (hilft bei kurzen reconnects)
    boolean up = status.connected() || recentReading;
    long thresholdSeconds = thresholdMs / 1000L;

    HealthCheckResponseBuilder b = HealthCheckResponse.named("mbus-serial").status(up)
        .withData("connected", status.connected())
        .withData("port", status.port() == null ? "" : status.port())
        .withData("baud", status.baud())
        .withData("address", status.address())
        .withData("pollIntervalMs", status.pollIntervalMs())
        .withData("reconnectIntervalMs", status.reconnectIntervalMs())
        .withData("readingFreshnessThresholdSeconds", thresholdSeconds);

    status.lastReadingAt().ifPresent(t -> b.withData("lastReadingAt", t.toString()));
    status.lastConnectOkAt().ifPresent(t -> b.withData("lastConnectOkAt", t.toString()));
    status.lastConnectFailAt().ifPresent(t -> b.withData("lastConnectFailAt", t.toString()));
    status.lastError().ifPresent(e -> b.withData("lastError", e));

    return b.build();
  }
}