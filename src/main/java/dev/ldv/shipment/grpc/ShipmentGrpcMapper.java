package dev.ldv.shipment.grpc;

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
                .build();
    }

    private static ShipmentStatus fromDomainStatus(dev.ldv.shipment.domain.ShipmentStatus domainStatus) {
        return switch (domainStatus) {
            case CREATED -> ShipmentStatus.CREATED;
        };
    }
}
