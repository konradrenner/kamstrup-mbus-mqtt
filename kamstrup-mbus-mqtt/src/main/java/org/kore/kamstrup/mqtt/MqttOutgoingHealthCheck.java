package org.kore.kamstrup.mqtt;

import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.time.Instant;

@Readiness
public class MqttOutgoingHealthCheck implements HealthCheck {

  @Inject MqttStatus status;

  @ConfigProperty(name = "mp.messaging.outgoing.mbus.host", defaultValue = "localhost")
  String host;

  @ConfigProperty(name = "mp.messaging.outgoing.mbus.port", defaultValue = "1883")
  int port;

  @ConfigProperty(name = "mp.messaging.outgoing.mbus.topic", defaultValue = "kamstrup/mbus")
  String topic;

  @Override
  public HealthCheckResponse call() {
    // DOWN, wenn die letzte Aktion ein Failure war und danach kein Success mehr kam
    boolean hadFailure = status.failures() > 0;
    boolean downBecauseLastWasFailure = false;

    if (hadFailure) {
      Instant lf = status.lastFailureAt().orElse(null);
      Instant ls = status.lastSuccessAt().orElse(null);
      if (lf != null && (ls == null || ls.isBefore(lf))) {
        downBecauseLastWasFailure = true;
      }
    }

    boolean up = !downBecauseLastWasFailure;

    var b = HealthCheckResponse.named("mqtt-outgoing-mbus").status(up)
        .withData("host", host)
        .withData("port", port)
        .withData("topic", topic)
        .withData("attempts", status.attempts())
        .withData("successes", status.successes())
        .withData("failures", status.failures());

    status.lastAttemptAt().ifPresent(t -> b.withData("lastAttemptAt", t.toString()));
    status.lastSuccessAt().ifPresent(t -> b.withData("lastSuccessAt", t.toString()));
    status.lastFailureAt().ifPresent(t -> b.withData("lastFailureAt", t.toString()));
    status.lastFailure().ifPresent(m -> b.withData("lastFailure", m));

    return b.build();
  }
}