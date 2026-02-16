package org.kore.kamstrup;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class LastReadingStore {

  private final AtomicReference<MeterReadingEvent> last = new AtomicReference<>();

  public Optional<MeterReadingEvent> last() {
    return Optional.ofNullable(last.get());
  }

  void onReading(@ObservesAsync MeterReadingEvent event) {
    last.set(event);
  }
}