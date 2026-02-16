package org.kore.kamstrup.mbus;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kore.kamstrup.MeterReadingEvent;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MbusPoller {

  private static final Logger LOG = Logger.getLogger(MbusPoller.class.getName());

  @ConfigProperty(name = "mbus.port") String port;
  @ConfigProperty(name = "mbus.baud") int baud;
  @ConfigProperty(name = "mbus.address") int address;
  @ConfigProperty(name = "mbus.poll-interval-ms") long pollIntervalMs;

  @Inject Event<MeterReadingEvent> meterReadingEvent;

  private ScheduledExecutorService exec;
  private MbusClient client;
  private boolean fcb;

  @PostConstruct
  void start() {
    LOG.info(() -> "[POLL] Starting poller. port=" + port + " baud=" + baud +
        " addr=" + address + " intervalMs=" + pollIntervalMs);

    if (pollIntervalMs <= 0) {
      LOG.severe("[POLL] poll-interval-ms must be > 0");
      return;
    }

    try {
      this.client = new MbusClient(port, baud);
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "[POLL] Failed to create MbusClient (serial open failed?)", e);
      return;
    }

    this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "mbus-poller");
      t.setDaemon(true);
      return t;
    });

    this.exec.scheduleWithFixedDelay(this::pollOnceSafe, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
    LOG.info("[POLL] Scheduled polling.");
  }

  @PreDestroy
  void stop() {
    LOG.info("[POLL] Stopping poller...");
    if (exec != null) exec.shutdownNow();
    if (client != null) {
      try {
        client.close();
      } catch (Exception e) {
        LOG.log(Level.WARNING, "[POLL] Error while closing client", e);
      }
    }
  }

  private void pollOnceSafe() {
    try {
      pollOnce();
    } catch (Exception e) {
      LOG.log(Level.SEVERE, "[POLL] Poll error", e);
    }
  }

  private void pollOnce() {
    Objects.requireNonNull(client, "client");

    LOG.fine(() -> "[POLL] Normalize (SND_NKE) addr=" + address);
    client.normalize(address);

    LOG.fine(() -> "[POLL] REQ_UD2 addr=" + address + " fcb=" + (fcb ? 1 : 0));
    MeterReadingEvent reading = client.readOnce(address, fcb);
    fcb = !fcb;

    if (reading == null) {
      LOG.warning("[POLL] No reading (timeout / invalid frame)");
      return;
    }

    meterReadingEvent.fireAsync(reading);
    LOG.info(() -> "[POLL] Fired async event: meterId=" + reading.meterIdBcd() +
        " vol=" + reading.volumeM3() + " m³");
  }
}