package dev.ldv.shipment.repository;

import dev.ldv.shipment.domain.Shipment;
import dev.ldv.shipment.domain.ShipmentException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@ApplicationScoped
public class InMemoryShipmentRepository implements ShipmentRepository {

    private final Map<String, String> trackingNumberIndex = new HashMap<>();

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

    @Override
    public Shipment saveIfTrackingNumberNotExists(Shipment shipment) throws ShipmentException {
        synchronized (this) {
            if (trackingNumberIndex.containsKey(shipment.trackingNumber())) {
                throw new ShipmentException("Shipment with given tracking number already exists",
                        ShipmentException.Category.DUPLICATE_TRACKING_NUMBER);
            }

            shipmentMap.put(shipment.id(), shipment);
            trackingNumberIndex.put(shipment.trackingNumber(), shipment.id());

            return shipment;
        }
    }

    @Override
    public Shipment update(String id, Function<Shipment, Shipment> updateFunction) {
        return shipmentMap.computeIfPresent(
                id,
                (key, oldValue) -> updateFunction.apply(oldValue)
        );
    }
}
