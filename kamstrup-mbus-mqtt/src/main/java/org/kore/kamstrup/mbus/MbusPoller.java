package org.kore.kamstrup.mbus;

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

@ApplicationScoped
public class MbusPoller {

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
    System.out.println("[POLL] Starting poller. intervalMs=" + pollIntervalMs);
    this.client = new MbusClient(port, baud);

    this.exec = Executors.newSingleThreadScheduledExecutor();
    this.exec.scheduleWithFixedDelay(this::pollOnceSafe, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  void stop() {
    System.out.println("[POLL] Stopping poller...");
    if (exec != null) exec.shutdownNow();
    if (client != null) client.close();
  }

  private void pollOnceSafe() {
    try {
      pollOnce();
    } catch (Exception e) {
      System.err.println("[POLL] Error: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void pollOnce() {
    client.normalize(address);

    MeterReadingEvent reading = client.readOnce(address, fcb);
    fcb = !fcb;

    if (reading == null) return;

    // CDI async event fan-out (MQTT + Store + anything else)
    meterReadingEvent.fireAsync(reading);
    System.out.println("[POLL] Fired async event: meterId=" + reading.meterIdBcd() + " vol=" + reading.volumeM3());
  }
}