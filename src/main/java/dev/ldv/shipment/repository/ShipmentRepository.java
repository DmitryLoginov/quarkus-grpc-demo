package dev.ldv.shipment.repository;

import dev.ldv.shipment.domain.Shipment;

import java.util.Optional;
import java.util.function.Function;

public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    Optional<Shipment> findById(String id);

    Shipment saveIfTrackingNumberNotExists(Shipment shipment);

    Shipment update(String id, Function<Shipment, Shipment> updateFunction);
}
