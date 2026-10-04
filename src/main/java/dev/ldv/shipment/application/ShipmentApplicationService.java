package dev.ldv.shipment.application;

import dev.ldv.shipment.domain.Shipment;
import dev.ldv.shipment.domain.ShipmentStatus;
import dev.ldv.shipment.repository.ShipmentRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class ShipmentApplicationService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentApplicationService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public Shipment create(String trackingNumber, String origin, String destination) {
        Log.debugf("Creating shipment with params: trackingNumber = %s, origin = %s, destination = %s",
                trackingNumber, origin, destination);

        String shipmentId = UUID.randomUUID().toString();
        Log.debugf("shipmentId: %s", shipmentId);

        Shipment shipment = new Shipment(shipmentId, trackingNumber, origin, destination, ShipmentStatus.CREATED);
        Log.debugf("Created shipment: %s", shipment);

        return shipmentRepository.save(shipment);
    }

    public Shipment getById(String id) {
        Log.debugf("Getting shipment with id: %s", id);

        return shipmentRepository.findById(id).orElseThrow();
    }
}
