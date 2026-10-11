package dev.ldv.shipment.grpc;

import com.google.protobuf.Timestamp;
import dev.ldv.shipment.domain.ShipmentUpdate;

import java.time.Instant;

public final class ShipmentGrpcMapper {

    private ShipmentGrpcMapper() {
    }

    public static Shipment fromDomainModel(dev.ldv.shipment.domain.Shipment domainModel) {
        return Shipment.newBuilder()
                .setId(domainModel.id())
                .setTrackingNumber(domainModel.trackingNumber())
                .setOrigin(domainModel.origin())
                .setDestination(domainModel.destination())
                .setStatus(fromDomainStatus(domainModel.status()))
                .addAllCheckpoints(domainModel.checkpoints().stream()
                        .map(ShipmentGrpcMapper::fromDomainCheckpoint)
                        .toList())
                .build();
    }

    public static ShipmentEvent fromDomainUpdate(ShipmentUpdate domainModel) {
        return ShipmentEvent.newBuilder()
                .setShipment(fromDomainModel(domainModel.shipment()))
                .setCheckpoint(fromDomainCheckpoint(domainModel.checkpoint()))
                .build();
    }

    private static Checkpoint fromDomainCheckpoint(dev.ldv.shipment.domain.Checkpoint domainModel) {
        return Checkpoint.newBuilder()
                .setShipmentId(domainModel.shipmentId())
                .setStatus(fromDomainStatus(domainModel.status()))
                .setLocation(domainModel.location())
                .setOccurredAt(fromInstant(domainModel.occurredAt()))
                .build();
    }

    private static ShipmentStatus fromDomainStatus(dev.ldv.shipment.domain.ShipmentStatus domainStatus) {
        return switch (domainStatus) {
            case CREATED -> ShipmentStatus.CREATED;
            case IN_TRANSIT -> ShipmentStatus.IN_TRANSIT;
            case OUT_FOR_DELIVERY -> ShipmentStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> ShipmentStatus.DELIVERED;
            case CANCELLED -> ShipmentStatus.CANCELLED;
        };
    }

    private static Timestamp fromInstant(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    public static dev.ldv.shipment.domain.ShipmentStatus toDomainStatus(ShipmentStatus grpcStatus) {
        return switch (grpcStatus) {
            case CREATED -> dev.ldv.shipment.domain.ShipmentStatus.CREATED;
            case IN_TRANSIT -> dev.ldv.shipment.domain.ShipmentStatus.IN_TRANSIT;
            case OUT_FOR_DELIVERY -> dev.ldv.shipment.domain.ShipmentStatus.OUT_FOR_DELIVERY;
            case DELIVERED -> dev.ldv.shipment.domain.ShipmentStatus.DELIVERED;
            case CANCELLED -> dev.ldv.shipment.domain.ShipmentStatus.CANCELLED;
            default -> throw new IllegalStateException("Unexpected value: " + grpcStatus);
        };
    }
}
