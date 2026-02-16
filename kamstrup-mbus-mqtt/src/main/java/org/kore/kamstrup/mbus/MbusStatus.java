package org.kore.kamstrup.mbus;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;

@Singleton
public class MbusStatus {
  private volatile boolean connected;
  private volatile String port;
  private volatile int baud;
  private volatile int address;
  private volatile long pollIntervalMs;
  private volatile long reconnectIntervalMs;

  private volatile Instant lastReadingAt;
  private volatile Instant lastConnectOkAt;
  private volatile Instant lastConnectFailAt;
  private volatile String lastError;

  public void setConfigured(String port, int baud, int address, long pollIntervalMs, long reconnectIntervalMs) {
    this.port = port;
    this.baud = baud;
    this.address = address;
    this.pollIntervalMs = pollIntervalMs;
    this.reconnectIntervalMs = reconnectIntervalMs;
  }

  public void onConnected() {
    connected = true;
    lastConnectOkAt = Instant.now();
    lastError = null;
  }

  public void onDisconnected() {
    connected = false;
  }

  public void onConnectFailed(Exception e) {
    connected = false;
    lastConnectFailAt = Instant.now();
    lastError = e == null ? null : (e.getClass().getSimpleName() + ": " + e.getMessage());
  }

  public void onReading() {
    lastReadingAt = Instant.now();
  }

  public boolean connected() { return connected; }
  public String port() { return port; }
  public int baud() { return baud; }
  public int address() { return address; }
  public long pollIntervalMs() { return pollIntervalMs; }
  public long reconnectIntervalMs() { return reconnectIntervalMs; }

  public Optional<Instant> lastReadingAt() { return Optional.ofNullable(lastReadingAt); }
  public Optional<Instant> lastConnectOkAt() { return Optional.ofNullable(lastConnectOkAt); }
  public Optional<Instant> lastConnectFailAt() { return Optional.ofNullable(lastConnectFailAt); }
  public Optional<String> lastError() { return Optional.ofNullable(lastError); }
}