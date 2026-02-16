package org.kore.kamstrup.mqtt;

import io.smallrye.reactive.messaging.annotations.Channel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.kore.kamstrup.MeterReadingEvent;

@ApplicationScoped
public class MqttPublisher {

  @Inject
  @Channel("mbus")
  Emitter<MeterReadingEvent> emitter;

  void onReading(@ObservesAsync MeterReadingEvent event) {
    emitter.send(event);
    System.out.println("[MQTT] Published event for meterId=" + event.meterIdBcd());
  }
}