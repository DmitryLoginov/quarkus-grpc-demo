package dev.ldv.shipment.domain;

public record Shipment(String id,
                       String trackingNumber,
                       String origin,
                       String destination,
                       ShipmentStatus status) {
}
