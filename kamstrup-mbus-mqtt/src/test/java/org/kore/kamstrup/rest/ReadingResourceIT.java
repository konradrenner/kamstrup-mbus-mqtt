package org.kore.kamstrup.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kore.kamstrup.LastReadingStore;
import org.kore.kamstrup.MeterReadingEvent;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class ReadingResourceIT {

  @Inject
  LastReadingStore store;

  @Test
  void shouldReturnLastReading() {
    store.onReading(new MeterReadingEvent(
        Instant.now(),
        26,
        "12345678",
        "KAM",
        1,
        22,
        3,
        0,
        42.0,
        6,
        11,
        List.of(),
        "RAW"
    ));

    given()
        .when().get("/api/last")
        .then()
        .statusCode(200)
        .body("meterIdBcd", equalTo("12345678"));
  }
}