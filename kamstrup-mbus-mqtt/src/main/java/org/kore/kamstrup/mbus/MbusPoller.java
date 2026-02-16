package org.kore.kamstrup.mbus;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.kamstrup.MeterReadingEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@Startup
@ApplicationScoped
public class MbusPoller {

  private static final Logger LOG = Logger.getLogger(MbusPoller.class.getName());

  @ConfigProperty(name = "mbus.port")
  String port;

  @ConfigProperty(name = "mbus.baud")
  int baud;

  @ConfigProperty(name = "mbus.address")
  int address;

  @ConfigProperty(name = "mbus.poll-interval-ms")
  long pollIntervalMs;

  @ConfigProperty(name = "mbus.reconnect-interval-ms", defaultValue = "15000")
  long reconnectIntervalMs;

  @Inject
  Event<MeterReadingEvent> meterReadingEvent;

  @Inject
  MbusStatus mbusStatus;

  private final AtomicBoolean errorPhaseLogged = new AtomicBoolean(false);

  private ScheduledExecutorService exec;
  private volatile MbusClient client;
  private volatile long nextReconnectAttemptAt = 0L;

  private boolean fcb;

  @PostConstruct
  void start() {
    LOG.info(() -> "[POLL] Starting. port=" + port +
        " baud=" + baud +
        " addr=" + address +
        " poll=" + pollIntervalMs +
        " reconnect=" + reconnectIntervalMs);

    mbusStatus.setConfigured(port, baud, address, pollIntervalMs, reconnectIntervalMs);

    exec = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "mbus-poller");
      t.setDaemon(true);
      return t;
    });

    exec.scheduleWithFixedDelay(this::tickSafe, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  void stop() {
    LOG.info("[POLL] Stopping...");
    if (exec != null) {
      exec.shutdownNow();
    }
    closeClientQuietly();
  }

  private void tickSafe() {
    try {
      tick();
    } catch (Exception e) {
      if (errorPhaseLogged.compareAndSet(false, true)) {
        LOG.log(Level.SEVERE,
            "[POLL] Polling failed. REST/UI remains up. Further errors suppressed until recovery.",
            e);
      } else {
        LOG.log(Level.FINE, "[POLL] Poll error (suppressed)", e);
      }
      closeClientQuietly();
    }
  }

  private void tick() {

    // 1) Ensure client connection
    if (client == null) {
      tryReconnectIfDue();
      return;
    }

    // 2) Normal M-Bus sequence
    client.normalize(address);

    MeterReadingEvent reading = client.readOnce(address, fcb);
    fcb = !fcb;

    if (reading == null) {
      LOG.fine("[POLL] No reading");
      return;
    }

    mbusStatus.onReading();

    meterReadingEvent.fireAsync(reading);

    LOG.info(() ->
        "[POLL] Event fired: meterId=" + reading.meterIdBcd() +
            " vol=" + reading.volumeM3());
  }

  private void tryReconnectIfDue() {
    long now = System.currentTimeMillis();
    if (now < nextReconnectAttemptAt) {
      return;
    }

    nextReconnectAttemptAt = now + reconnectIntervalMs;

    try {
      LOG.info("[POLL] Attempting serial connect...");
      client = new MbusClient(port, baud);

      mbusStatus.onConnected();
      errorPhaseLogged.set(false);

      LOG.info("[POLL] Serial connected.");
    } catch (Exception e) {

      mbusStatus.onConnectFailed(e);

      if (errorPhaseLogged.compareAndSet(false, true)) {
        LOG.log(Level.SEVERE,
            "[POLL] Cannot open serial port. Will retry. Further errors suppressed.",
            e);
      } else {
        LOG.log(Level.FINE, "[POLL] Serial open failed (suppressed)", e);
      }

      client = null;
    }
  }

  private void closeClientQuietly() {
    MbusClient c = client;
    client = null;

    mbusStatus.onDisconnected();

    if (c != null) {
      try {
        c.close();
      } catch (Exception ignored) {
        // intentionally ignored
      }
    }
  }
}