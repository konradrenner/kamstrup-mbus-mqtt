package org.kore.kamstrup.mqtt;

import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.kore.kamstrup.MeterReadingEvent;

import java.util.logging.Logger;

@ApplicationScoped
public class MqttPublisher {

  private static final Logger LOG = Logger.getLogger(MqttPublisher.class.getName());

  @Inject
  @Channel("mbus")
  MutinyEmitter<MeterReadingEvent> emitter;

  @Inject
  MqttStatus mqttStatus;

  @ConfigProperty(name = "mp.messaging.outgoing.mbus.topic", defaultValue = "kamstrup/mbus")
  String topic;

  void onReading(@ObservesAsync MeterReadingEvent event) {

    mqttStatus.onAttempt();

    emitter.send(event)
        .subscribe().with(
            ignored -> {
              mqttStatus.onSuccess();
              LOG.fine(() -> "[MQTT] Published to topic=" + topic +
                  " meterId=" + event.meterIdBcd());
            },
            err -> {
              mqttStatus.onFailure(err);
              LOG.warning(() -> "[MQTT] Publish failed: " +
                  err.getClass().getSimpleName() + ": " + err.getMessage());
            }
        );
  }
}