package dev.ldv.shipment.grpc;

import dev.ldv.shipment.application.ShipmentApplicationService;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;

@GrpcService
public class ShipmentGrpcService implements ShipmentService {

    private final ShipmentApplicationService delegate;

    public ShipmentGrpcService(ShipmentApplicationService shipmentService) {
        this.delegate = shipmentService;
    }

    @Override
    public Uni<Shipment> createShipment(CreateShipmentRequest request) {
        return Uni.createFrom().item(() -> {
            dev.ldv.shipment.domain.Shipment domainModel =
                    delegate.create(request.getTrackingNumber(), request.getOrigin(), request.getDestination());
            return ShipmentGrpcMapper.fromDomainModel(domainModel);
        });
    }

    @Override
    public Uni<Shipment> getShipment(GetShipmentRequest request) {
        return Uni.createFrom().item(() -> {
            dev.ldv.shipment.domain.Shipment domainModel =
                    delegate.getById(request.getShipmentId());
            return ShipmentGrpcMapper.fromDomainModel(domainModel);
        });
    }
}
