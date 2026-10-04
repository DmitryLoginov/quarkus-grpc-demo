package dev.ldv.shipment.repository;

import dev.ldv.shipment.domain.Shipment;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryShipmentRepository implements ShipmentRepository {

    private final Map<String, Shipment> shipmentMap = new ConcurrentHashMap<>();

    @Override
    public Shipment save(Shipment shipment) {
        shipmentMap.put(shipment.id(), shipment);

        return shipment;
    }

    @Override
    public Optional<Shipment> findById(String id) {
        return Optional.ofNullable(shipmentMap.get(id));
    }
}
