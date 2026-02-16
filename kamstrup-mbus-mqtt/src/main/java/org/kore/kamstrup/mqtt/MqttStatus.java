package org.kore.kamstrup.mqtt;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
public class MqttStatus {
  private final AtomicLong attempts = new AtomicLong();
  private final AtomicLong successes = new AtomicLong();
  private final AtomicLong failures = new AtomicLong();

  private volatile Instant lastAttemptAt;
  private volatile Instant lastSuccessAt;
  private volatile Instant lastFailureAt;
  private volatile String lastFailure;

  public void onAttempt() {
    attempts.incrementAndGet();
    lastAttemptAt = Instant.now();
  }

  public void onSuccess() {
    successes.incrementAndGet();
    lastSuccessAt = Instant.now();
    lastFailure = null;
  }

  public void onFailure(Throwable t) {
    failures.incrementAndGet();
    lastFailureAt = Instant.now();
    lastFailure = t == null ? null : (t.getClass().getSimpleName() + ": " + t.getMessage());
  }

  public long attempts() { return attempts.get(); }
  public long successes() { return successes.get(); }
  public long failures() { return failures.get(); }

  public Optional<Instant> lastAttemptAt() { return Optional.ofNullable(lastAttemptAt); }
  public Optional<Instant> lastSuccessAt() { return Optional.ofNullable(lastSuccessAt); }
  public Optional<Instant> lastFailureAt() { return Optional.ofNullable(lastFailureAt); }
  public Optional<String> lastFailure() { return Optional.ofNullable(lastFailure); }
}