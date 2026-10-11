package dev.ldv.shipment.domain;

import java.time.Instant;

public record Checkpoint(String shipmentId,
                         ShipmentStatus status,
                         String location,
                         Instant occurredAt) {
}
