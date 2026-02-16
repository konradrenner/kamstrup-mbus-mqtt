package org.kore.kamstrup;

import java.time.Instant;
import java.util.List;

public record MeterReadingEvent(
    Instant ts,
    int primaryAddress,
    String meterIdBcd,
    String manufacturer,
    int versionId,
    int deviceType,
    int accessNumber,
    int status,
    Double volumeM3,
    Integer waterTempC,
    Integer ambientTempC,
    List<InfoCode> infoCodes,
    String rawFrameHex
) {
  public static MeterReadingEvent withNow(
      int primaryAddress,
      String meterIdBcd,
      String manufacturer,
      int versionId,
      int deviceType,
      int accessNumber,
      int status,
      Double volumeM3,
      Integer waterTempC,
      Integer ambientTempC,
      List<InfoCode> infoCodes,
      String rawFrameHex
  ) {
    return new MeterReadingEvent(
        Instant.now(),
        primaryAddress,
        meterIdBcd,
        manufacturer,
        versionId,
        deviceType,
        accessNumber,
        status,
        volumeM3,
        waterTempC,
        ambientTempC,
        infoCodes,
        rawFrameHex
    );
  }
}