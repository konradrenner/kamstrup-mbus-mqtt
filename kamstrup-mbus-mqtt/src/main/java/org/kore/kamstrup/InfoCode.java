package org.kore.kamstrup;

public record InfoCode(
    String name,
    String description,
    boolean active,
    int hoursActiveLast30Days
) {}