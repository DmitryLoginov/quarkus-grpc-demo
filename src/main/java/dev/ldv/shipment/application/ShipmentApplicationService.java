package dev.ldv.shipment.application;

import dev.ldv.shipment.domain.*;
import dev.ldv.shipment.repository.ShipmentRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ShipmentApplicationService {

    private final ShipmentRepository shipmentRepository;
    private final Clock clock;

    public ShipmentApplicationService(ShipmentRepository shipmentRepository,
                                      Clock clock) {
        this.shipmentRepository = shipmentRepository;
        this.clock = clock;
    }

    public Shipment create(@NotBlank String trackingNumber,
                           @NotBlank String origin,
                           @NotBlank String destination) throws ConstraintViolationException {
        Log.debugf("Creating shipment with params: trackingNumber = %s, origin = %s, destination = %s",
                trackingNumber, origin, destination);

        String shipmentId = UUID.randomUUID().toString();
        Log.debugf("shipmentId: %s", shipmentId);

        Checkpoint createdCheckpoint = createCheckpoint(shipmentId,
                ShipmentStatus.CREATED,
                origin,
                clock.instant());
        Log.debugf("Created checkpoint: %s", createdCheckpoint);

        Shipment shipment = new Shipment(shipmentId,
                trackingNumber,
                origin,
                destination,
                ShipmentStatus.CREATED,
                List.of(createdCheckpoint));
        Log.debugf("Created shipment: %s", shipment);

        return shipmentRepository.saveIfTrackingNumberNotExists(shipment);
    }

    public Shipment getById(@NotBlank String id) throws ConstraintViolationException, ShipmentException {
        Log.debugf("Getting shipment with id: %s", id);

        return shipmentRepository.findById(id).orElseThrow(
                () -> new ShipmentException("Shipment with given id not found",
                        ShipmentException.Category.SHIPMENT_NOT_FOUND)
        );
    }

    public ShipmentUpdate addCheckpoint(@NotBlank String shipmentId,
                                        @NotNull ShipmentStatus status,
                                        @NotBlank String location,
                                        @NotNull Instant occurredAt) throws ConstraintViolationException, ShipmentException {
        Log.debugf("Add checkpoint for shipment with id: %s", shipmentId);
        Log.debugf("Checkpoint params: status: %s, location: %s, timestamp: %s", status, location, occurredAt);

        Shipment updatedShipment = shipmentRepository.update(shipmentId, persistentShipment -> {
                    Log.debugf("Validating transition...");
                    ShipmentStatusTransitionValidator.validateTransition(persistentShipment.status(), status);

                    Checkpoint checkpoint = createCheckpoint(shipmentId, status, location, occurredAt);
                    Log.debugf("Checkpoint: %s", checkpoint);

                    List<Checkpoint> checkpoints = new ArrayList<>(persistentShipment.checkpoints());
                    checkpoints.add(checkpoint);

                    return new Shipment(shipmentId,
                            persistentShipment.trackingNumber(),
                            persistentShipment.origin(),
                            persistentShipment.destination(),
                            status,
                            checkpoints);
                }
        );

        if (updatedShipment == null) {
            throw new ShipmentException("Shipment with given id not found",
                    ShipmentException.Category.SHIPMENT_NOT_FOUND);
        }

        Log.debugf("Updated shipment: %s", updatedShipment);

        return new ShipmentUpdate(updatedShipment, updatedShipment.checkpoints().getLast());
    }

    private Checkpoint createCheckpoint(String shipmentId,
                                        ShipmentStatus status,
                                        String location,
                                        Instant occurredAt) {
        return new Checkpoint(shipmentId,
                status,
                location,
                occurredAt);
    }
}
