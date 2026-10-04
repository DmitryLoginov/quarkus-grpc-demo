package dev.ldv.shipment.repository;

import dev.ldv.shipment.domain.Shipment;

import java.util.Optional;

public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    Optional<Shipment> findById(String id);
}
