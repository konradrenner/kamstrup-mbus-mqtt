package org.kore.kamstrup.mbus;

import java.util.List;

import org.kore.kamstrup.InfoCode;

public final class InfoCodeDecoder {

  private InfoCodeDecoder() {}

  public static List<InfoCode> decode(int raw16) {
    int infoBits = raw16 & 0x000F;              // bits 0..3
    int hours = (raw16 >> 4) & 0x0FFF;          // bits 4..15

    return List.of(
        new InfoCode(
            "DRY",
            "Zähler nicht wassergefüllt – es wird nichts gemessen",
            (infoBits & 0b0001) != 0,
            hours
        ),
        new InfoCode(
            "REVERSE",
            "Rückwärtsfluss erkannt",
            (infoBits & 0b0010) != 0,
            hours
        ),
        new InfoCode(
            "LEAK",
            "Wasser läuft kontinuierlich > 24h",
            (infoBits & 0b0100) != 0,
            hours
        ),
        new InfoCode(
            "BURST",
            "Hoher Durchfluss kontinuierlich > 30min",
            (infoBits & 0b1000) != 0,
            hours
        )
    );
  }
}