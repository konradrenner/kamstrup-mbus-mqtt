package org.kore.kamstrup.mbus;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

final class MbusFrames {
  private MbusFrames() {}

  static final int START_SHORT = 0x10;
  static final int START_LONG  = 0x68;
  static final int STOP        = 0x16;
  static final int ACK         = 0xE5;

  static final int C_SND_NKE   = 0x40;
  static final int C_REQ_UD2_0 = 0x5B;
  static final int C_REQ_UD2_1 = 0x7B;

  static final int CI_RSP_VARIABLE = 0x72;

  static byte[] shortFrame(int cField, int aField) {
    int cs = (cField + aField) & 0xFF;
    return new byte[] {
        (byte) START_SHORT,
        (byte) cField,
        (byte) aField,
        (byte) cs,
        (byte) STOP
    };
  }

  static boolean isAck(byte[] frame) {
    return frame != null && frame.length == 1 && (frame[0] & 0xFF) == ACK;
  }

  static int findNextStart(byte[] b) {
    for (int i = 0; i < b.length; i++) {
      int v = b[i] & 0xFF;
      if (v == ACK || v == START_SHORT || v == START_LONG) return i;
    }
    return -1;
  }

  static String hex(byte[] data) {
    StringBuilder sb = new StringBuilder();
    for (byte x : data) sb.append(String.format("%02X ", x));
    return sb.toString().trim();
  }

  static String hx(int v) {
    return String.format("%02X", v & 0xFF);
  }

  static ParsedLongFrame parseLongFrame(byte[] f) {
    if ((f[0] & 0xFF) != START_LONG) throw new IllegalArgumentException("Not a long frame");
    int l1 = f[1] & 0xFF;
    int l2 = f[2] & 0xFF;
    if (l1 != l2) throw new IllegalArgumentException("L mismatch");
    if ((f[3] & 0xFF) != START_LONG) throw new IllegalArgumentException("Missing second 0x68");

    int totalLen = 4 + (l1 + 2);
    if (f.length < totalLen) throw new IllegalArgumentException("Frame too short for L");

    int c  = f[4] & 0xFF;
    int a  = f[5] & 0xFF;
    int ci = f[6] & 0xFF;

    int dataStart = 7;
    int dataEnd = totalLen - 2; // exclude CS + STOP
    byte[] data = Arrays.copyOfRange(f, dataStart, dataEnd);

    int cs = f[totalLen - 2] & 0xFF;

    int sum = (c + a + ci) & 0xFF;
    for (byte db : data) sum = (sum + (db & 0xFF)) & 0xFF;
    if (sum != cs) {
      throw new IllegalArgumentException("Checksum mismatch: frame=0x" + hx(cs) + " calc=0x" + hx(sum));
    }

    return new ParsedLongFrame(l1, c, a, ci, data, cs);
  }

  record ParsedLongFrame(int l, int c, int a, int ci, byte[] data, int checksum) {}
}