package dev.ldv.shipment.grpc;

import dev.ldv.shipment.application.ShipmentApplicationService;
import dev.ldv.shipment.domain.ShipmentException;
import dev.ldv.shipment.domain.ShipmentUpdate;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;

import java.time.Instant;

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

    @Override
    public Uni<ShipmentEvent> addCheckpoint(AddCheckpointRequest request) {
        return Uni.createFrom().item(() -> {
           if (!request.hasCheckpoint()) {
               throw new ShipmentException("Checkpoint not provided",
                       ShipmentException.Category.INVALID_INPUT);
           }

           ShipmentStatus status = request.getCheckpoint().getStatus();

           if (status == ShipmentStatus.UNRECOGNIZED || status == ShipmentStatus.SHIPMENT_STATUS_UNSPECIFIED) {
               throw new ShipmentException("Checkpoint status is invalid",
                       ShipmentException.Category.INVALID_INPUT);
           }

           Checkpoint checkpoint = request.getCheckpoint();

           if (!checkpoint.hasOccurredAt()) {
               throw new ShipmentException("Checkpoint timestamp not provided",
                       ShipmentException.Category.INVALID_INPUT);
           }

           Instant instant;

           try {
               instant = Instant.ofEpochSecond(checkpoint.getOccurredAt().getSeconds(),
                       checkpoint.getOccurredAt().getNanos());
           } catch (Exception e) {
               throw new ShipmentException("Unable to parse checkpoint timestamp",
                       ShipmentException.Category.INVALID_INPUT);
           }

           ShipmentUpdate shipmentUpdate = delegate.addCheckpoint(checkpoint.getShipmentId(),
                   ShipmentGrpcMapper.toDomainStatus(checkpoint.getStatus()), checkpoint.getLocation(), instant);

           return ShipmentGrpcMapper.fromDomainUpdate(shipmentUpdate);
        });
    }
}
