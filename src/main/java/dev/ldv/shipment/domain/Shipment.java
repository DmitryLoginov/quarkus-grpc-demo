package dev.ldv.shipment.domain;

import java.util.List;

public record Shipment(String id,
                       String trackingNumber,
                       String origin,
                       String destination,
                       ShipmentStatus status,
                       List<Checkpoint> checkpoints) {

    public Shipment {
        checkpoints = List.copyOf(checkpoints);
    }
}
