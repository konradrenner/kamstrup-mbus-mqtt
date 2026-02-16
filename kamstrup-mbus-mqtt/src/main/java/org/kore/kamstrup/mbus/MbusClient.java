package org.kore.kamstrup.mbus;

import static org.kore.kamstrup.mbus.MbusFrames.ACK;
import static org.kore.kamstrup.mbus.MbusFrames.CI_RSP_VARIABLE;
import static org.kore.kamstrup.mbus.MbusFrames.C_REQ_UD2_0;
import static org.kore.kamstrup.mbus.MbusFrames.C_REQ_UD2_1;
import static org.kore.kamstrup.mbus.MbusFrames.C_SND_NKE;
import static org.kore.kamstrup.mbus.MbusFrames.START_LONG;
import static org.kore.kamstrup.mbus.MbusFrames.START_SHORT;
import static org.kore.kamstrup.mbus.MbusFrames.STOP;
import static org.kore.kamstrup.mbus.MbusFrames.findNextStart;
import static org.kore.kamstrup.mbus.MbusFrames.hex;
import static org.kore.kamstrup.mbus.MbusFrames.hx;
import static org.kore.kamstrup.mbus.MbusFrames.isAck;
import static org.kore.kamstrup.mbus.MbusFrames.parseLongFrame;
import static org.kore.kamstrup.mbus.MbusFrames.shortFrame;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kore.kamstrup.InfoCode;
import org.kore.kamstrup.MeterReadingEvent;

import com.fazecast.jSerialComm.SerialPort;

public final class MbusClient implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(MbusClient.class.getName());

  private final SerialPort port;

  public MbusClient(String portName, int baud) {
    LOG.info(() -> "[MBUS] Opening " + portName + " @ " + baud + " (8E1)");
    this.port = SerialPort.getCommPort(portName);
    this.port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.EVEN_PARITY);
    this.port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

    // Nonblocking + eigener Buffer (weil Bytes oft einzeln kommen)
    this.port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

    if (!this.port.openPort()) {
      throw new IllegalStateException("Could not open serial port: " + portName);
    }
    LOG.info(() -> "[MBUS] Port opened OK: " + this.port.getSystemPortName());
  }

  public void normalize(int address) {
    write(shortFrame(C_SND_NKE, address));
    byte[] f = readOneFrame(1200);
    if (isAck(f)) {
      LOG.fine("[MBUS] ACK after SND_NKE");
    } else {
      LOG.warning(() -> "[MBUS] Unexpected after SND_NKE: " + (f == null ? "(none)" : hex(f)));
    }
  }

  public MeterReadingEvent readOnce(int address, boolean fcb) {
    write(shortFrame(fcb ? C_REQ_UD2_1 : C_REQ_UD2_0, address));

    byte[] f = readOneFrame(3000);
    if (f == null) {
      LOG.warning("[MBUS] No response to REQ_UD2");
      return null;
    }
    if (isAck(f)) {
      f = readOneFrame(3000);
      if (f == null) {
        LOG.warning("[MBUS] ACK but no data frame");
        return null;
      }
    }

    if ((f[0] & 0xFF) != START_LONG) {
      LOG.warning("[MBUS] Not a long frame: " + hex(f));
      return null;
    }

    var lf = parseLongFrame(f);
    if (lf.ci() != CI_RSP_VARIABLE) {
      LOG.warning(() -> "[MBUS] Unexpected CI=0x" + hx(lf.ci()));
      return null;
    }

    return decodeMinimal(lf, f);
  }

  private byte[] readOneFrame(long timeoutMs) {
    long end = System.currentTimeMillis() + timeoutMs;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    while (System.currentTimeMillis() < end) {
      int avail = port.bytesAvailable();
      if (avail > 0) {
        byte[] tmp = new byte[Math.min(avail, 256)];
        int r = port.readBytes(tmp, tmp.length);
        if (r > 0) buffer.write(tmp, 0, r);
      }

      byte[] b = buffer.toByteArray();
      int start = findNextStart(b);
      if (start >= 0 && start > 0) {
        b = Arrays.copyOfRange(b, start, b.length);
        buffer.reset();
        buffer.writeBytes(b);
      }

      b = buffer.toByteArray();
      if (b.length == 0) {
        sleep5();
        continue;
      }

      int first = b[0] & 0xFF;

      if (first == ACK) return new byte[] {(byte) ACK};

      if (first == START_SHORT) {
        if (b.length >= 5 && (b[4] & 0xFF) == STOP) {
          return Arrays.copyOfRange(b, 0, 5);
        }
      }

      if (first == START_LONG) {
        if (b.length >= 4) {
          int l1 = b[1] & 0xFF, l2 = b[2] & 0xFF, s2 = b[3] & 0xFF;
          if (l1 == l2 && s2 == START_LONG) {
            int totalLen = 4 + (l1 + 2);
            if (b.length >= totalLen && (b[totalLen - 1] & 0xFF) == STOP) {
              return Arrays.copyOfRange(b, 0, totalLen);
            }
          }
        }
      }

      sleep5();
    }

    return null;
  }

  private static void sleep5() {
    try { Thread.sleep(5); } catch (InterruptedException ignored) {}
  }

  private void write(byte[] frame) {
    LOG.fine(() -> "[TX] " + hex(frame));
    int w = port.writeBytes(frame, frame.length);
    if (w != frame.length) throw new IllegalStateException("Write failed " + w + "/" + frame.length);
  }

  private static MeterReadingEvent decodeMinimal(MbusFrames.ParsedLongFrame lf, byte[] rawFrame) {
    byte[] d = lf.data();
    if (d.length < 12) return null;

    String meterId = bcdToStringLsbFirst(Arrays.copyOfRange(d, 0, 4));
    String man = decodeManufacturer(d[4] & 0xFF, d[5] & 0xFF);

    int versionId = d[6] & 0xFF;
    int deviceType = d[7] & 0xFF;
    int accessNumber = d[8] & 0xFF;
    int status = d[9] & 0xFF;

    Double volumeM3 = null;
    Integer waterTempC = null;
    Integer ambientTempC = null;
    Integer infoCodeRaw16 = null;
    List<InfoCode> infoCodes = List.of();

    int idx = 12;
    while (idx + 1 < d.length) {
      int dif = d[idx++] & 0xFF;
      int vif = d[idx++] & 0xFF;

      // VIF ext minimal: FF 20 (info code, 2 bytes)
      if ((vif == 0xFF || vif == 0xFD) && idx < d.length) {
        int vife1 = d[idx++] & 0xFF;
        int len = difToLen(dif);

        if (vif == 0xFF && vife1 == 0x20 && len == 2 && idx + 1 < d.length) {
          int lo = d[idx++] & 0xFF;
          int hi = d[idx++] & 0xFF;
          infoCodeRaw16 = (hi << 8) | lo;
        } else {
          idx += Math.max(0, len);
        }
        continue;
      }

      int len = difToLen(dif);
      if (idx + len > d.length) break;
      byte[] val = Arrays.copyOfRange(d, idx, idx + len);
      idx += len;

      // volume VIF 13..16
      if (len == 4 && volumeM3 == null && (vif == 0x13 || vif == 0x14 || vif == 0x15 || vif == 0x16)) {
        long u32 = u32LE(val);
        double scale = switch (vif) {
          case 0x13 -> 1e-3;
          case 0x14 -> 1e-2;
          case 0x15 -> 1e-1;
          case 0x16 -> 1.0;
          default -> 1.0;
        };
        volumeM3 = u32 * scale;
      }

      if (len == 1 && vif == 0x5B && waterTempC == null) waterTempC = val[0] & 0xFF;
      if (len == 1 && vif == 0x67 && ambientTempC == null) ambientTempC = val[0] & 0xFF;
    }

    if (infoCodeRaw16 != null) {
        infoCodes = InfoCodeDecoder.decode(infoCodeRaw16);
    }

    return MeterReadingEvent.withNow(
        lf.a(),
        meterId,
        man,
        versionId,
        deviceType,
        accessNumber,
        status,
        volumeM3,
        waterTempC,
        ambientTempC,
        infoCodes,
        hex(rawFrame)
    );
  }

  private static int difToLen(int dif) {
    return switch (dif & 0x0F) {
      case 0x0 -> 0;
      case 0x1 -> 1;
      case 0x2 -> 2;
      case 0x3 -> 3;
      case 0x4 -> 4;
      case 0x6 -> 6;
      default -> 0;
    };
  }

  private static long u32LE(byte[] b) {
    return Integer.toUnsignedLong(ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt());
  }

  private static String bcdToStringLsbFirst(byte[] bcd4) {
    StringBuilder sb = new StringBuilder();
    for (int i = bcd4.length - 1; i >= 0; i--) {
      int v = bcd4[i] & 0xFF;
      sb.append((v >> 4) & 0x0F).append(v & 0x0F);
    }
    return sb.toString();
  }

  private static String decodeManufacturer(int manL, int manH) {
    int m = (manH << 8) | manL;
    char c1 = (char) (((m >> 10) & 0x1F) + 64);
    char c2 = (char) (((m >> 5)  & 0x1F) + 64);
    char c3 = (char) (( m        & 0x1F) + 64);
    return "" + c1 + c2 + c3;
  }

  @Override
  public void close() {
    try {
      LOG.fine("[MBUS] Closing port");
      port.closePort();
    } catch (Exception e) {
      LOG.log(Level.WARNING, "[MBUS] Error while closing port", e);
    }
  }
}