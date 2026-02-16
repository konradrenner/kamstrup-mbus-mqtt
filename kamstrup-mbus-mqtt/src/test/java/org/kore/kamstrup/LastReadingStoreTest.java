package org.kore.kamstrup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class LastReadingStoreTest {

  @Test
  void shouldStoreLastEvent() {
    LastReadingStore store = new LastReadingStore();

    MeterReadingEvent event = new MeterReadingEvent(
        Instant.now(),
        26,
        "12345678",
        "KAM",
        1,
        22,
        3,
        0,
        12.3,
        6,
        11,
        List.of(),
        "RAW"
    );

    store.onReading(event);

    assertTrue(store.last().isPresent());
    assertEquals("12345678", store.last().get().meterIdBcd());
  }
}