package org.kore.kamstrup.mbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.kore.kamstrup.InfoCode;

class InfoCodeDecoderTest {

  @Test
  void shouldDecodeLeakWithHours() {
    // raw16 = hours=18 (0x12), LEAK bit=0b0100
    int raw = (18 << 4) | 0b0100;

    List<InfoCode> codes = InfoCodeDecoder.decode(raw);

    InfoCode leak = codes.stream()
        .filter(c -> c.name().equals("LEAK"))
        .findFirst()
        .orElseThrow();

    assertTrue(leak.active());
    assertEquals(18, leak.hoursActiveLast30Days());
  }

  @Test
  void shouldHaveNoActiveCodesWhenZero() {
    List<InfoCode> codes = InfoCodeDecoder.decode(0);

    assertTrue(codes.stream().noneMatch(InfoCode::active));
  }
}